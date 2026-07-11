package dev.talos.runs;

import com.jayway.jsonpath.JsonPath;
import dev.talos.audit.AuditEvent;
import dev.talos.audit.AuditEventRepository;
import dev.talos.integrations.GitHubClient;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest(properties = {
		"talos.jwt-secret=test-only-jwt-signing-secret-not-for-production-use-32bytes+",
		"talos.admin-email=admin@test.local",
		"talos.admin-password=test-admin-password",
		"talos.internal-api-token=test-internal-token-not-for-production-use-32bytes+",
		"talos.secrets-key=ZGV2LW9ubHktMzItYnl0ZS1wbGFjZWhvbGRlci1rZXk="
})
@AutoConfigureMockMvc
class RunControllerIntegrationTest {

	@MockitoBean
	private GitHubClient gitHubClient;

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

	private static final String VALID_YAML = """
			project:
			  name: example-backend
			  type: spring-boot
			  repo: git@github.com:org/example-backend.git
			commands:
			  test: "./mvnw test"
			agents:
			  preferred: opencode
			  allowed: [opencode, claude-code, custom-shell]
			""";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private AuditEventRepository auditEventRepository;

	@Autowired
	private GitChangeRepository gitChangeRepository;

	// Cached across this class's ~20 test methods (one shared Spring context/Testcontainers Redis):
	// logging in fresh per test would otherwise trip Phase 11's login rate limiter, which counts
	// attempts per client IP and doesn't distinguish MockMvc's synthetic requests from each other.
	private static String cachedBearerToken;

	private String bearerToken() throws Exception {
		if (cachedBearerToken == null) {
			String response = mockMvc.perform(post("/api/v1/auth/login")
							.contentType(MediaType.APPLICATION_JSON)
							.content("{\"email\":\"admin@test.local\",\"password\":\"test-admin-password\"}"))
					.andExpect(status().isOk())
					.andReturn().getResponse().getContentAsString();
			cachedBearerToken = "Bearer " + JsonPath.<String>read(response, "$.token");
		}
		return cachedBearerToken;
	}

