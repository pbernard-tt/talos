// SPDX-FileCopyrightText: 2026 Vulkan Technologies
// SPDX-License-Identifier: AGPL-3.0-or-later

package dev.talos.events;

import com.jayway.jsonpath.JsonPath;
import com.networknt.schema.Error;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import dev.talos.integrations.DeployProvider;
import dev.talos.integrations.GitHubClient;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 5 acceptance: "start-run yields a RabbitMQ message validating against its JSON Schema."
 * Consumes the real message off a test-declared queue bound to talos.events and validates it
 * against packages/contracts/events/task.run.requested.json (Section 11).
 */
@Testcontainers
@SpringBootTest(properties = {
		"talos.jwt-secret=test-only-jwt-signing-secret-not-for-production-use-32bytes+",
		"talos.admin-email=admin@test.local",
		"talos.admin-password=test-admin-password",
		"talos.internal-api-token=test-internal-token-not-for-production-use-32bytes+",
		"talos.secrets-key=ZGV2LW9ubHktMzItYnl0ZS1wbGFjZWhvbGRlci1rZXk="
})
@AutoConfigureMockMvc
class EventPublisherIntegrationTest {

	@MockitoBean
	private GitHubClient gitHubClient;

	@MockitoBean
	private DeployProvider deployProvider;

