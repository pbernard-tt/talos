package dev.talos.runs;

import com.jayway.jsonpath.JsonPath;
import dev.talos.integrations.DeployProvider;
import org.junit.jupiter.api.Test;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Phase 10 acceptance: "deploy button disabled without approval"; "mock-Dokploy integration test covers success and failure." */
@Testcontainers
@SpringBootTest(properties = {
		"talos.jwt-secret=test-only-jwt-signing-secret-not-for-production-use-32bytes+",
		"talos.admin-email=admin@test.local",
		"talos.admin-password=test-admin-password",
		"talos.internal-api-token=test-internal-token-not-for-production-use-32bytes+",
		"talos.secrets-key=ZGV2LW9ubHktMzItYnl0ZS1wbGFjZWhvbGRlci1rZXk="
})
@AutoConfigureMockMvc
class DeployControllerIntegrationTest {

	private static final String INTERNAL_TOKEN = "test-internal-token-not-for-production-use-32bytes+";

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

	@MockitoBean
	private DeployProvider deployProvider;

	private static final String PRODUCTION_DEPLOY_YAML = """
			project:
			  name: deploy-demo
			  type: spring-boot
			  repo: git@github.com:org/deploy-demo.git
			commands:
			  test: "./mvnw test"
			deploy:
			  provider: dokploy
			  app_id: prod-app-id
			  environment: production
			  approval_required: true
			""";

	private static final String STAGING_AUTO_DEPLOY_YAML = """
			project:
			  name: deploy-staging-demo
			  type: spring-boot
			  repo: git@github.com:org/deploy-staging-demo.git
			commands:
			  test: "./mvnw test"
			deploy:
			  provider: dokploy
			  app_id: staging-app-id
			  environment: staging
			  approval_required: false
			""";

	private String bearerToken() throws Exception {
		String response = mockMvc.perform(post("/api/v1/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\":\"admin@test.local\",\"password\":\"test-admin-password\"}"))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		return "Bearer " + JsonPath.<String>read(response, "$.token");
	}

