// SPDX-FileCopyrightText: 2026 Vulkan Technologies
// SPDX-License-Identifier: AGPL-3.0-or-later

package dev.talos.runs;

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

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Phase 5 acceptance: "reaper fails an expired run in test." Calls RunReaper directly rather than waiting for its 60s @Scheduled trigger. */
@Testcontainers
@SpringBootTest(properties = {
		"talos.jwt-secret=test-only-jwt-signing-secret-not-for-production-use-32bytes+",
		"talos.admin-email=admin@test.local",
		"talos.admin-password=test-admin-password",
		"talos.internal-api-token=test-internal-token-not-for-production-use-32bytes+"
})
@AutoConfigureMockMvc
class RunReaperTest {

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
	private RunReaper runReaper;

	@Autowired
	private AgentRunRepository agentRunRepository;

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
								{"name":"Reaper Project %s","repoUrl":"git@github.com:org/reaper.git","stackType":"spring-boot"}
								""".formatted(UUID.randomUUID())))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return JsonPath.read(response, "$.id");
	}

	private String createTask(String token, String projectId) throws Exception {
		String response = mockMvc.perform(post("/api/v1/tasks")
						.header("Authorization", token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"projectId\":\"" + projectId + "\",\"title\":\"Reaper task\"}"))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return JsonPath.read(response, "$.id");
	}

	/** Bypasses start-run's real 10-minute QUEUED timeout so the test doesn't have to wait for it. */
	private AgentRun saveExpiredRun(UUID taskId, UUID projectId, RunStatus status, Instant timeoutAt) {
		AgentRun run = new AgentRun(taskId, projectId, "custom-shell", "api_key");
		run.transitionTo(status, timeoutAt, null);
		return agentRunRepository.save(run);
	}

	@Test
	void reapExpiredRuns_failsExpiredRun_andMovesTaskToBlocked() throws Exception {
		String token = bearerToken();
		String projectId = createProject(token);
		String taskId = createTask(token, projectId);

		AgentRun run = saveExpiredRun(UUID.fromString(taskId), UUID.fromString(projectId), RunStatus.QUEUED,
				Instant.now().minusSeconds(120));

		runReaper.reapExpiredRuns();

		AgentRun reaped = agentRunRepository.findById(run.getId()).orElseThrow();
		assertThat(reaped.getStatus()).isEqualTo(RunStatus.FAILED);
		assertThat(reaped.getErrorMessage()).isEqualTo("TIMEOUT");
		assertThat(reaped.getCompletedAt()).isNotNull();

		mockMvc.perform(get("/api/v1/tasks/{id}", taskId).header("Authorization", token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("BLOCKED"));
	}

	@Test
	void reapExpiredRuns_leavesNotYetExpiredRunsAlone() throws Exception {
		String token = bearerToken();
		String projectId = createProject(token);
		String taskId = createTask(token, projectId);

		AgentRun run = saveExpiredRun(UUID.fromString(taskId), UUID.fromString(projectId), RunStatus.QUEUED,
				Instant.now().plusSeconds(600));

		runReaper.reapExpiredRuns();

		AgentRun untouched = agentRunRepository.findById(run.getId()).orElseThrow();
		assertThat(untouched.getStatus()).isEqualTo(RunStatus.QUEUED);
	}

	@Test
	void reapExpiredRuns_leavesWaitingApprovalAlone_sinceItHasNoTimeout() throws Exception {
		String token = bearerToken();
		String projectId = createProject(token);
		String taskId = createTask(token, projectId);

		// WAITING_APPROVAL has no configured timeout (Section 8.2: "none, reminder event after 24h"),
		// so transitionTo leaves timeoutAt null regardless of what's requested here.
		AgentRun run = saveExpiredRun(UUID.fromString(taskId), UUID.fromString(projectId),
				RunStatus.WAITING_APPROVAL, null);

		runReaper.reapExpiredRuns();

		AgentRun untouched = agentRunRepository.findById(run.getId()).orElseThrow();
		assertThat(untouched.getStatus()).isEqualTo(RunStatus.WAITING_APPROVAL);
	}
}
