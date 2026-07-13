// SPDX-FileCopyrightText: 2026 Vulkan Technologies
// SPDX-License-Identifier: AGPL-3.0-or-later

package dev.talos.approvals;

import com.jayway.jsonpath.JsonPath;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Phase 8's Review and approval flow: auto-creation on WAITING_APPROVAL, the policy scan, and approve/reject/request-changes. */
@Testcontainers
@SpringBootTest(properties = {
		"talos.jwt-secret=test-only-jwt-signing-secret-not-for-production-use-32bytes+",
		"talos.admin-email=admin@test.local",
		"talos.admin-password=test-admin-password",
		"talos.internal-api-token=test-internal-token-not-for-production-use-32bytes+"
})
@AutoConfigureMockMvc
class ApprovalControllerIntegrationTest {

	private static final String INTERNAL_TOKEN = "test-internal-token-not-for-production-use-32bytes+";

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
		registry.add("spring.data.redis.url", () -> "redis://" + redis.getHost() + ":" + redis.getMappedPort(6379));
	}

	@Autowired
	private MockMvc mockMvc;

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
								{"name":"Approval Project %s","repoUrl":"git@github.com:org/approval.git","stackType":"spring-boot"}
								""".formatted(java.util.UUID.randomUUID())))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return JsonPath.read(response, "$.id");
	}

	private String createTask(String token, String projectId) throws Exception {
		String response = mockMvc.perform(post("/api/v1/tasks")
						.header("Authorization", token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"projectId\":\"" + projectId + "\",\"title\":\"Approval task\"}"))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return JsonPath.read(response, "$.id");
	}

	private String startRun(String token, String taskId) throws Exception {
		String response = mockMvc.perform(post("/api/v1/tasks/{id}/start-run", taskId)
						.header("Authorization", token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"agentKey\":\"custom-shell\"}"))
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

	private void recordChanges(String runId, String filesJson) throws Exception {
		mockMvc.perform(post("/internal/v1/runs/{id}/changes", runId)
						.header("X-Talos-Internal-Token", INTERNAL_TOKEN)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"files\":" + filesJson + "}"))
				.andExpect(status().isNoContent());
	}

	/** Walks a run to WAITING_APPROVAL with a clean (no-pattern-matching) file change. */
	private String runToWaitingApproval(String token, String taskId, String filesJson) throws Exception {
		String runId = startRun(token, taskId);
		transitionInternal(runId, "PREPARING_WORKSPACE");
		transitionInternal(runId, "RUNNING_AGENT");
		transitionInternal(runId, "RUNNING_TESTS");
		transitionInternal(runId, "REVIEWING");
		recordChanges(runId, filesJson);
		transitionInternal(runId, "WAITING_APPROVAL");
		return runId;
	}

	private String pendingApprovalIdForRun(String token, String runId) throws Exception {
		String response = mockMvc.perform(get("/api/v1/approvals").header("Authorization", token)
						.param("runId", runId))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		return JsonPath.read(response, "$.content[0].id");
	}

	@Test
	void waitingApproval_autoCreatesPendingApproval_andMovesTaskToReview() throws Exception {
		String token = bearerToken();
		String projectId = createProject(token);
		String taskId = createTask(token, projectId);
		String runId = runToWaitingApproval(token, taskId,
				"[{\"filePath\":\"README.md\",\"changeType\":\"MODIFIED\",\"additions\":1,\"deletions\":0}]");

		mockMvc.perform(get("/api/v1/approvals").header("Authorization", token).param("runId", runId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content.length()").value(1))
				.andExpect(jsonPath("$.content[0].status").value("PENDING"))
				.andExpect(jsonPath("$.content[0].runId").value(runId));

		mockMvc.perform(get("/api/v1/tasks/{id}", taskId).header("Authorization", token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("REVIEW"));

		mockMvc.perform(get("/api/v1/runs/{id}", runId).header("Authorization", token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.reviewStatus").value("CLEAN"));
	}

	@Test
	void envFileChange_isRiskFlaggedWithMatchedPatternDisplayed() throws Exception {
		String token = bearerToken();
		String projectId = createProject(token);
		String taskId = createTask(token, projectId);
		String runId = runToWaitingApproval(token, taskId,
				"[{\"filePath\":\"backend/.env\",\"changeType\":\"MODIFIED\",\"additions\":2,\"deletions\":0}]");

		mockMvc.perform(get("/api/v1/runs/{id}", runId).header("Authorization", token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.reviewStatus").value("RISK_FLAGGED"));

		String approvalId = pendingApprovalIdForRun(token, runId);
		mockMvc.perform(get("/api/v1/approvals/{id}", approvalId).header("Authorization", token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.changes[0].riskFlagged").value(true))
				.andExpect(jsonPath("$.changes[0].matchedPattern").value(".env*"));
	}

	@Test
	void approve_transitionsRunToApproved_andPublishesApprovalDecided() throws Exception {
		String token = bearerToken();
		String projectId = createProject(token);
		String taskId = createTask(token, projectId);
		String runId = runToWaitingApproval(token, taskId,
				"[{\"filePath\":\"README.md\",\"changeType\":\"MODIFIED\",\"additions\":1,\"deletions\":0}]");
		String approvalId = pendingApprovalIdForRun(token, runId);

		mockMvc.perform(post("/api/v1/approvals/{id}/approve", approvalId)
						.header("Authorization", token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"notes\":\"looks good\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("APPROVED"))
				.andExpect(jsonPath("$.notes").value("looks good"));

		mockMvc.perform(get("/api/v1/runs/{id}", runId).header("Authorization", token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("APPROVED"));
	}

	@Test
	void reject_requiresNotes_transitionsRunToRejected_andTaskReturnsToReady() throws Exception {
		String token = bearerToken();
		String projectId = createProject(token);
		String taskId = createTask(token, projectId);
		String runId = runToWaitingApproval(token, taskId,
				"[{\"filePath\":\"README.md\",\"changeType\":\"MODIFIED\",\"additions\":1,\"deletions\":0}]");
		String approvalId = pendingApprovalIdForRun(token, runId);

		mockMvc.perform(post("/api/v1/approvals/{id}/reject", approvalId)
						.header("Authorization", token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isBadRequest());

		mockMvc.perform(post("/api/v1/approvals/{id}/reject", approvalId)
						.header("Authorization", token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"notes\":\"wrong approach\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("REJECTED"));

		mockMvc.perform(get("/api/v1/tasks/{id}", taskId).header("Authorization", token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("READY"));
	}

	@Test
	void requestChanges_keepsDistinctApprovalOutcome_butDrivesRunToRejected() throws Exception {
		String token = bearerToken();
		String projectId = createProject(token);
		String taskId = createTask(token, projectId);
		String runId = runToWaitingApproval(token, taskId,
				"[{\"filePath\":\"README.md\",\"changeType\":\"MODIFIED\",\"additions\":1,\"deletions\":0}]");
		String approvalId = pendingApprovalIdForRun(token, runId);

		mockMvc.perform(post("/api/v1/approvals/{id}/request-changes", approvalId)
						.header("Authorization", token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"notes\":\"please add tests\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("CHANGES_REQUESTED"));

		mockMvc.perform(get("/api/v1/runs/{id}", runId).header("Authorization", token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("REJECTED"));

		mockMvc.perform(get("/api/v1/tasks/{id}", taskId).header("Authorization", token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("READY"));
	}

	@Test
	void decidingAnAlreadyDecidedApproval_returns409() throws Exception {
		String token = bearerToken();
		String projectId = createProject(token);
		String taskId = createTask(token, projectId);
		String runId = runToWaitingApproval(token, taskId,
				"[{\"filePath\":\"README.md\",\"changeType\":\"MODIFIED\",\"additions\":1,\"deletions\":0}]");
		String approvalId = pendingApprovalIdForRun(token, runId);

		mockMvc.perform(post("/api/v1/approvals/{id}/approve", approvalId)
						.header("Authorization", token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isOk());

		mockMvc.perform(post("/api/v1/approvals/{id}/approve", approvalId)
						.header("Authorization", token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error.code").value("APPROVAL_ALREADY_DECIDED"));
	}
}