	private void configureDokployIntegration(String token) throws Exception {
		mockMvc.perform(post("/api/v1/integrations")
						.header("Authorization", token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"type":"dokploy","name":"primary-dokploy","configJson":{"baseUrl":"https://dokploy.test"},
								 "secret":"dokploy-test-key","authMode":"api_key"}
								"""))
				.andExpect(status().isCreated());
	}

	private String createProjectWithConfig(String token, String name, String yaml) throws Exception {
		String response = mockMvc.perform(post("/api/v1/projects")
						.header("Authorization", token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name":"%s %s","repoUrl":"git@github.com:org/deploy.git","stackType":"spring-boot"}
								""".formatted(name, java.util.UUID.randomUUID())))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		String projectId = JsonPath.read(response, "$.id");
		mockMvc.perform(post("/api/v1/projects/{id}/sync-config", projectId)
						.header("Authorization", token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"configYaml\":" + toJsonString(yaml) + "}"))
				.andExpect(status().isOk());
		return projectId;
	}

	private String createTask(String token, String projectId) throws Exception {
		String response = mockMvc.perform(post("/api/v1/tasks")
						.header("Authorization", token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"projectId\":\"" + projectId + "\",\"title\":\"Deploy task\"}"))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return JsonPath.read(response, "$.id");
	}

	private void transitionInternal(String runId, String status) throws Exception {
		mockMvc.perform(post("/internal/v1/runs/{id}/status", runId)
						.header("X-Talos-Internal-Token", INTERNAL_TOKEN)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"status\":\"" + status + "\"}"))
				.andExpect(status().isOk());
	}

	private String pendingApprovalIdForRun(String token, String runId) throws Exception {
		String response = mockMvc.perform(get("/api/v1/approvals").header("Authorization", token)
						.param("runId", runId))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		return JsonPath.read(response, "$.content[0].id");
	}

	/** Walks a run to COMPLETED without going through push/PR (Phase 9 is orthogonal to this test). */
	private String runToCompleted(String token, String taskId) throws Exception {
		String response = mockMvc.perform(post("/api/v1/tasks/{id}/start-run", taskId)
						.header("Authorization", token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"agentKey\":\"custom-shell\"}"))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		String runId = JsonPath.read(response, "$.id");
		transitionInternal(runId, "PREPARING_WORKSPACE");
		transitionInternal(runId, "RUNNING_AGENT");
		transitionInternal(runId, "RUNNING_TESTS");
		transitionInternal(runId, "REVIEWING");
		transitionInternal(runId, "WAITING_APPROVAL");
		String approvalId = pendingApprovalIdForRun(token, runId);
		mockMvc.perform(post("/api/v1/approvals/{id}/approve", approvalId)
						.header("Authorization", token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isOk());
		transitionInternal(runId, "COMPLETED");
		return runId;
	}

	@Test
	void deploy_nonCompletedRun_returns422() throws Exception {
		String token = bearerToken();
		String projectId = createProjectWithConfig(token, "Not Completed", PRODUCTION_DEPLOY_YAML);
		String taskId = createTask(token, projectId);
		String response = mockMvc.perform(post("/api/v1/tasks/{id}/start-run", taskId)
						.header("Authorization", token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"agentKey\":\"custom-shell\"}"))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		String runId = JsonPath.read(response, "$.id");

		mockMvc.perform(post("/api/v1/runs/{id}/deploy", runId).header("Authorization", token))
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.error.code").value("DEPLOY_REQUIRES_COMPLETED_RUN"));
	}

	@Test
	void deploy_noDeployConfigured_returns422() throws Exception {
		String token = bearerToken();
		String projectId = createProjectWithConfig(token, "No Deploy Config",
				"project:\n  name: no-deploy\n  type: spring-boot\n  repo: git@github.com:org/no-deploy.git\ncommands:\n  test: \"./mvnw test\"\n");
		String taskId = createTask(token, projectId);
		String runId = runToCompleted(token, taskId);

		mockMvc.perform(post("/api/v1/runs/{id}/deploy", runId).header("Authorization", token))
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.error.code").value("NO_DEPLOY_CONFIGURED"));
	}

	@Test
	void deploy_productionEnvironment_createsPendingApproval_andNeverCallsProviderUntilApproved() throws Exception {
		String token = bearerToken();
		configureDokployIntegration(token);
		String projectId = createProjectWithConfig(token, "Production Deploy", PRODUCTION_DEPLOY_YAML);
		String taskId = createTask(token, projectId);
		String runId = runToCompleted(token, taskId);

		String response = mockMvc.perform(post("/api/v1/runs/{id}/deploy", runId).header("Authorization", token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.approvalRequired").value(true))
				.andExpect(jsonPath("$.approval.status").value("PENDING"))
				.andExpect(jsonPath("$.approval.approvalType").value("DEPLOY"))
				.andExpect(jsonPath("$.approval.environment").value("production"))
				.andExpect(jsonPath("$.environment.lastDeployStatus").doesNotExist())
				.andReturn().getResponse().getContentAsString();
		String approvalId = JsonPath.read(response, "$.approval.id");

		// The required proof: requesting a deploy never calls the provider by itself.
		verify(deployProvider, never()).trigger(any(), any(), any(), any());

		mockMvc.perform(post("/api/v1/approvals/{id}/approve", approvalId)
						.header("Authorization", token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isOk());

		verify(deployProvider).trigger("https://dokploy.test", "dokploy-test-key", "prod-app-id",
				"talos: deploy run " + runId + " to production");

		mockMvc.perform(get("/api/v1/runs/{id}/deploy", runId).header("Authorization", token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.lastDeployStatus").value("RUNNING"));
	}

	@Test
	void deploy_stagingWithApprovalNotRequired_triggersImmediately_noApprovalRow() throws Exception {
		String token = bearerToken();
		configureDokployIntegration(token);
		String projectId = createProjectWithConfig(token, "Staging Auto Deploy", STAGING_AUTO_DEPLOY_YAML);
		String taskId = createTask(token, projectId);
		String runId = runToCompleted(token, taskId);

		mockMvc.perform(post("/api/v1/runs/{id}/deploy", runId).header("Authorization", token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.approvalRequired").value(false))
				.andExpect(jsonPath("$.approval").doesNotExist())
				.andExpect(jsonPath("$.environment.lastDeployStatus").value("RUNNING"));

		verify(deployProvider).trigger("https://dokploy.test", "dokploy-test-key", "staging-app-id",
				"talos: deploy run " + runId + " to staging");

		mockMvc.perform(get("/api/v1/approvals").header("Authorization", token)
						.param("runId", runId).param("type", "DEPLOY"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content.length()").value(0));
	}

	/**
	 * Regression test: triggerNow used to be @Transactional, so marking the environment FAILED in
	 * its own catch block was rolled back by the very exception it was reacting to (Spring rolls
	 * back the whole transaction on an unchecked exception) -- the environment silently reverted to
	 * "never deployed" instead of recording the failure. Caught via a live docker-compose walk
	 * against an unreachable Dokploy base URL, not by a unit test, hence this addition.
	 */
	@Test
	void deploy_providerTriggerThrows_marksEnvironmentFailed_notSilentlyRolledBack() throws Exception {
		String token = bearerToken();
		configureDokployIntegration(token);
		org.mockito.Mockito.doThrow(new RuntimeException("connection refused"))
				.when(deployProvider).trigger(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
						org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
		String projectId = createProjectWithConfig(token, "Trigger Failure", STAGING_AUTO_DEPLOY_YAML);
		String taskId = createTask(token, projectId);
		String runId = runToCompleted(token, taskId);

		mockMvc.perform(post("/api/v1/runs/{id}/deploy", runId).header("Authorization", token))
				.andExpect(status().is5xxServerError());

		mockMvc.perform(get("/api/v1/runs/{id}/deploy", runId).header("Authorization", token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.lastDeployStatus").value("FAILED"));
	}

	private static String toJsonString(String raw) {
		return "\"" + raw.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
	}
}