	@Container
	@ServiceConnection
	static PostgreSQLContainer postgres = new PostgreSQLContainer(org.testcontainers.utility.DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres"));

	@Container
	@ServiceConnection
	static RabbitMQContainer rabbitmq = new RabbitMQContainer("rabbitmq:4.1-management");

	@Container
	static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7"))
			.withExposedPorts(6379);

	@DynamicPropertySource
	static void redisProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.data.redis.url",
				() -> "redis://" + redis.getHost() + ":" + redis.getMappedPort(6379));
	}

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private RabbitAdmin rabbitAdmin;

	@Autowired
	private RabbitTemplate rabbitTemplate;

	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

	private static Schema loadSchema(String resource) throws Exception {
		try (InputStream in = EventPublisherIntegrationTest.class.getClassLoader().getResourceAsStream(resource)) {
			String schemaJson = new String(in.readAllBytes(), StandardCharsets.UTF_8);
			SchemaRegistry registry = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12);
			return registry.getSchema(schemaJson);
		}
	}

	@Test
	void startRun_publishesTaskRunRequested_validAgainstItsJsonSchema() throws Exception {
		String queueName = "test." + UUID.randomUUID();
		Queue queue = new Queue(queueName, false, false, true);
		rabbitAdmin.declareQueue(queue);
		Binding binding = BindingBuilder.bind(queue).to(new TopicExchange(RabbitConfig.EVENTS_EXCHANGE))
				.with("task.run.requested");
		rabbitAdmin.declareBinding(binding);

		String token = bearerToken();
		String projectId = createProject(token);
		String taskId = createTask(token, projectId);

		mockMvc.perform(post("/api/v1/tasks/{id}/start-run", taskId)
						.header("Authorization", token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"agentKey\":\"custom-shell\"}"))
				.andExpect(status().isCreated());

		Message message = rabbitTemplate.receive(queueName, 5000);
		assertThat(message).as("task.run.requested message on %s", queueName).isNotNull();

		String body = new String(message.getBody(), StandardCharsets.UTF_8);
		Schema schema = loadSchema("task.run.requested.json");
		JsonNode node = OBJECT_MAPPER.readTree(body);
		List<Error> errors = schema.validate(node);
		assertThat(errors).as("schema errors: %s", errors).isEmpty();

		assertThat((String) JsonPath.read(body, "$.event_type")).isEqualTo("task.run.requested");
		assertThat((Integer) JsonPath.read(body, "$.version")).isEqualTo(1);
		assertThat((String) JsonPath.read(body, "$.payload.task_id")).isEqualTo(taskId);
		assertThat((String) JsonPath.read(body, "$.payload.agent_key")).isEqualTo("custom-shell");
		assertThat((String) JsonPath.read(body, "$.payload.auth_mode")).isEqualTo("api_key");
	}

	@Test
	void internalStatusTransition_publishesRunStatusChanged_validAgainstItsJsonSchema() throws Exception {
		String queueName = "test." + UUID.randomUUID();
		Queue queue = new Queue(queueName, false, false, true);
		rabbitAdmin.declareQueue(queue);
		Binding binding = BindingBuilder.bind(queue).to(new TopicExchange(RabbitConfig.EVENTS_EXCHANGE))
				.with("run.status.changed");
		rabbitAdmin.declareBinding(binding);

		String token = bearerToken();
		String projectId = createProject(token);
		String taskId = createTask(token, projectId);
		String startRunResponse = mockMvc.perform(post("/api/v1/tasks/{id}/start-run", taskId)
						.header("Authorization", token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"agentKey\":\"custom-shell\"}"))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		String runId = JsonPath.read(startRunResponse, "$.id");

		// start-run's own CREATED->QUEUED transition already published one run.status.changed message.
		Message message = rabbitTemplate.receive(queueName, 5000);
		assertThat(message).isNotNull();

		String body = new String(message.getBody(), StandardCharsets.UTF_8);
		Schema schema = loadSchema("run.status.changed.json");
		JsonNode node = OBJECT_MAPPER.readTree(body);
		List<Error> errors = schema.validate(node);
		assertThat(errors).as("schema errors: %s", errors).isEmpty();

		assertThat((String) JsonPath.read(body, "$.payload.run_id")).isEqualTo(runId);
		assertThat((String) JsonPath.read(body, "$.payload.from")).isEqualTo("CREATED");
		assertThat((String) JsonPath.read(body, "$.payload.to")).isEqualTo("QUEUED");
	}

	@Test
	void cancelRun_publishesRunCancelRequested_validAgainstItsJsonSchema() throws Exception {
		String queueName = "test." + UUID.randomUUID();
		Queue queue = new Queue(queueName, false, false, true);
		rabbitAdmin.declareQueue(queue);
		Binding binding = BindingBuilder.bind(queue).to(new TopicExchange(RabbitConfig.EVENTS_EXCHANGE))
				.with("run.cancel.requested");
		rabbitAdmin.declareBinding(binding);

		String token = bearerToken();
		String projectId = createProject(token);
		String taskId = createTask(token, projectId);
		String startRunResponse = mockMvc.perform(post("/api/v1/tasks/{id}/start-run", taskId)
						.header("Authorization", token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"agentKey\":\"custom-shell\"}"))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		String runId = JsonPath.read(startRunResponse, "$.id");

		mockMvc.perform(post("/api/v1/runs/{id}/cancel", runId).header("Authorization", token))
				.andExpect(status().isOk());

		Message message = rabbitTemplate.receive(queueName, 5000);
		assertThat(message).isNotNull();

		String body = new String(message.getBody(), StandardCharsets.UTF_8);
		Schema schema = loadSchema("run.cancel.requested.json");
		JsonNode node = OBJECT_MAPPER.readTree(body);
		List<Error> errors = schema.validate(node);
		assertThat(errors).as("schema errors: %s", errors).isEmpty();

		assertThat((String) JsonPath.read(body, "$.payload.run_id")).isEqualTo(runId);
	}

	@Test
	void pullRequestCreated_publishesPrCreated_validAgainstItsJsonSchema() throws Exception {
		when(gitHubClient.createPullRequest(any(), any(), any(), any(), any(), any(), any()))
				.thenReturn(new GitHubClient.PullRequestResult(3, "https://github.com/org/event/pull/3"));

		String queueName = "test." + UUID.randomUUID();
		Queue queue = new Queue(queueName, false, false, true);
		rabbitAdmin.declareQueue(queue);
		Binding binding = BindingBuilder.bind(queue).to(new TopicExchange(RabbitConfig.EVENTS_EXCHANGE))
				.with("pr.created");
		rabbitAdmin.declareBinding(binding);

		String token = bearerToken();
		mockMvc.perform(post("/api/v1/integrations")
						.header("Authorization", token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"type\":\"github\",\"name\":\"primary-github\",\"secret\":\"ghp_test-token\",\"authMode\":\"pat\"}"))
				.andExpect(status().isCreated());

		String projectId = createProject(token);
		String taskId = createTask(token, projectId);
		String startRunResponse = mockMvc.perform(post("/api/v1/tasks/{id}/start-run", taskId)
						.header("Authorization", token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"agentKey\":\"custom-shell\"}"))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		String runId = JsonPath.read(startRunResponse, "$.id");

		for (String status : List.of("PREPARING_WORKSPACE", "RUNNING_AGENT", "RUNNING_TESTS", "REVIEWING",
				"WAITING_APPROVAL")) {
			mockMvc.perform(post("/internal/v1/runs/{id}/status", runId)
							.header("X-Talos-Internal-Token", "test-internal-token-not-for-production-use-32bytes+")
							.contentType(MediaType.APPLICATION_JSON)
							.content("{\"status\":\"" + status + "\"}"))
					.andExpect(status().isOk());
		}
		String approvalsResponse = mockMvc
				.perform(get("/api/v1/approvals").header("Authorization", token).param("runId", runId))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		String approvalId = JsonPath.read(approvalsResponse, "$.content[0].id");
		mockMvc.perform(post("/api/v1/approvals/{id}/approve", approvalId)
						.header("Authorization", token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isOk());

		mockMvc.perform(post("/internal/v1/runs/{id}/pull-request", runId)
						.header("X-Talos-Internal-Token", "test-internal-token-not-for-production-use-32bytes+")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"branchName\":\"agent/task-1-demo\",\"commitSha\":\"abc123\"}"))
				.andExpect(status().isOk());

		Message message = rabbitTemplate.receive(queueName, 5000);
		assertThat(message).isNotNull();

		String body = new String(message.getBody(), StandardCharsets.UTF_8);
		Schema schema = loadSchema("pr.created.json");
		JsonNode node = OBJECT_MAPPER.readTree(body);
		List<Error> errors = schema.validate(node);
		assertThat(errors).as("schema errors: %s", errors).isEmpty();

		assertThat((String) JsonPath.read(body, "$.payload.run_id")).isEqualTo(runId);
		assertThat((Integer) JsonPath.read(body, "$.payload.pr_number")).isEqualTo(3);
		assertThat((String) JsonPath.read(body, "$.payload.pr_url")).isEqualTo("https://github.com/org/event/pull/3");
	}

	@Test
	void stagingAutoDeploy_publishesDeployRequested_validAgainstItsJsonSchema() throws Exception {
		String queueName = "test." + UUID.randomUUID();
		Queue queue = new Queue(queueName, false, false, true);
		rabbitAdmin.declareQueue(queue);
		Binding binding = BindingBuilder.bind(queue).to(new TopicExchange(RabbitConfig.EVENTS_EXCHANGE))
				.with("deploy.requested");
		rabbitAdmin.declareBinding(binding);

		String token = bearerToken();
		mockMvc.perform(post("/api/v1/integrations")
						.header("Authorization", token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"type":"dokploy","name":"primary-dokploy","configJson":{"baseUrl":"https://dokploy.test"},
								 "secret":"dokploy-test-key","authMode":"api_key"}
								"""))
				.andExpect(status().isCreated());

		String projectResponse = mockMvc.perform(post("/api/v1/projects")
						.header("Authorization", token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name":"Deploy Event Project %s","repoUrl":"git@github.com:org/deploy-event.git","stackType":"spring-boot"}
								""".formatted(UUID.randomUUID())))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		String projectId = JsonPath.read(projectResponse, "$.id");
		mockMvc.perform(post("/api/v1/projects/{id}/sync-config", projectId)
						.header("Authorization", token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"configYaml":"project:\\n  name: deploy-event\\n  type: spring-boot\\n  repo: git@github.com:org/deploy-event.git\\ncommands:\\n  test: \\"./mvnw test\\"\\ndeploy:\\n  provider: dokploy\\n  app_id: event-app-id\\n  environment: staging\\n  approval_required: false\\n"}
								"""))
				.andExpect(status().isOk());

		String taskId = createTask(token, projectId);
		String startRunResponse = mockMvc.perform(post("/api/v1/tasks/{id}/start-run", taskId)
						.header("Authorization", token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"agentKey\":\"custom-shell\"}"))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		String runId = JsonPath.read(startRunResponse, "$.id");

		for (String status : List.of("PREPARING_WORKSPACE", "RUNNING_AGENT", "RUNNING_TESTS", "REVIEWING",
				"WAITING_APPROVAL")) {
			mockMvc.perform(post("/internal/v1/runs/{id}/status", runId)
							.header("X-Talos-Internal-Token", "test-internal-token-not-for-production-use-32bytes+")
							.contentType(MediaType.APPLICATION_JSON)
							.content("{\"status\":\"" + status + "\"}"))
					.andExpect(status().isOk());
		}
		String approvalsResponse = mockMvc
				.perform(get("/api/v1/approvals").header("Authorization", token).param("runId", runId))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		String approvalId = JsonPath.read(approvalsResponse, "$.content[0].id");
		mockMvc.perform(post("/api/v1/approvals/{id}/approve", approvalId)
						.header("Authorization", token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isOk());
		mockMvc.perform(post("/internal/v1/runs/{id}/status", runId)
						.header("X-Talos-Internal-Token", "test-internal-token-not-for-production-use-32bytes+")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"status\":\"COMPLETED\"}"))
				.andExpect(status().isOk());

		mockMvc.perform(post("/api/v1/runs/{id}/deploy", runId).header("Authorization", token))
				.andExpect(status().isOk());

		Message message = rabbitTemplate.receive(queueName, 5000);
		assertThat(message).isNotNull();

		String body = new String(message.getBody(), StandardCharsets.UTF_8);
		Schema schema = loadSchema("deploy.requested.json");
		JsonNode node = OBJECT_MAPPER.readTree(body);
		List<Error> errors = schema.validate(node);
		assertThat(errors).as("schema errors: %s", errors).isEmpty();

		assertThat((String) JsonPath.read(body, "$.payload.run_id")).isEqualTo(runId);
		assertThat((String) JsonPath.read(body, "$.payload.environment")).isEqualTo("staging");
		assertThat((String) JsonPath.read(body, "$.payload.dokploy_app_id")).isEqualTo("event-app-id");
	}

	private String bearerToken() throws Exception {
		String response = mockMvc.perform(post("/api/v1/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\":\"admin@test.local\",\"password\":\"test-admin-password\"}"))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		return "Bearer " + JsonPath.<String>read(response, "$.token");
	}

	private String createProject(String token) throws Exception {
		String response = mockMvc.perform(post("/api/v1/projects")
						.header("Authorization", token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name":"Event Project %s","repoUrl":"git@github.com:org/event.git","stackType":"spring-boot"}
								""".formatted(UUID.randomUUID())))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return JsonPath.read(response, "$.id");
	}

	private String createTask(String token, String projectId) throws Exception {
		String response = mockMvc.perform(post("/api/v1/tasks")
						.header("Authorization", token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"projectId\":\"" + projectId + "\",\"title\":\"Event task\"}"))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return JsonPath.read(response, "$.id");
	}
}
