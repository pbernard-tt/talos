package dev.talos;

import dev.talos.approvals.Approval;
import dev.talos.approvals.ApprovalRepository;
import dev.talos.integrations.Integration;
import dev.talos.integrations.IntegrationRepository;
import dev.talos.integrations.PullRequest;
import dev.talos.integrations.PullRequestRepository;
import dev.talos.projects.Project;
import dev.talos.projects.ProjectConfig;
import dev.talos.projects.ProjectConfigRepository;
import dev.talos.projects.ProjectRepository;
import dev.talos.runs.AgentRun;
import dev.talos.runs.AgentRunLog;
import dev.talos.runs.AgentRunLogRepository;
import dev.talos.runs.AgentRunRepository;
import dev.talos.runs.AgentRunStep;
import dev.talos.runs.AgentRunStepRepository;
import dev.talos.runs.GitChange;
import dev.talos.runs.GitChangeRepository;
import dev.talos.runs.GitChangeType;
import dev.talos.runs.LogStream;
import dev.talos.runs.StepStatus;
import dev.talos.runs.StepType;
import dev.talos.secrets.IntegrationCredential;
import dev.talos.secrets.IntegrationCredentialRepository;
import dev.talos.secrets.SecretValue;
import dev.talos.secrets.SecretValueRepository;
import dev.talos.tasks.Task;
import dev.talos.tasks.TaskRepository;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Round-trip test per entity in V002__core_schema.sql, plus Flyway migrate-twice idempotency.
 * Builds the real FK chain (project -> task -> run -> step/log/change/approval/pr) rather than
 * inserting orphan rows, so this also exercises the schema's referential integrity end to end.
 */
@Testcontainers
@SpringBootTest(properties = {
		"talos.jwt-secret=test-only-jwt-signing-secret-not-for-production-use-32bytes+",
		"talos.admin-email=admin@test.local",
		"talos.admin-password=test-admin-password"
})
class CoreSchemaRepositoryTest {

	@Container
	@ServiceConnection
	static PostgreSQLContainer postgres = new PostgreSQLContainer(org.testcontainers.utility.DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres"));

	@Autowired
	private Flyway flyway;

	@Autowired
	private ProjectRepository projectRepository;

	@Autowired
	private ProjectConfigRepository projectConfigRepository;

	@Autowired
	private TaskRepository taskRepository;

	@Autowired
	private AgentRunRepository agentRunRepository;

	@Autowired
	private AgentRunStepRepository agentRunStepRepository;

	@Autowired
	private AgentRunLogRepository agentRunLogRepository;

	@Autowired
	private GitChangeRepository gitChangeRepository;

	@Autowired
	private ApprovalRepository approvalRepository;

	@Autowired
	private IntegrationRepository integrationRepository;

	@Autowired
	private PullRequestRepository pullRequestRepository;

	@Autowired
	private SecretValueRepository secretValueRepository;

	@Autowired
	private IntegrationCredentialRepository integrationCredentialRepository;

	@Test
	void flywayMigrateTwice_isIdempotent() {
		MigrateResult result = flyway.migrate();
		assertThat(result.migrationsExecuted).isZero();
	}

	@Test
	void projectAndProjectConfig_roundTrip() {
		Project project = projectRepository.save(
				new Project("Example Backend", "example-backend-" + UUID.randomUUID(),
						"git@github.com:org/example-backend.git", "main", "spring-boot"));

		Project loaded = projectRepository.findById(project.getId()).orElseThrow();
		assertThat(loaded.getName()).isEqualTo("Example Backend");
		assertThat(loaded.getStatus().name()).isEqualTo("ACTIVE");
		assertThat(loaded.getCreatedAt()).isNotNull();

		ProjectConfig config = projectConfigRepository.save(
				new ProjectConfig(project.getId(), "project:\n  name: example-backend\n",
						Map.of("project", Map.of("name", "example-backend")), 1));

		ProjectConfig loadedConfig = projectConfigRepository.findById(config.getId()).orElseThrow();
		assertThat(loadedConfig.getVersion()).isEqualTo(1);
		assertThat(loadedConfig.isActive()).isTrue();
		assertThat(loadedConfig.getParsedJson()).containsKey("project");
	}

	@Test
	void fullRunChain_roundTrip() {
		Project project = projectRepository.save(
				new Project("Chain Project", "chain-project-" + UUID.randomUUID(),
						"git@github.com:org/chain.git", "main", "spring-boot"));

		Task task = taskRepository.save(
				new Task(project.getId(), "Add /hello endpoint", "Return a greeting", null));
		assertThat(taskRepository.findById(task.getId()).orElseThrow().getStatus().name()).isEqualTo("BACKLOG");

		AgentRun run = agentRunRepository.save(
				new AgentRun(task.getId(), project.getId(), "custom-shell", "api_key"));
		assertThat(agentRunRepository.findById(run.getId()).orElseThrow().getStatus().name()).isEqualTo("CREATED");

		AgentRunStep step = agentRunStepRepository.save(
				new AgentRunStep(run.getId(), StepType.WORKSPACE, StepStatus.COMPLETED));
		assertThat(agentRunStepRepository.findById(step.getId()).orElseThrow().getStepType())
				.isEqualTo(StepType.WORKSPACE);

		AgentRunLog log = agentRunLogRepository.save(
				new AgentRunLog(run.getId(), step.getId(), 1L, LogStream.STDOUT, "cloning repository"));
		assertThat(agentRunLogRepository.findById(log.getId()).orElseThrow().getMessage())
				.isEqualTo("cloning repository");

		GitChange change = gitChangeRepository.save(
				new GitChange(run.getId(), "src/main/java/Hello.java", GitChangeType.ADDED, 12, 0, false));
		assertThat(gitChangeRepository.findById(change.getId()).orElseThrow().getChangeType())
				.isEqualTo(GitChangeType.ADDED);

		Approval approval = approvalRepository.save(
				new Approval(task.getId(), run.getId(), "RUN_RESULT", "Push branch and open PR", null,
						Instant.now().plusSeconds(86400)));
		assertThat(approvalRepository.findById(approval.getId()).orElseThrow().getStatus().name())
				.isEqualTo("PENDING");

		PullRequest pr = pullRequestRepository.save(
				new PullRequest(run.getId(), "github", 42, "https://github.com/org/chain/pull/42"));
		assertThat(pullRequestRepository.findById(pr.getId()).orElseThrow().getStatus().name()).isEqualTo("OPEN");
	}

	@Test
	void integrationAndCredential_roundTrip() {
		Integration integration = integrationRepository.save(
				new Integration("github", "org-github", Map.of("owner", "org")));
		assertThat(integrationRepository.findById(integration.getId()).orElseThrow().isEnabled()).isTrue();

		SecretValue secret = secretValueRepository.save(
				new SecretValue("encrypted".getBytes(StandardCharsets.UTF_8), "nonce".getBytes(StandardCharsets.UTF_8)));
		assertThat(secretValueRepository.findById(secret.getId()).orElseThrow().getEncryptedValue())
				.isEqualTo("encrypted".getBytes(StandardCharsets.UTF_8));

		IntegrationCredential credential = integrationCredentialRepository.save(
				new IntegrationCredential(integration.getId(), secret.getId(), "pat", null));
		assertThat(integrationCredentialRepository.findById(credential.getId()).orElseThrow().getAuthMode())
				.isEqualTo("pat");
	}
}
