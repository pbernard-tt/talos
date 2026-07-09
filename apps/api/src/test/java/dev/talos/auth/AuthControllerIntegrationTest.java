package dev.talos.auth;

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

@Testcontainers
@SpringBootTest(properties = {
		"talos.jwt-secret=test-only-jwt-signing-secret-not-for-production-use-32bytes+",
		"talos.admin-email=admin@test.local",
		"talos.admin-password=test-admin-password"
})
@AutoConfigureMockMvc
class AuthControllerIntegrationTest {

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
		// RabbitMQ/Redis became load-bearing actuator health checks in Phase 5 (application.yml's
		// management.health.{rabbit,redis}.enabled overrides were removed); actuatorHealth_isPublic
		// needs real containers, not unreachable localhost addresses, or /actuator/health reports 503.
		registry.add("spring.data.redis.url",
				() -> "redis://" + redis.getHost() + ":" + redis.getMappedPort(6379));
	}

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private AuditEventRepository auditEventRepository;

	@Test
	void loginWithSeededCredentials_returnsJwtAndWritesAuditRow() throws Exception {
		mockMvc.perform(post("/api/v1/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\":\"admin@test.local\",\"password\":\"test-admin-password\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.token").isNotEmpty())
				.andExpect(jsonPath("$.expiresAt").isNotEmpty());

		User admin = userRepository.findByEmail("admin@test.local").orElseThrow();
		assertThat(admin.getRole()).isEqualTo(Role.OWNER);

		List<AuditEvent> loginEvents = auditEventRepository.findAll().stream()
				.filter(e -> "user.login".equals(e.getEventType()))
				.toList();
		assertThat(loginEvents)
				.anyMatch(e -> admin.getId().equals(e.getActorUserId()) && "user".equals(e.getEntityType()));
	}

	@Test
	void loginWithWrongPassword_returns401WithErrorEnvelope() throws Exception {
		mockMvc.perform(post("/api/v1/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\":\"admin@test.local\",\"password\":\"wrong-password\"}"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"));
	}

	@Test
	void unauthenticatedRequestToProtectedApi_returns401WithErrorEnvelope() throws Exception {
		mockMvc.perform(get("/api/v1/does-not-matter"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
	}

	@Test
	void actuatorHealth_isPublic() throws Exception {
		mockMvc.perform(get("/actuator/health"))
				.andExpect(status().isOk());
	}
}
