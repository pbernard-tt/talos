package dev.talos.recommendations;

import com.jayway.jsonpath.JsonPath;
import dev.talos.recommendations.dto.RecommendationResponse;
import dev.talos.runs.AgentRun;
import dev.talos.runs.AgentRunRepository;
import dev.talos.runs.GitChange;
import dev.talos.runs.GitChangeRepository;
import dev.talos.runs.GitChangeType;
import dev.talos.runs.ReviewStatus;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Phase 14 acceptance: recommendation signals are advisory-only, computed from run history
 * (outcomes, review flags, costs) -- "ignoring a recommendation changes no behavior" is exercised
 * separately by RunService.resolveAgentKey defaulting when no task.assignedAgentKey is set. */
@Testcontainers
@SpringBootTest(properties = {
		"talos.jwt-secret=test-only-jwt-signing-secret-not-for-production-use-32bytes+",
		"talos.admin-email=admin@test.local",
		"talos.admin-password=test-admin-password",
		"talos.internal-api-token=test-internal-token-not-for-production-use-32bytes+"
})
@AutoConfigureMockMvc
class RecommendationServiceIntegrationTest {

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
	private GitChangeRepository gitChangeRepository;

	@Autowired
	private RecommendationService recommendationService;

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
								{"name":"Rec Project %s","repoUrl":"git@github.com:org/rec.git","stackType":"spring-boot"}
								""".formatted(UUID.randomUUID())))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return UUID.fromString(JsonPath.read(response, "$.id"));
	}

	private UUID createTask(String token, UUID projectId) throws Exception {
		String response = mockMvc.perform(post("/api/v1/tasks")
						.header("Authorization", token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"projectId\":\"" + projectId + "\",\"title\":\"Rec task\"}"))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return UUID.fromString(JsonPath.read(response, "$.id"));
	}

	private AgentRun completedRun(UUID taskId, UUID projectId, String agentKey, RunStatus status,
			BigDecimal costUsd, ReviewStatus reviewStatus) {
		AgentRun run = new AgentRun(taskId, projectId, agentKey, "api_key");
		run.applyPipelineDetails(null, null, null, null, null, 0, 100, 20, costUsd, "test-model");
		run.setReviewStatus(reviewStatus);
		run.transitionTo(status, null, null);
		return agentRunRepository.save(run);
	}

	@Test
	void suggestedAgentIsTheHigherSuccessRateAgent_andCheapestCapableIsTheLowerCostOne() throws Exception {
		String token = bearerToken();
		UUID projectId = createProject(token);
		UUID taskId = createTask(token, projectId);

		// claude-code: 2/2 completed, avg cost 0.10 -- best success rate.
		completedRun(taskId, projectId, "claude-code", RunStatus.COMPLETED, new BigDecimal("0.08"), ReviewStatus.CLEAN);
		completedRun(taskId, projectId, "claude-code", RunStatus.COMPLETED, new BigDecimal("0.12"), ReviewStatus.CLEAN);
		// codex-cli: 1/2 completed (50% success, still "capable"), avg cost 0.02 -- cheaper.
		completedRun(taskId, projectId, "codex-cli", RunStatus.COMPLETED, new BigDecimal("0.02"), ReviewStatus.CLEAN);
		completedRun(taskId, projectId, "codex-cli", RunStatus.FAILED, new BigDecimal("0.02"), ReviewStatus.CLEAN);

		RecommendationResponse response = recommendationService.getRecommendations(projectId);

		assertThat(response.suggestedAgentKey()).isEqualTo("claude-code");
		assertThat(response.cheapestCapableAgentKey()).isEqualTo("codex-cli");
		assertThat(response.agentStats()).hasSize(2);
	}

	@Test
	void riskFlagsSurfaceFileAreasWithAtLeastTwoRiskFlaggedRuns_andIgnoreASingleOccurrence() throws Exception {
		String token = bearerToken();
		UUID projectId = createProject(token);
		UUID taskId = createTask(token, projectId);

		AgentRun run1 = completedRun(taskId, projectId, "claude-code", RunStatus.COMPLETED, new BigDecimal("0.05"),
				ReviewStatus.RISK_FLAGGED);
		AgentRun run2 = completedRun(taskId, projectId, "claude-code", RunStatus.COMPLETED, new BigDecimal("0.05"),
				ReviewStatus.RISK_FLAGGED);
		AgentRun run3 = completedRun(taskId, projectId, "claude-code", RunStatus.COMPLETED, new BigDecimal("0.05"),
				ReviewStatus.CLEAN);
		gitChangeRepository.save(new GitChange(run1.getId(), "infra/secrets.yaml", GitChangeType.MODIFIED, 3, 1, true));
		gitChangeRepository.save(new GitChange(run2.getId(), "infra/deploy.yaml", GitChangeType.MODIFIED, 5, 0, true));
		gitChangeRepository.save(new GitChange(run3.getId(), "docs/readme.md", GitChangeType.MODIFIED, 2, 0, true));

		RecommendationResponse response = recommendationService.getRecommendations(projectId);

		assertThat(response.riskFlags()).hasSize(1);
		assertThat(response.riskFlags().get(0).fileArea()).isEqualTo("infra");
		assertThat(response.riskFlags().get(0).riskFlaggedRunCount()).isEqualTo(2);
	}

	@Test
	void recommendationsForAProjectWithNoRunHistoryDegradeGracefully() throws Exception {
		String token = bearerToken();
		UUID projectId = createProject(token);

		RecommendationResponse response = recommendationService.getRecommendations(projectId);

		assertThat(response.suggestedAgentKey()).isNull();
		assertThat(response.cheapestCapableAgentKey()).isNull();
		assertThat(response.agentStats()).isEmpty();
		assertThat(response.riskFlags()).isEmpty();
	}
}
