package dev.talos.auth;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Section 16 Phase 15 acceptance: "role x endpoint-class matrix covered by tests"; "a VIEWER
 * attempting an approval receives 403 plus an audit row"; "no endpoint relies on UI hiding for
 * protection." Covers one representative endpoint per tier class rather than every single
 * endpoint -- {@code @PreAuthorize} denial fires before the controller method body runs (proven by
 * the {@code AuthorizationDeniedException} handling in AuthorizationExceptionHandler), so a fixed
 * random UUID path is enough to prove the role gate without needing real fixture data; the request
 * body must still be syntactically/semantically valid where {@code @Valid} applies, because Spring
 * MVC resolves and validates method arguments before the method-security AOP interceptor runs.
 */
@Testcontainers
@SpringBootTest(properties = {
		"talos.jwt-secret=test-only-jwt-signing-secret-not-for-production-use-32bytes+",
		"talos.admin-email=admin@test.local",
		"talos.admin-password=test-admin-password",
		"talos.internal-api-token=test-internal-token-not-for-production-use-32bytes+"
})
@AutoConfigureMockMvc
class RoleAuthorizationMatrixTest {

	private static final List<Role> HIERARCHY_HIGH_TO_LOW = List.of(Role.OWNER, Role.MAINTAINER, Role.REVIEWER, Role.VIEWER);
	private static final String RANDOM_ID = UUID.randomUUID().toString();

	@Container
	@ServiceConnection
	static PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres"));

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
	private UserRepository userRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	// Static and cached across this class's test methods (one shared Spring context/Testcontainers
	// Redis): logging in fresh per test method would otherwise trip Phase 11's login rate limiter,
	// which counts attempts per client IP and doesn't distinguish MockMvc's synthetic requests.
	private static final Map<Role, String> tokenCache = new EnumMap<>(Role.class);

	private String tokenFor(Role role) throws Exception {
		String cached = tokenCache.get(role);
		if (cached != null) {
			return cached;
		}
		String email = "matrix-" + role + "-" + UUID.randomUUID() + "@test.local";
		userRepository.save(new User(email, "Matrix " + role, passwordEncoder.encode("test-password-12345"), role));
		String response = mockMvc.perform(post("/api/v1/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\":\"" + email + "\",\"password\":\"test-password-12345\"}"))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		String token = "Bearer " + JsonPath.<String>read(response, "$.token");
		tokenCache.put(role, token);
		return token;
	}

	private static boolean meetsMinimum(Role actual, Role minimum) {
		return HIERARCHY_HIGH_TO_LOW.indexOf(actual) <= HIERARCHY_HIGH_TO_LOW.indexOf(minimum);
	}

	private MockHttpServletRequestBuilder request(String method, String path, String body) {
		MockHttpServletRequestBuilder builder = switch (method) {
			case "POST" -> post(path);
			case "PUT" -> put(path);
			case "PATCH" -> patch(path);
			case "GET" -> get(path);
			default -> throw new IllegalArgumentException(method);
		};
		if (body != null) {
			builder = builder.contentType(MediaType.APPLICATION_JSON).content(body);
		}
		return builder;
	}

	/** Asserts every role at or above minRole reaches the controller body (any non-403 status --
	 * 200/201/404/409/422 all prove the role gate passed); every role below it is denied with 403
	 * FORBIDDEN. */
	private void assertRoleGate(String method, String path, Role minRole, String body) throws Exception {
		for (Role role : Role.values()) {
			String token = tokenFor(role);
			var result = mockMvc.perform(request(method, path, body).header("Authorization", token))
					.andReturn();
			int status = result.getResponse().getStatus();
			if (meetsMinimum(role, minRole)) {
				assertThat(status)
						.withFailMessage("%s %s: role %s (>= %s) unexpectedly got 403", method, path, role, minRole)
						.isNotEqualTo(403);
			} else {
				assertThat(status)
						.withFailMessage("%s %s: role %s (< %s) expected 403 but got %d", method, path, role, minRole, status)
						.isEqualTo(403);
			}
		}
	}

