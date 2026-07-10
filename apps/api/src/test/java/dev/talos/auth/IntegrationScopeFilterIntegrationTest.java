package dev.talos.auth;

import com.jayway.jsonpath.JsonPath;
import dev.talos.audit.AuditEvent;
import dev.talos.audit.AuditEventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Section 16 Phase 12 Track B: the Telegram service account's JWT may create/read tasks, read
 * projects, read run status, and list approvals -- and nothing else. This is the "least privilege"
 * acceptance criterion, enforced ahead of the general Phase 15 RBAC matrix by IntegrationScopeFilter.
 */
@Testcontainers
@SpringBootTest(properties = {
		"talos.jwt-secret=test-only-jwt-signing-secret-not-for-production-use-32bytes+",
		"talos.admin-email=admin@test.local",
		"talos.admin-password=test-admin-password",
		"talos.telegram-service-email=telegram-bot@test.local",
		"talos.telegram-service-password=test-telegram-password"
})
@AutoConfigureMockMvc
class IntegrationScopeFilterIntegrationTest {

	@Container
	@ServiceConnection
	static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");

	@Container
	@ServiceConnection
	static RabbitMQContainer rabbitmq = new RabbitMQContainer("rabbitmq:4.1-management");

	@Container
	static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7"))
			.withExposedPorts(6379);

	@DynamicPropertySource
	static void redisProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.data.redis.url", () -> "redis://" + redis.getHost() + ":" + redis.getMappedPort(6379));
	}

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private AuditEventRepository auditEventRepository;

	private String loginAs(String email, String password) throws Exception {
		String response = mockMvc.perform(post("/api/v1/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		return "Bearer " + JsonPath.<String>read(response, "$.token");
	}

	private String adminToken() throws Exception {
		return loginAs("admin@test.local", "test-admin-password");
	}

	private String telegramToken() throws Exception {
		return loginAs("telegram-bot@test.local", "test-telegram-password");
	}

	private String createProject(String token) throws Exception {
		String response = mockMvc.perform(post("/api/v1/projects")
						.header("Authorization", token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name":"Scope Project %s","repoUrl":"git@github.com:org/scope.git","stackType":"spring-boot"}
								""".formatted(java.util.UUID.randomUUID())))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return JsonPath.read(response, "$.id");
	}

	@Test
	void telegramServiceAccount_canCreateAndReadTasks() throws Exception {
		String adminToken = adminToken();
		String projectId = createProject(adminToken);
		String telegramToken = telegramToken();

		String response = mockMvc.perform(post("/api/v1/tasks")
						.header("Authorization", telegramToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"projectId\":\"" + projectId + "\",\"title\":\"Via Telegram\",\"source\":\"TELEGRAM\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.source").value("TELEGRAM"))
				.andExpect(jsonPath("$.status").value("BACKLOG"))
				.andReturn().getResponse().getContentAsString();
		String taskId = JsonPath.read(response, "$.id");

		mockMvc.perform(get("/api/v1/tasks/{id}", taskId).header("Authorization", telegramToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.source").value("TELEGRAM"));

		mockMvc.perform(get("/api/v1/projects").header("Authorization", telegramToken))
				.andExpect(status().isOk());

		mockMvc.perform(get("/api/v1/approvals").header("Authorization", telegramToken))
				.andExpect(status().isOk());
	}

	@Test
	void telegramServiceAccount_canRecordRejectedSender_writingAnAuditRow() throws Exception {
		String telegramToken = telegramToken();

		mockMvc.perform(post("/api/v1/chat/rejected-sender")
						.header("Authorization", telegramToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"channel\":\"TELEGRAM\",\"chatId\":\"999999\"}"))
				.andExpect(status().isNoContent());

		List<AuditEvent> rejections = auditEventRepository.findAll().stream()
				.filter(e -> "chat.rejected_sender".equals(e.getEventType()))
				.toList();
		assertThat(rejections).anyMatch(e -> "999999".equals(e.getDetailsJson().get("chatId")));
	}

	@Test
	void telegramServiceAccount_cannotStartRunApproveOrReadSecrets_andDeniedAccessIsAudited() throws Exception {
		String adminToken = adminToken();
		String projectId = createProject(adminToken);
		String telegramToken = telegramToken();

		String response = mockMvc.perform(post("/api/v1/tasks")
						.header("Authorization", telegramToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"projectId\":\"" + projectId + "\",\"title\":\"Via Telegram\"}"))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		String taskId = JsonPath.read(response, "$.id");

		mockMvc.perform(post("/api/v1/tasks/{id}/start-run", taskId)
						.header("Authorization", telegramToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"agentKey\":\"custom-shell\"}"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("INTEGRATION_SCOPE_FORBIDDEN"));

		mockMvc.perform(get("/api/v1/integrations").header("Authorization", telegramToken))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("INTEGRATION_SCOPE_FORBIDDEN"));

		List<AuditEvent> denials = auditEventRepository.findAll().stream()
				.filter(e -> "integration.access_denied".equals(e.getEventType()))
				.toList();
		assertThat(denials).hasSizeGreaterThanOrEqualTo(2);
	}

	@Test
	void adminAccount_isUnaffectedByIntegrationScope() throws Exception {
		String adminToken = adminToken();
		String projectId = createProject(adminToken);

		mockMvc.perform(get("/api/v1/integrations").header("Authorization", adminToken))
				.andExpect(status().isOk());

		String response = mockMvc.perform(post("/api/v1/tasks")
						.header("Authorization", adminToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"projectId\":\"" + projectId + "\",\"title\":\"Dashboard task\"}"))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		String taskId = JsonPath.read(response, "$.id");

		mockMvc.perform(post("/api/v1/tasks/{id}/start-run", taskId)
						.header("Authorization", adminToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"agentKey\":\"custom-shell\"}"))
				.andExpect(status().isCreated());
	}
}
