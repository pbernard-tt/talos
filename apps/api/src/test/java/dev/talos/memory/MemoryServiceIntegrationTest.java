package dev.talos.memory;

import dev.talos.projects.Project;
import dev.talos.projects.ProjectConfig;
import dev.talos.projects.ProjectConfigRepository;
import dev.talos.projects.ProjectRepository;
import dev.talos.runs.AgentRun;
import dev.talos.runs.AgentRunRepository;
import dev.talos.runs.GitChange;
import dev.talos.runs.GitChangeRepository;
import dev.talos.runs.GitChangeType;
import dev.talos.runs.RunStatus;
import dev.talos.tasks.Task;
import dev.talos.tasks.TaskRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(properties = {
		"talos.jwt-secret=test-only-jwt-signing-secret-not-for-production-use-32bytes+",
		"talos.admin-email=admin@test.local",
		"talos.admin-password=test-admin-password"
})
class MemoryServiceIntegrationTest {

	@Container
	@ServiceConnection
	static PostgreSQLContainer postgres = new PostgreSQLContainer(org.testcontainers.utility.DockerImageName
			.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres"));

	@Autowired
	private MemoryService memoryService;
	@Autowired
	private ProjectRepository projectRepository;
	@Autowired
	private ProjectConfigRepository projectConfigRepository;
	@Autowired
	private TaskRepository taskRepository;
	@Autowired
	private AgentRunRepository agentRunRepository;
	@Autowired
	private GitChangeRepository gitChangeRepository;

	@Test
	void ingestedDocsSearchOnlyWithinSameProjectAndRespectBudget() {
		Project alpha = projectRepository.save(new Project("Alpha", "alpha-memory", "git@example:alpha.git", "main", "python"));
		Project beta = projectRepository.save(new Project("Beta", "beta-memory", "git@example:beta.git", "main", "python"));

		memoryService.ingestDocument(alpha.getId(), MemorySourceType.OPERATOR_NOTE, "note-1", "Alpha note",
				"Use AccountService for invoice reconciliation and customer credits.");
		memoryService.ingestDocument(beta.getId(), MemorySourceType.OPERATOR_NOTE, "note-1", "Beta note",
				"Use BillingGateway only for warehouse stock updates.");

		var alphaResults = memoryService.search(alpha.getId(), "invoice AccountService", 5, 2000);
		var betaResults = memoryService.search(beta.getId(), "invoice AccountService", 5, 2000);

		assertThat(alphaResults).isNotEmpty();
		assertThat(alphaResults).allMatch(result -> result.sourceRef().equals("note-1"));
		assertThat(alphaResults).allMatch(result -> result.content().contains("AccountService"));
		assertThat(betaResults).isNotEmpty();
		assertThat(betaResults).allMatch(result -> result.title().equals("Beta note"));

		var budgeted = memoryService.search(alpha.getId(), "invoice AccountService", 5, 12);
		assertThat(budgeted.stream().mapToInt(result -> result.content().length()).sum()).isLessThanOrEqualTo(12);
	}

	@Test
	void completedRunProducesMemoryWhenEnabledAndSkipsWhenDisabled() {
		Project enabled = projectRepository.save(new Project("Enabled", "enabled-memory", "git@example:enabled.git", "main", "python"));
		Project disabled = projectRepository.save(new Project("Disabled", "disabled-memory", "git@example:disabled.git", "main", "python"));
		projectConfigRepository.save(new ProjectConfig(disabled.getId(), "memory:\n  enabled: false\n",
				Map.of("memory", Map.of("enabled", false)), 1));

		Task enabledTask = taskRepository.save(new Task(enabled.getId(), "Remember service", "Use AccountService", null));
		AgentRun enabledRun = agentRunRepository.save(new AgentRun(enabledTask.getId(), enabled.getId(), "custom-shell", "api_key"));
		enabledRun.setDiffPatch("diff --git a/service.py b/service.py\n+AccountService()");
		enabledRun.transitionTo(RunStatus.COMPLETED, null, null);
		agentRunRepository.save(enabledRun);
		gitChangeRepository.save(new GitChange(enabledRun.getId(), "service.py", GitChangeType.MODIFIED, 1, 0, false));

		Task disabledTask = taskRepository.save(new Task(disabled.getId(), "Do not remember", "Disabled memory", null));
		AgentRun disabledRun = agentRunRepository.save(new AgentRun(disabledTask.getId(), disabled.getId(), "custom-shell", "api_key"));
		disabledRun.transitionTo(RunStatus.COMPLETED, null, null);

		memoryService.ingestCompletedRun(enabledRun);
		memoryService.ingestCompletedRun(disabledRun);

		assertThat(memoryService.search(enabled.getId(), "AccountService service", 5, 2000)).isNotEmpty();
		assertThat(memoryService.search(disabled.getId(), "Disabled memory", 5, 2000)).isEmpty();
	}
}