	@Test
	void maintainerTierEndpoints_rejectReviewerAndViewer_admitMaintainerAndOwner() throws Exception {
		assertRoleGate("POST", "/api/v1/projects", Role.MAINTAINER,
				"{\"name\":\"Matrix Project\",\"repoUrl\":\"git@github.com:org/matrix.git\",\"stackType\":\"spring-boot\"}");
		assertRoleGate("PUT", "/api/v1/projects/" + RANDOM_ID, Role.MAINTAINER,
				"{\"name\":\"x\",\"repoUrl\":\"git@github.com:org/x.git\",\"defaultBranch\":\"main\",\"stackType\":\"spring-boot\"}");
		assertRoleGate("POST", "/api/v1/projects/" + RANDOM_ID + "/sync-config", Role.MAINTAINER,
				"{\"configYaml\":\"project:\\n  name: x\\n\"}");
		assertRoleGate("POST", "/api/v1/projects/" + RANDOM_ID + "/memory/documents", Role.MAINTAINER,
				"{\"sourceType\":\"OPERATOR_NOTE\",\"content\":\"note\"}");
		assertRoleGate("POST", "/api/v1/tasks", Role.MAINTAINER,
				"{\"projectId\":\"" + RANDOM_ID + "\",\"title\":\"Matrix Task\"}");
		assertRoleGate("PATCH", "/api/v1/tasks/" + RANDOM_ID, Role.MAINTAINER, "{}");
		assertRoleGate("POST", "/api/v1/tasks/" + RANDOM_ID + "/move", Role.MAINTAINER,
				"{\"status\":\"READY\",\"boardPosition\":0}");
		assertRoleGate("POST", "/api/v1/tasks/" + RANDOM_ID + "/start-run", Role.MAINTAINER, null);
		assertRoleGate("POST", "/api/v1/runs/" + RANDOM_ID + "/cancel", Role.MAINTAINER, null);
	}

	@Test
	void reviewerTierEndpoints_rejectViewerOnly_admitReviewerMaintainerOwner() throws Exception {
		assertRoleGate("POST", "/api/v1/approvals/" + RANDOM_ID + "/approve", Role.REVIEWER, null);
		assertRoleGate("POST", "/api/v1/approvals/" + RANDOM_ID + "/reject", Role.REVIEWER, "{\"notes\":\"x\"}");
		assertRoleGate("POST", "/api/v1/approvals/" + RANDOM_ID + "/request-changes", Role.REVIEWER, "{\"notes\":\"x\"}");
	}

	@Test
	void ownerTierEndpoints_rejectEveryoneElse_admitOwnerOnly() throws Exception {
		assertRoleGate("POST", "/api/v1/runs/" + RANDOM_ID + "/deploy", Role.OWNER, null);
		assertRoleGate("GET", "/api/v1/integrations", Role.OWNER, null);
		assertRoleGate("POST", "/api/v1/integrations", Role.OWNER,
				"{\"type\":\"github\",\"name\":\"Matrix Integration\",\"configJson\":{}}");
		assertRoleGate("POST", "/api/v1/integrations/" + RANDOM_ID + "/test", Role.OWNER, null);
		assertRoleGate("GET", "/api/v1/users", Role.OWNER, null);
		assertRoleGate("PATCH", "/api/v1/users/" + RANDOM_ID, Role.OWNER, "{}");
	}

	@Test
	void readOnlyEndpoints_admitEveryRole() throws Exception {
		for (Role role : Role.values()) {
			String token = tokenFor(role);
			mockMvc.perform(get("/api/v1/projects").header("Authorization", token)).andExpect(status().isOk());
			mockMvc.perform(get("/api/v1/tasks").header("Authorization", token)).andExpect(status().isOk());
			mockMvc.perform(get("/api/v1/runs").header("Authorization", token)).andExpect(status().isOk());
			mockMvc.perform(get("/api/v1/approvals").header("Authorization", token)).andExpect(status().isOk());
		}
	}
}
