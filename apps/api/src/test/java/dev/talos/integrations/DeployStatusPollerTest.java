package dev.talos.integrations;

import com.jayway.jsonpath.JsonPath;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Direct-call-in-test style, mirroring dev.talos.runs.RunReaperTest -- doesn't wait for the 30s @Scheduled trigger. */
@Testcontainers
@SpringBootTest(properties = {
		"talos.jwt-secret=test-only-jwt-signing-secret-not-for-production-use-32bytes+",
		"talos.admin-email=admin@test.local",
		"talos.admin-password=test-admin-password",
		"talos.internal-api-token=test-internal-token-not-for-production-use-32bytes+",
		"talos.secrets-key=ZGV2LW9ubHktMzItYnl0ZS1wbGFjZWhvbGRlci1rZXk="
})
@AutoConfigureMockMvc
class DeployStatusPollerTest {

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

	@Autowired
	private DeployStatusPoller poller;

	@Autowired
	private ProjectEnvironmentRepository projectEnvironmentRepository;

	@Autowired
	private AgentRunRepository agentRunRepository;

	@MockitoBean
	private DeployProvider deployProvider;

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

	private String createProject(String token) throws Exception {
		String response = mockMvc.perform(post("/api/v1/projects")
						.header("Authorization", token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name":"Deploy Poller Project %s","repoUrl":"git@github.com:org/poller.git","stackType":"spring-boot"}
								""".formatted(UUID.randomUUID())))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return JsonPath.read(response, "$.id");
	}

	private String createTask(String token, String projectId) throws Exception {
		String response = mockMvc.perform(post("/api/v1/tasks")
						.header("Authorization", token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"projectId\":\"" + projectId + "\",\"title\":\"Deploy poller task\"}"))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return JsonPath.read(response, "$.id");
	}

	private UUID saveCompletedRun(UUID taskId, UUID projectId) {
		AgentRun run = new AgentRun(taskId, projectId, "custom-shell", "api_key");
		run.transitionTo(RunStatus.COMPLETED, null, null);
		return agentRunRepository.save(run).getId();
	}

	private ProjectEnvironment saveRunningEnvironment(UUID projectId, UUID runId) {
		ProjectEnvironment env = new ProjectEnvironment(projectId, "production", "dokploy", "app-123", true);
		env.markTriggered(runId);
		return projectEnvironmentRepository.save(env);
	}

	@Test
	void pollInFlightDeploys_terminalSucceeded_updatesStatusAndDeployedAt() throws Exception {
		String token = bearerToken();
		configureDokployIntegration(token);
		UUID projectId = UUID.fromString(createProject(token));
		UUID taskId = UUID.fromString(createTask(token, projectId.toString()));
		when(deployProvider.pollLatestStatus("https://dokploy.test", "dokploy-test-key", "app-123"))
				.thenReturn(DeployProvider.DeployPollResult.succeeded());
		ProjectEnvironment env = saveRunningEnvironment(projectId, saveCompletedRun(taskId, projectId));

		poller.pollInFlightDeploys();

		ProjectEnvironment updated = projectEnvironmentRepository.findById(env.getId()).orElseThrow();
		assertThat(updated.getLastDeployStatus()).isEqualTo(DeployStatus.SUCCEEDED);
		assertThat(updated.getLastDeployedAt()).isNotNull();
	}

	@Test
	void pollInFlightDeploys_terminalFailed_updatesStatus() throws Exception {
		String token = bearerToken();
		configureDokployIntegration(token);
		UUID projectId = UUID.fromString(createProject(token));
		UUID taskId = UUID.fromString(createTask(token, projectId.toString()));
		when(deployProvider.pollLatestStatus("https://dokploy.test", "dokploy-test-key", "app-123"))
				.thenReturn(DeployProvider.DeployPollResult.failed("build failed"));
		ProjectEnvironment env = saveRunningEnvironment(projectId, saveCompletedRun(taskId, projectId));

		poller.pollInFlightDeploys();

		ProjectEnvironment updated = projectEnvironmentRepository.findById(env.getId()).orElseThrow();
		assertThat(updated.getLastDeployStatus()).isEqualTo(DeployStatus.FAILED);
	}

	@Test
	void pollInFlightDeploys_stillRunning_leavesEnvironmentUntouched() throws Exception {
		String token = bearerToken();
		configureDokployIntegration(token);
		UUID projectId = UUID.fromString(createProject(token));
		UUID taskId = UUID.fromString(createTask(token, projectId.toString()));
		when(deployProvider.pollLatestStatus("https://dokploy.test", "dokploy-test-key", "app-123"))
				.thenReturn(DeployProvider.DeployPollResult.inProgress());
		ProjectEnvironment env = saveRunningEnvironment(projectId, saveCompletedRun(taskId, projectId));

		poller.pollInFlightDeploys();

		ProjectEnvironment untouched = projectEnvironmentRepository.findById(env.getId()).orElseThrow();
		assertThat(untouched.getLastDeployStatus()).isEqualTo(DeployStatus.RUNNING);
		assertThat(untouched.getLastDeployedAt()).isNull();
	}
}
