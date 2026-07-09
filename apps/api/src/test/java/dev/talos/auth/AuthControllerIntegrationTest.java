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
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

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

	@DynamicPropertySource
	static void disableUnusedIntegrations(DynamicPropertyRegistry registry) {
		// No RabbitMQ/Redis container in this test; those integrations aren't load-bearing until
		// later phases, so point them at addresses that won't be dialed during this test's requests.
		registry.add("spring.rabbitmq.addresses", () -> "amqp://guest:guest@localhost:5672");
		registry.add("spring.data.redis.url", () -> "redis://localhost:6379");
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