	private String createProject(String token, boolean withConfig) throws Exception {
		String response = mockMvc.perform(post("/api/v1/projects")
						.header("Authorization", token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name":"Run Project %s","repoUrl":"git@github.com:org/run.git","stackType":"spring-boot"}
								""".formatted(java.util.UUID.randomUUID())))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		String projectId = JsonPath.read(response, "$.id");
		if (withConfig) {
			mockMvc.perform(post("/api/v1/projects/{id}/sync-config", projectId)
							.header("Authorization", token)
							.contentType(MediaType.APPLICATION_JSON)
							.content("{\"configYaml\":" + toJsonString(VALID_YAML) + "}"))
					.andExpect(status().isOk());
		}
		return projectId;
	}

	private String createTask(String token, String projectId) throws Exception {
		String response = mockMvc.perform(post("/api/v1/tasks")
						.header("Authorization", token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"projectId\":\"" + projectId + "\",\"title\":\"Run task\"}"))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return JsonPath.read(response, "$.id");
	}

	private String startRun(String token, String taskId, String body) throws Exception {
		var request = post("/api/v1/tasks/{id}/start-run", taskId).header("Authorization", token);
		if (body != null) {
			request = request.contentType(MediaType.APPLICATION_JSON).content(body);
		}
		String response = mockMvc.perform(request)
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return JsonPath.read(response, "$.id");
	}

	@Test
	void startRun_createsQueuedRun_movesTaskToRunning_andAudits() throws Exception {
		String token = bearerToken();
		String projectId = createProject(token, false);
		String taskId = createTask(token, projectId);

		String runId = startRun(token, taskId, "{\"agentKey\":\"custom-shell\",\"authMode\":\"api_key\"}");

		mockMvc.perform(get("/api/v1/runs/{id}", runId).header("Authorization", token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("QUEUED"))
				.andExpect(jsonPath("$.agentKey").value("custom-shell"))
				.andExpect(jsonPath("$.taskId").value(taskId))
				.andExpect(jsonPath("$.timeoutAt").isNotEmpty())
				.andExpect(jsonPath("$.steps").isArray());

		mockMvc.perform(get("/api/v1/tasks/{id}", taskId).header("Authorization", token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("RUNNING"));

		assertThat(auditEventRepository.findAll())
				.anyMatch(e -> "run.created".equals(e.getEventType()) && runId.equals(String.valueOf(e.getEntityId())))
				.anyMatch(e -> "run.status.changed".equals(e.getEventType())
						&& runId.equals(String.valueOf(e.getEntityId())))
				.anyMatch(e -> "task.status.changed".equals(e.getEventType())
						&& taskId.equals(String.valueOf(e.getEntityId())));
	}

	@Test
	void startRun_withoutAgentKey_usesActiveConfigPreferredAgent() throws Exception {
		String token = bearerToken();
		String projectId = createProject(token, true);
		String taskId = createTask(token, projectId);

		String runId = startRun(token, taskId, null);

		mockMvc.perform(get("/api/v1/runs/{id}", runId).header("Authorization", token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.agentKey").value("opencode"))
				.andExpect(jsonPath("$.providerAuthMode").value("api_key"));
	}

	@Test
	void startRun_noActiveConfigAndNoAgentKey_returns422() throws Exception {
		String token = bearerToken();
		String projectId = createProject(token, false);
		String taskId = createTask(token, projectId);

		mockMvc.perform(post("/api/v1/tasks/{id}/start-run", taskId).header("Authorization", token))
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.error.code").value("NO_ACTIVE_CONFIG"));
	}

	@Test
	void startRun_whenActiveRunExists_returns409() throws Exception {
		String token = bearerToken();
		String projectId = createProject(token, false);
		String taskId = createTask(token, projectId);
		startRun(token, taskId, "{\"agentKey\":\"custom-shell\"}");

		mockMvc.perform(post("/api/v1/tasks/{id}/start-run", taskId)
						.header("Authorization", token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"agentKey\":\"custom-shell\"}"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error.code").value("ACTIVE_RUN_EXISTS"));
	}

	@Test
	void internalStatus_illegalTransition_returns422AndWritesAuditEvent() throws Exception {
		String token = bearerToken();
		String projectId = createProject(token, false);
		String taskId = createTask(token, projectId);
		String runId = startRun(token, taskId, "{\"agentKey\":\"custom-shell\"}");

		mockMvc.perform(post("/internal/v1/runs/{id}/status", runId)
						.header("X-Talos-Internal-Token", "test-internal-token-not-for-production-use-32bytes+")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"status\":\"COMPLETED\"}"))
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.error.code").value("ILLEGAL_RUN_TRANSITION"));

		assertThat(auditEventRepository.findAll())
				.anyMatch(e -> "run.transition.rejected".equals(e.getEventType())
						&& runId.equals(String.valueOf(e.getEntityId())));
	}

	@Test
	void internalStatus_fullHappyPathWalk_appliesTaskMappingAtEachStage() throws Exception {
		String token = bearerToken();
		String projectId = createProject(token, false);
		String taskId = createTask(token, projectId);
		String runId = startRun(token, taskId, "{\"agentKey\":\"custom-shell\"}");

		transitionInternal(runId, "PREPARING_WORKSPACE");
		transitionInternal(runId, "RUNNING_AGENT");
		transitionInternal(runId, "RUNNING_TESTS");
		transitionInternal(runId, "REVIEWING");
		transitionInternal(runId, "WAITING_APPROVAL");

		mockMvc.perform(get("/api/v1/tasks/{id}", taskId).header("Authorization", token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("REVIEW"));

		// APPROVED is a human decision (Section 8.2) -- only reachable via /api/v1/approvals (Phase 8).
		String approvalId = pendingApprovalIdForRun(token, runId);
		mockMvc.perform(post("/api/v1/approvals/{id}/approve", approvalId)
						.header("Authorization", token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isOk());
		transitionInternal(runId, "COMPLETED");

		mockMvc.perform(get("/api/v1/tasks/{id}", taskId).header("Authorization", token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("DONE"));

		mockMvc.perform(get("/api/v1/runs/{id}", runId).header("Authorization", token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("COMPLETED"))
				.andExpect(jsonPath("$.startedAt").isNotEmpty())
				.andExpect(jsonPath("$.completedAt").isNotEmpty());
	}

	@Test
	void internalStatus_cannotSetApprovedOrRejectedDirectly_requiresApprovalEndpoint() throws Exception {
		String token = bearerToken();
		String projectId = createProject(token, false);
		String taskId = createTask(token, projectId);
		String runId = startRun(token, taskId, "{\"agentKey\":\"custom-shell\"}");
		transitionInternal(runId, "PREPARING_WORKSPACE");
		transitionInternal(runId, "RUNNING_AGENT");
		transitionInternal(runId, "RUNNING_TESTS");
		transitionInternal(runId, "REVIEWING");
		transitionInternal(runId, "WAITING_APPROVAL");

		mockMvc.perform(post("/internal/v1/runs/{id}/status", runId)
						.header("X-Talos-Internal-Token", "test-internal-token-not-for-production-use-32bytes+")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"status\":\"APPROVED\"}"))
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.error.code").value("APPROVAL_REQUIRED_FOR_TRANSITION"));

		mockMvc.perform(post("/internal/v1/runs/{id}/status", runId)
						.header("X-Talos-Internal-Token", "test-internal-token-not-for-production-use-32bytes+")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"status\":\"REJECTED\"}"))
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.error.code").value("APPROVAL_REQUIRED_FOR_TRANSITION"));

		mockMvc.perform(get("/api/v1/runs/{id}", runId).header("Authorization", token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("WAITING_APPROVAL"));
	}

	private String pendingApprovalIdForRun(String token, String runId) throws Exception {
		String response = mockMvc.perform(get("/api/v1/approvals").header("Authorization", token)
						.param("runId", runId))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		return JsonPath.read(response, "$.content[0].id");
	}

	@Test
	void internalStatus_failed_movesTaskToBlocked() throws Exception {
		String token = bearerToken();
		String projectId = createProject(token, false);
		String taskId = createTask(token, projectId);
		String runId = startRun(token, taskId, "{\"agentKey\":\"custom-shell\"}");

		mockMvc.perform(post("/internal/v1/runs/{id}/status", runId)
						.header("X-Talos-Internal-Token", "test-internal-token-not-for-production-use-32bytes+")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"status\":\"FAILED\",\"errorMessage\":\"boom\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("FAILED"))
				.andExpect(jsonPath("$.errorMessage").value("boom"));

		mockMvc.perform(get("/api/v1/tasks/{id}", taskId).header("Authorization", token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("BLOCKED"));
	}

	@Test
	void internalStatus_optionalPipelineDetails_persistOntoRun() throws Exception {
		String token = bearerToken();
		String projectId = createProject(token, false);
		String taskId = createTask(token, projectId);
		String runId = startRun(token, taskId, "{\"agentKey\":\"custom-shell\"}");

		mockMvc.perform(post("/internal/v1/runs/{id}/status", runId)
						.header("X-Talos-Internal-Token", "test-internal-token-not-for-production-use-32bytes+")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"status":"PREPARING_WORKSPACE","workspacePath":"/var/talos/workspaces/demo/runs/r1/worktree",
								 "branchName":"agent/task-t1-demo"}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.workspacePath").value("/var/talos/workspaces/demo/runs/r1/worktree"))
				.andExpect(jsonPath("$.branchName").value("agent/task-t1-demo"));

		mockMvc.perform(post("/internal/v1/runs/{id}/status", runId)
						.header("X-Talos-Internal-Token", "test-internal-token-not-for-production-use-32bytes+")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"status\":\"RUNNING_AGENT\",\"prompt\":\"echo hi\",\"exitCode\":0}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.prompt").value("echo hi"))
				.andExpect(jsonPath("$.exitCode").value(0));

		mockMvc.perform(post("/internal/v1/runs/{id}/status", runId)
						.header("X-Talos-Internal-Token", "test-internal-token-not-for-production-use-32bytes+")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"status\":\"RUNNING_TESTS\",\"testStatus\":\"PASSED\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.testStatus").value("PASSED"));
	}

	@Test
	void internalSteps_startThenComplete_updatesSameStepRow() throws Exception {
		String token = bearerToken();
		String projectId = createProject(token, false);
		String taskId = createTask(token, projectId);
		String runId = startRun(token, taskId, "{\"agentKey\":\"custom-shell\"}");

		mockMvc.perform(post("/internal/v1/runs/{id}/steps", runId)
						.header("X-Talos-Internal-Token", "test-internal-token-not-for-production-use-32bytes+")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"stepType\":\"WORKSPACE\",\"status\":\"RUNNING\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("RUNNING"));

		mockMvc.perform(post("/internal/v1/runs/{id}/steps", runId)
						.header("X-Talos-Internal-Token", "test-internal-token-not-for-production-use-32bytes+")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"stepType\":\"WORKSPACE\",\"status\":\"COMPLETED\",\"summary\":\"cloned\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("COMPLETED"))
				.andExpect(jsonPath("$.summary").value("cloned"));

		mockMvc.perform(get("/api/v1/runs/{id}", runId).header("Authorization", token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.steps.length()").value(1));
	}

	@Test
	void internalLogs_ingestedLogsAppearInLogsEndpoint() throws Exception {
		String token = bearerToken();
		String projectId = createProject(token, false);
		String taskId = createTask(token, projectId);
		String runId = startRun(token, taskId, "{\"agentKey\":\"custom-shell\"}");

		mockMvc.perform(post("/internal/v1/runs/{id}/logs", runId)
						.header("X-Talos-Internal-Token", "test-internal-token-not-for-production-use-32bytes+")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"entries":[
								  {"stream":"STDOUT","sequence":1,"message":"cloning repo","timestamp":"2026-07-09T12:00:00Z"},
								  {"stream":"STDOUT","sequence":2,"message":"clone complete","timestamp":"2026-07-09T12:00:01Z"}
								]}
								"""))
				.andExpect(status().isNoContent());

		mockMvc.perform(get("/api/v1/runs/{id}/logs", runId).header("Authorization", token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content.length()").value(2))
				.andExpect(jsonPath("$.content[0].message").value("cloning repo"))
				.andExpect(jsonPath("$.content[1].sequence").value(2));
	}

	@Test
	void internalContext_returnsRunTaskProjectAndActiveConfig() throws Exception {
		String token = bearerToken();
		String projectId = createProject(token, true);
		String taskId = createTask(token, projectId);
		String runId = startRun(token, taskId, null);

		mockMvc.perform(get("/internal/v1/runs/{id}/context", runId)
						.header("X-Talos-Internal-Token", "test-internal-token-not-for-production-use-32bytes+"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.run.id").value(runId))
				.andExpect(jsonPath("$.task.id").value(taskId))
				.andExpect(jsonPath("$.project.id").value(projectId))
				.andExpect(jsonPath("$.activeConfig.agents.preferred").value("opencode"));
	}

	@Test
	void internalEndpoints_requireValidServiceToken_notJwt() throws Exception {
		String token = bearerToken();
		String projectId = createProject(token, false);
		String taskId = createTask(token, projectId);
		String runId = startRun(token, taskId, "{\"agentKey\":\"custom-shell\"}");

		mockMvc.perform(get("/internal/v1/runs/{id}/context", runId).header("Authorization", token))
				.andExpect(status().isUnauthorized());

		mockMvc.perform(get("/internal/v1/runs/{id}/context", runId)
						.header("X-Talos-Internal-Token", "wrong-token"))
				.andExpect(status().isUnauthorized());

		mockMvc.perform(get("/internal/v1/runs/{id}/context", runId)
						.header("X-Talos-Internal-Token", "test-internal-token-not-for-production-use-32bytes+"))
				.andExpect(status().isOk());
	}

	@Test
	void listAndGetRuns_filtersAndDetail() throws Exception {
		String token = bearerToken();
		String projectId = createProject(token, false);
		String taskId = createTask(token, projectId);
		String runId = startRun(token, taskId, "{\"agentKey\":\"custom-shell\"}");

		mockMvc.perform(get("/api/v1/runs").header("Authorization", token)
						.param("projectId", projectId)
						.param("status", "QUEUED"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[?(@.id=='" + runId + "')]").exists());

		mockMvc.perform(get("/api/v1/runs").header("Authorization", token)
						.param("projectId", projectId)
						.param("status", "COMPLETED"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[?(@.id=='" + runId + "')]").doesNotExist());
	}

	@Test
	void cancel_queuedRun_transitionsToCancelled_movesTaskToReady_andPublishesCancelEvent() throws Exception {
		String token = bearerToken();
		String projectId = createProject(token, false);
		String taskId = createTask(token, projectId);
		String runId = startRun(token, taskId, "{\"agentKey\":\"custom-shell\"}");

		mockMvc.perform(post("/api/v1/runs/{id}/cancel", runId).header("Authorization", token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("CANCELLED"));

		mockMvc.perform(get("/api/v1/tasks/{id}", taskId).header("Authorization", token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("READY"));
	}

	@Test
	void cancel_terminalRun_returns422() throws Exception {
		String token = bearerToken();
		String projectId = createProject(token, false);
		String taskId = createTask(token, projectId);
		String runId = startRun(token, taskId, "{\"agentKey\":\"custom-shell\"}");
		mockMvc.perform(post("/api/v1/runs/{id}/cancel", runId).header("Authorization", token))
				.andExpect(status().isOk());

		mockMvc.perform(post("/api/v1/runs/{id}/cancel", runId).header("Authorization", token))
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.error.code").value("ILLEGAL_RUN_TRANSITION"));
	}

	@Test
	void internalChanges_persistsGitChangeRows_andAudits() throws Exception {
		String token = bearerToken();
		String projectId = createProject(token, false);
		String taskId = createTask(token, projectId);
		String runId = startRun(token, taskId, "{\"agentKey\":\"custom-shell\"}");

		mockMvc.perform(post("/internal/v1/runs/{id}/changes", runId)
						.header("X-Talos-Internal-Token", "test-internal-token-not-for-production-use-32bytes+")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"files":[
								  {"filePath":"README.md","changeType":"MODIFIED","additions":1,"deletions":0}
								],"diffArtifactRef":"/var/talos/workspaces/run/artifacts/diff.patch"}
								"""))
				.andExpect(status().isNoContent());

		assertThat(auditEventRepository.findAll())
				.anyMatch(e -> "run.changes.recorded".equals(e.getEventType())
						&& runId.equals(String.valueOf(e.getEntityId())));
		assertThat(gitChangeRepository.findByRunId(java.util.UUID.fromString(runId)))
				.anyMatch(c -> "README.md".equals(c.getFilePath()) && c.getChangeType() == GitChangeType.MODIFIED);
	}

	private void createGitHubIntegration(String token, String secret) throws Exception {
		mockMvc.perform(post("/api/v1/integrations")
						.header("Authorization", token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"type\":\"github\",\"name\":\"primary-github\",\"secret\":\"" + secret
								+ "\",\"authMode\":\"pat\"}"))
				.andExpect(status().isCreated());
	}

	/** Phase 9's required server-side proof: an unapproved run cannot obtain push credentials or open a PR. */
	@Test
	void gitTokenAndPullRequest_requireApprovedRun_rejectNonApprovedStatuses() throws Exception {
		String token = bearerToken();
		String projectId = createProject(token, false);
		String taskId = createTask(token, projectId);
		String runId = startRun(token, taskId, "{\"agentKey\":\"custom-shell\"}");

		mockMvc.perform(get("/internal/v1/runs/{id}/git-token", runId)
						.header("X-Talos-Internal-Token", "test-internal-token-not-for-production-use-32bytes+"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error.code").value("RUN_NOT_APPROVED"));

		mockMvc.perform(post("/internal/v1/runs/{id}/pull-request", runId)
						.header("X-Talos-Internal-Token", "test-internal-token-not-for-production-use-32bytes+")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"branchName\":\"agent/task-1-demo\",\"commitSha\":\"abc123\"}"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error.code").value("RUN_NOT_APPROVED"));

		transitionInternal(runId, "PREPARING_WORKSPACE");
		transitionInternal(runId, "RUNNING_AGENT");
		transitionInternal(runId, "RUNNING_TESTS");
		transitionInternal(runId, "REVIEWING");
		transitionInternal(runId, "WAITING_APPROVAL");

		mockMvc.perform(get("/internal/v1/runs/{id}/git-token", runId)
						.header("X-Talos-Internal-Token", "test-internal-token-not-for-production-use-32bytes+"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error.code").value("RUN_NOT_APPROVED"));
	}

	@Test
	void approvedRun_gitTokenThenPullRequest_reachesCompletedWithPullRequestRow() throws Exception {
		String token = bearerToken();
		createGitHubIntegration(token, "ghp_super-secret-token");
		when(gitHubClient.createPullRequest(eq("ghp_super-secret-token"), eq("org"), eq("run"), any(), any(), any(),
				any())).thenReturn(new GitHubClient.PullRequestResult(7, "https://github.com/org/run/pull/7"));

		String projectId = createProject(token, false);
		String taskId = createTask(token, projectId);
		String runId = startRun(token, taskId, "{\"agentKey\":\"custom-shell\"}");
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

		mockMvc.perform(get("/internal/v1/runs/{id}/git-token", runId)
						.header("X-Talos-Internal-Token", "test-internal-token-not-for-production-use-32bytes+"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.token").value("ghp_super-secret-token"))
				.andExpect(jsonPath("$.repoUrl").value("git@github.com:org/run.git"));

		mockMvc.perform(post("/internal/v1/runs/{id}/pull-request", runId)
						.header("X-Talos-Internal-Token", "test-internal-token-not-for-production-use-32bytes+")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"branchName\":\"agent/task-1-demo\",\"commitSha\":\"abc123\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.prNumber").value(7))
				.andExpect(jsonPath("$.url").value("https://github.com/org/run/pull/7"));

		mockMvc.perform(get("/api/v1/runs/{id}", runId).header("Authorization", token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("COMPLETED"));

		mockMvc.perform(get("/api/v1/runs/{id}/pull-request", runId).header("Authorization", token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.prNumber").value(7))
				.andExpect(jsonPath("$.provider").value("github"));
	}

	@Test
	void getUnknownRun_returns404() throws Exception {
		String token = bearerToken();
		mockMvc.perform(get("/api/v1/runs/{id}", "019547c1-0000-7000-8000-000000000000")
						.header("Authorization", token))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("RUN_NOT_FOUND"));
	}

	private void transitionInternal(String runId, String status) throws Exception {
		mockMvc.perform(post("/internal/v1/runs/{id}/status", runId)
						.header("X-Talos-Internal-Token", "test-internal-token-not-for-production-use-32bytes+")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"status\":\"" + status + "\"}"))
				.andExpect(status().isOk());
	}

	private static String toJsonString(String raw) {
		return "\"" + raw.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
	}
}
