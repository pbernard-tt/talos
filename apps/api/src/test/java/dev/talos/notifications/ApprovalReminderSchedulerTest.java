package dev.talos.notifications;

import dev.talos.approvals.Approval;
import dev.talos.approvals.ApprovalRepository;
import dev.talos.approvals.ApprovalStatus;
import dev.talos.audit.AuditEventRepository;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Calls the scheduler directly rather than waiting for its 5-minute @Scheduled trigger
 * (the RunReaperTest precedent). Semantics under test are review #4's operator decision:
 * reminder only -- the approval must stay PENDING, and exactly one reminder is ever sent. */
@Testcontainers
@SpringBootTest(properties = {
		"talos.jwt-secret=test-only-jwt-signing-secret-not-for-production-use-32bytes+",
		"talos.admin-email=admin@test.local",
		"talos.admin-password=test-admin-password"
})
class ApprovalReminderSchedulerTest {

	@Container
	@ServiceConnection
	static PostgreSQLContainer postgres = new PostgreSQLContainer(org.testcontainers.utility.DockerImageName
			.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres"));

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
	private ApprovalReminderScheduler scheduler;
	@Autowired
	private ProjectRepository projectRepository;
	@Autowired
	private TaskRepository taskRepository;
	@Autowired
	private AgentRunRepository agentRunRepository;
	@Autowired
	private ApprovalRepository approvalRepository;
	@Autowired
	private AuditEventRepository auditEventRepository;

	private Approval pendingApproval(Instant expiresAt) {
		Project project = projectRepository.save(new Project("Reminder Project", "reminder-" + UUID.randomUUID(),
				"git@github.com:org/reminder.git", "main", "python"));
		Task task = taskRepository.save(new Task(project.getId(), "Reminder task", null, null));
		AgentRun run = agentRunRepository.save(new AgentRun(task.getId(), project.getId(), "custom-shell", "api_key"));
		return approvalRepository.save(
				new Approval(task.getId(), run.getId(), "RUN_RESULT", "Review run results", null, expiresAt));
	}

	private long reminderAuditCount(UUID approvalId) {
		return auditEventRepository.findByEntityTypeAndEntityId("approval", approvalId).stream()
				.filter(e -> ApprovalReminderScheduler.REMINDER_AUDIT_EVENT.equals(e.getEventType()))
				.count();
	}

	@Test
	void overdueApproval_getsExactlyOneReminder_andStaysPending() {
		Approval overdue = pendingApproval(Instant.now().minus(Duration.ofHours(1)));

		scheduler.remindOverdueApprovals();
		scheduler.remindOverdueApprovals();

		assertThat(reminderAuditCount(overdue.getId())).isEqualTo(1);
		assertThat(approvalRepository.findById(overdue.getId()).orElseThrow().getStatus())
				.isEqualTo(ApprovalStatus.PENDING);
	}

	@Test
	void notYetOverdueApproval_getsNoReminder() {
		Approval fresh = pendingApproval(Instant.now().plus(Duration.ofHours(23)));

		scheduler.remindOverdueApprovals();

		assertThat(reminderAuditCount(fresh.getId())).isZero();
	}
}
