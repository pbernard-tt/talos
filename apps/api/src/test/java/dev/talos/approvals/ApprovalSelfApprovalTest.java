// SPDX-FileCopyrightText: 2026 Vulkan Technologies
// SPDX-License-Identifier: AGPL-3.0-or-later

package dev.talos.approvals;

import com.jayway.jsonpath.JsonPath;
import dev.talos.audit.AuditEventRepository;
import dev.talos.auth.Role;
import dev.talos.auth.User;
import dev.talos.auth.UserRepository;
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
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Section 16 Phase 15 acceptance: "the user who requested a run cannot approve it (OWNER may
 * override; the override is audited)." Only MAINTAINER+ can start a run and REVIEWER+ can approve
 * one, so the realistic self-approval scenario is a MAINTAINER (which implies REVIEWER via the
 * role hierarchy) both requesting and trying to decide their own run.
 */
@Testcontainers
@SpringBootTest(properties = {
		"talos.jwt-secret=test-only-jwt-signing-secret-not-for-production-use-32bytes+",
		"talos.admin-email=admin@test.local",
		"talos.admin-password=test-admin-password",
		"talos.internal-api-token=test-internal-token-not-for-production-use-32bytes+"
})
@AutoConfigureMockMvc
class ApprovalSelfApprovalTest {

	private static final String INTERNAL_TOKEN = "test-internal-token-not-for-production-use-32bytes+";

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

	@Autowired
	private AuditEventRepository auditEventRepository;

	private String loginAs(Role role) throws Exception {
		String email = "rbac-" + UUID.randomUUID() + "@test.local";
		userRepository.save(new User(email, "Test " + role, passwordEncoder.encode("test-password-12345"), role));
		String response = mockMvc.perform(post("/api/v1/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\":\"" + email + "\",\"password\":\"test-password-12345\"}"))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		return "Bearer " + JsonPath.<String>read(response, "$.token");
	}

	private String createProject(String token) throws Exception {
		String response = mockMvc.perform(post("/api/v1/projects")
						.header("Authorization", token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name":"Self Approval Project %s","repoUrl":"git@github.com:org/sa.git","stackType":"spring-boot"}
								""".formatted(UUID.randomUUID())))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return JsonPath.read(response, "$.id");
	}

	private String createTask(String token, String projectId) throws Exception {
		String response = mockMvc.perform(post("/api/v1/tasks")
						.header("Authorization", token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"projectId\":\"" + projectId + "\",\"title\":\"Self approval task\"}"))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return JsonPath.read(response, "$.id");
	}

	private String runToWaitingApproval(String token, String taskId) throws Exception {
		String response = mockMvc.perform(post("/api/v1/tasks/{id}/start-run", taskId)
						.header("Authorization", token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"agentKey\":\"custom-shell\"}"))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		String runId = JsonPath.read(response, "$.id");
		for (String status : new String[] {"PREPARING_WORKSPACE", "RUNNING_AGENT", "RUNNING_TESTS", "REVIEWING"}) {
			mockMvc.perform(post("/internal/v1/runs/{id}/status", runId)
							.header("X-Talos-Internal-Token", INTERNAL_TOKEN)
							.contentType(MediaType.APPLICATION_JSON)
							.content("{\"status\":\"" + status + "\"}"))
					.andExpect(status().isOk());
		}
		mockMvc.perform(post("/internal/v1/runs/{id}/changes", runId)
						.header("X-Talos-Internal-Token", INTERNAL_TOKEN)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"files\":[{\"filePath\":\"README.md\",\"changeType\":\"MODIFIED\",\"additions\":1,\"deletions\":0}]}"))
				.andExpect(status().isNoContent());
		mockMvc.perform(post("/internal/v1/runs/{id}/status", runId)
						.header("X-Talos-Internal-Token", INTERNAL_TOKEN)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"status\":\"WAITING_APPROVAL\"}"))
				.andExpect(status().isOk());
		return runId;
	}

	private String pendingApprovalIdForRun(String token, String runId) throws Exception {
		String response = mockMvc.perform(get("/api/v1/approvals").header("Authorization", token).param("runId", runId))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		return JsonPath.read(response, "$.content[0].id");
	}

	@Test
	void maintainerCannotApproveTheirOwnRequestedRun() throws Exception {
		String maintainerToken = loginAs(Role.MAINTAINER);
		String projectId = createProject(maintainerToken);
		String taskId = createTask(maintainerToken, projectId);
		String runId = runToWaitingApproval(maintainerToken, taskId);
		String approvalId = pendingApprovalIdForRun(maintainerToken, runId);

		mockMvc.perform(post("/api/v1/approvals/{id}/approve", approvalId)
						.header("Authorization", maintainerToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("SELF_APPROVAL_FORBIDDEN"));

		assertThat(auditEventRepository.findAll())
				.anyMatch(e -> "approval.self_approval_rejected".equals(e.getEventType()));
	}

	@Test
	void aDifferentReviewerCanApproveSomeoneElsesRun() throws Exception {
		String maintainerToken = loginAs(Role.MAINTAINER);
		String reviewerToken = loginAs(Role.REVIEWER);
		String projectId = createProject(maintainerToken);
		String taskId = createTask(maintainerToken, projectId);
		String runId = runToWaitingApproval(maintainerToken, taskId);
		String approvalId = pendingApprovalIdForRun(maintainerToken, runId);

		mockMvc.perform(post("/api/v1/approvals/{id}/approve", approvalId)
						.header("Authorization", reviewerToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("APPROVED"));
	}

	@Test
	void ownerCanOverrideSelfApproval_andTheOverrideIsAudited() throws Exception {
		String ownerToken = loginAs(Role.OWNER);
		String projectId = createProject(ownerToken);
		String taskId = createTask(ownerToken, projectId);
		String runId = runToWaitingApproval(ownerToken, taskId);
		String approvalId = pendingApprovalIdForRun(ownerToken, runId);

		mockMvc.perform(post("/api/v1/approvals/{id}/approve", approvalId)
						.header("Authorization", ownerToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("APPROVED"));

		assertThat(auditEventRepository.findAll())
				.anyMatch(e -> "approval.self_approval_override".equals(e.getEventType()));
	}

	@Test
	void viewerCannotApprove_gets403WithAuditRow() throws Exception {
		String maintainerToken = loginAs(Role.MAINTAINER);
		String viewerToken = loginAs(Role.VIEWER);
		String projectId = createProject(maintainerToken);
		String taskId = createTask(maintainerToken, projectId);
		String runId = runToWaitingApproval(maintainerToken, taskId);
		String approvalId = pendingApprovalIdForRun(maintainerToken, runId);

		mockMvc.perform(post("/api/v1/approvals/{id}/approve", approvalId)
						.header("Authorization", viewerToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

		assertThat(auditEventRepository.findAll())
				.anyMatch(e -> "role.access_denied".equals(e.getEventType()));
	}
}
