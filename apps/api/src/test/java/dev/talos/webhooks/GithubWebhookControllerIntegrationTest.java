package dev.talos.webhooks;

import dev.talos.audit.AuditEvent;
import dev.talos.audit.AuditEventRepository;
import dev.talos.integrations.PullRequest;
import dev.talos.integrations.PullRequestRepository;
import dev.talos.integrations.PullRequestStatus;
import dev.talos.projects.Project;
import dev.talos.projects.ProjectRepository;
import dev.talos.runs.AgentRun;
import dev.talos.runs.AgentRunRepository;
import dev.talos.tasks.Task;
import dev.talos.tasks.TaskRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest(properties = {
		"talos.jwt-secret=test-only-jwt-signing-secret-not-for-production-use-32bytes+",
		"talos.admin-email=admin@test.local",
		"talos.admin-password=test-admin-password",
		"talos.github-webhook-secret=test-webhook-secret"
})
@AutoConfigureMockMvc
class GithubWebhookControllerIntegrationTest {

	private static final String SECRET = "test-webhook-secret";

	@Container
	@ServiceConnection
	static PostgreSQLContainer postgres = new PostgreSQLContainer(org.testcontainers.utility.DockerImageName
			.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres"));

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
	private ProjectRepository projectRepository;
	@Autowired
	private TaskRepository taskRepository;
	@Autowired
	private AgentRunRepository agentRunRepository;
	@Autowired
	private PullRequestRepository pullRequestRepository;
	@Autowired
	private AuditEventRepository auditEventRepository;

	private PullRequest openPullRequest(String htmlUrl, int number) {
		Project project = projectRepository.save(new Project("Webhook Project", "webhook-" + UUID.randomUUID(),
				"git@github.com:org/webhook.git", "main", "python"));
		Task task = taskRepository.save(new Task(project.getId(), "Webhook task", null, null));
		AgentRun run = agentRunRepository.save(new AgentRun(task.getId(), project.getId(), "custom-shell", "api_key"));
		return pullRequestRepository.save(new PullRequest(run.getId(), "github", number, htmlUrl));
	}

	private static String sign(String body) throws Exception {
		Mac mac = Mac.getInstance("HmacSHA256");
		mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
		return "sha256=" + HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
	}

	private static String prClosedPayload(String htmlUrl, boolean merged) {
		return """
				{"action":"closed","pull_request":{"number":42,"merged":%s,"html_url":"%s"},
				 "repository":{"full_name":"org/webhook"}}
				""".formatted(merged, htmlUrl);
	}

	@Test
	void mergedPr_movesStatusToMerged_andAudits() throws Exception {
		String url = "https://github.com/org/webhook/pull/" + UUID.randomUUID();
		PullRequest pr = openPullRequest(url, 42);
		String body = prClosedPayload(url, true);

		mockMvc.perform(post("/api/v1/webhooks/github")
						.header("X-Hub-Signature-256", sign(body))
						.header("X-GitHub-Event", "pull_request")
						.contentType("application/json")
						.content(body))
				.andExpect(status().isNoContent());

		assertThat(pullRequestRepository.findById(pr.getId()).orElseThrow().getStatus())
				.isEqualTo(PullRequestStatus.MERGED);
		List<AuditEvent> audits = auditEventRepository.findAll().stream()
				.filter(a -> "pr.status.changed".equals(a.getEventType()) && pr.getId().equals(a.getEntityId()))
				.toList();
		assertThat(audits).hasSize(1);
	}

	@Test
	void closedUnmergedPr_movesStatusToClosed_andReopenRestoresOpen() throws Exception {
		String url = "https://github.com/org/webhook/pull/" + UUID.randomUUID();
		PullRequest pr = openPullRequest(url, 42);

		String closed = prClosedPayload(url, false);
		mockMvc.perform(post("/api/v1/webhooks/github")
						.header("X-Hub-Signature-256", sign(closed))
						.header("X-GitHub-Event", "pull_request")
						.contentType("application/json")
						.content(closed))
				.andExpect(status().isNoContent());
		assertThat(pullRequestRepository.findById(pr.getId()).orElseThrow().getStatus())
				.isEqualTo(PullRequestStatus.CLOSED);

		String reopened = """
				{"action":"reopened","pull_request":{"number":42,"merged":false,"html_url":"%s"}}
				""".formatted(url);
		mockMvc.perform(post("/api/v1/webhooks/github")
						.header("X-Hub-Signature-256", sign(reopened))
						.header("X-GitHub-Event", "pull_request")
						.contentType("application/json")
						.content(reopened))
				.andExpect(status().isNoContent());
		assertThat(pullRequestRepository.findById(pr.getId()).orElseThrow().getStatus())
				.isEqualTo(PullRequestStatus.OPEN);
	}

	@Test
	void invalidSignature_rejected401_andNothingChanges() throws Exception {
		String url = "https://github.com/org/webhook/pull/" + UUID.randomUUID();
		PullRequest pr = openPullRequest(url, 42);
		String body = prClosedPayload(url, true);

		mockMvc.perform(post("/api/v1/webhooks/github")
						.header("X-Hub-Signature-256", "sha256=" + "0".repeat(64))
						.header("X-GitHub-Event", "pull_request")
						.contentType("application/json")
						.content(body))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value("WEBHOOK_SIGNATURE_INVALID"));

		assertThat(pullRequestRepository.findById(pr.getId()).orElseThrow().getStatus())
				.isEqualTo(PullRequestStatus.OPEN);
	}

	@Test
	void missingSignature_rejected401() throws Exception {
		mockMvc.perform(post("/api/v1/webhooks/github")
						.header("X-GitHub-Event", "pull_request")
						.contentType("application/json")
						.content("{}"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value("WEBHOOK_SIGNATURE_INVALID"));
	}

	@Test
	void unknownPrAndOtherEventTypes_areAcknowledgedAndIgnored() throws Exception {
		String unknown = prClosedPayload("https://github.com/org/other/pull/999", true);
		mockMvc.perform(post("/api/v1/webhooks/github")
						.header("X-Hub-Signature-256", sign(unknown))
						.header("X-GitHub-Event", "pull_request")
						.contentType("application/json")
						.content(unknown))
				.andExpect(status().isNoContent());

		String ping = "{\"zen\":\"Keep it logically awesome.\"}";
		mockMvc.perform(post("/api/v1/webhooks/github")
						.header("X-Hub-Signature-256", sign(ping))
						.header("X-GitHub-Event", "ping")
						.contentType("application/json")
						.content(ping))
				.andExpect(status().isNoContent());
	}
}
