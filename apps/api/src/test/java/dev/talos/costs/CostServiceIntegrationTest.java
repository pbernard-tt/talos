// SPDX-FileCopyrightText: 2026 Vulkan Technologies
// SPDX-License-Identifier: AGPL-3.0-or-later

package dev.talos.costs;

import com.jayway.jsonpath.JsonPath;
import dev.talos.costs.dto.MonthlyCostSummary;
import dev.talos.runs.AgentRun;
import dev.talos.runs.AgentRunRepository;
import dev.talos.runs.RunStatus;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Phase 14 acceptance: "after fixture runs on two providers, monthly per-provider totals match
 * the fixtures' known usage exactly"; "a run with no usage metadata degrades gracefully." */
@Testcontainers
@SpringBootTest(properties = {
		"talos.jwt-secret=test-only-jwt-signing-secret-not-for-production-use-32bytes+",
		"talos.admin-email=admin@test.local",
		"talos.admin-password=test-admin-password",
		"talos.internal-api-token=test-internal-token-not-for-production-use-32bytes+"
})
@AutoConfigureMockMvc
class CostServiceIntegrationTest {

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
		registry.add("spring.data.redis.url",
				() -> "redis://" + redis.getHost() + ":" + redis.getMappedPort(6379));
	}

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private AgentRunRepository agentRunRepository;

	@Autowired
	private CostService costService;

	private String bearerToken() throws Exception {
		String response = mockMvc.perform(post("/api/v1/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\":\"admin@test.local\",\"password\":\"test-admin-password\"}"))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		return "Bearer " + JsonPath.<String>read(response, "$.token");
	}

	private UUID createProject(String token) throws Exception {
		String response = mockMvc.perform(post("/api/v1/projects")
						.header("Authorization", token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name":"Cost Project %s","repoUrl":"git@github.com:org/cost.git","stackType":"spring-boot"}
								""".formatted(UUID.randomUUID())))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return UUID.fromString(JsonPath.read(response, "$.id"));
	}

	private UUID createTask(String token, UUID projectId) throws Exception {
		String response = mockMvc.perform(post("/api/v1/tasks")
						.header("Authorization", token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"projectId\":\"" + projectId + "\",\"title\":\"Cost task\"}"))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return UUID.fromString(JsonPath.read(response, "$.id"));
	}

	private AgentRun completedRun(UUID taskId, UUID projectId, String agentKey, String authMode,
			Integer inputTokens, Integer outputTokens, BigDecimal costUsd) {
		AgentRun run = new AgentRun(taskId, projectId, agentKey, authMode);
		run.applyPipelineDetails(null, null, null, null, null, 0, inputTokens, outputTokens, costUsd, "test-model");
		run.transitionTo(RunStatus.COMPLETED, null, null);
		return agentRunRepository.save(run);
	}

	@Test
	void monthlyTotalsMatchKnownFixtureUsageExactlyPerProvider() throws Exception {
		String token = bearerToken();
		UUID projectId = createProject(token);
		UUID taskId = createTask(token, projectId);

		completedRun(taskId, projectId, "claude-code", "api_key", 1000, 200, new BigDecimal("0.0500"));
		completedRun(taskId, projectId, "claude-code", "api_key", 500, 100, new BigDecimal("0.0250"));
		completedRun(taskId, projectId, "codex-cli", "api_key", 2000, 400, null);

		var summary = costService.monthlySummary(projectId);
		String thisMonth = DateTimeFormatter.ofPattern("yyyy-MM").withZone(ZoneOffset.UTC).format(Instant.now());

		MonthlyCostSummary claude = summary.stream()
				.filter(row -> row.agentKey().equals("claude-code") && row.month().equals(thisMonth))
				.findFirst().orElseThrow();
		assertThat(claude.totalCostUsd()).isEqualByComparingTo("0.0750");
		assertThat(claude.totalInputTokens()).isEqualTo(1500);
		assertThat(claude.totalOutputTokens()).isEqualTo(300);
		assertThat(claude.runCount()).isEqualTo(2);

		// codex-cli's usage has no client-reported dollar cost (Phase 14: never fabricate one) --
		// the run still contributes tokens without a pipeline failure or a synthesized price.
		MonthlyCostSummary codex = summary.stream()
				.filter(row -> row.agentKey().equals("codex-cli") && row.month().equals(thisMonth))
				.findFirst().orElseThrow();
		assertThat(codex.totalCostUsd()).isNull();
		assertThat(codex.totalInputTokens()).isEqualTo(2000);
		assertThat(codex.runCount()).isEqualTo(1);
	}

	@Test
	void subscriptionLocalRunsAreCountedSeparatelyAndContributeNoCost() throws Exception {
		String token = bearerToken();
		UUID projectId = createProject(token);
		UUID taskId = createTask(token, projectId);

		// RunService.updateStatus (see RunControllerIntegrationTest for the end-to-end policy test)
		// is responsible for nulling out any adapter-reported cost for a subscription_local run
		// before persistence -- this fixture starts from that already-enforced null cost_usd and
		// checks the aggregation query reports subscriptionRunCount without a fabricated price.
		AgentRun run = completedRun(taskId, projectId, "claude-code", "subscription_local", 800, 150, null);

		var summary = costService.monthlySummary(projectId);
		String thisMonth = DateTimeFormatter.ofPattern("yyyy-MM").withZone(ZoneOffset.UTC).format(Instant.now());
		MonthlyCostSummary row = summary.stream()
				.filter(r -> r.agentKey().equals("claude-code") && r.month().equals(thisMonth))
				.findFirst().orElseThrow();

		assertThat(row.totalCostUsd()).isNull();
		assertThat(row.subscriptionRunCount()).isEqualTo(1);
		assertThat(agentRunRepository.findById(run.getId()).orElseThrow().getCostUsd()).isNull();
	}
}
