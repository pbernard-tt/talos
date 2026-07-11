package dev.talos.notifications;

import dev.talos.approvals.Approval;
import dev.talos.approvals.ApprovalRepository;
import dev.talos.approvals.ApprovalStatus;
import dev.talos.approvals.dto.ApprovalRequestedPayload;
import dev.talos.audit.AuditService;
import dev.talos.audit.AuditEventRepository;
import dev.talos.events.EventPublisher;
import dev.talos.runs.AgentRun;
import dev.talos.runs.AgentRunRepository;
import dev.talos.tasks.Task;
import dev.talos.tasks.TaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;

/**
 * Section 6.2's {@code dev.talos.notifications} module ("approval reminders, log-only in MVP") /
 * Section 8.2's "reminder event after 24 h" on WAITING_APPROVAL. Operator-decided semantics
 * (initial review #4): reminder only -- the approval stays PENDING and the run stays
 * WAITING_APPROVAL indefinitely, per the plan's transition table which gives that state no
 * timeout. The {@code EXPIRED} enum/DDL status therefore remains deliberately unreachable.
 *
 * <p>The reminder is three things at once: a log line (the MVP "notification channel"), an
 * {@code approval.reminder.sent} audit row (which doubles as the sent-once marker -- no schema
 * column for it exists in Section 9.2, so the audit trail is the durable record), and a
 * re-published {@code approval.requested} event so the Section 12 notifiers (chat adapters)
 * nudge the operator again over the schema they already consume.
 */
@Component
public class ApprovalReminderScheduler {

	private static final Logger log = LoggerFactory.getLogger(ApprovalReminderScheduler.class);
	static final String REMINDER_AUDIT_EVENT = "approval.reminder.sent";

	private final ApprovalRepository approvalRepository;
	private final AgentRunRepository agentRunRepository;
	private final TaskRepository taskRepository;
	private final AuditService auditService;
	private final AuditEventRepository auditEventRepository;
	private final EventPublisher eventPublisher;

	public ApprovalReminderScheduler(ApprovalRepository approvalRepository, AgentRunRepository agentRunRepository,
			TaskRepository taskRepository, AuditService auditService, AuditEventRepository auditEventRepository,
			EventPublisher eventPublisher) {
		this.approvalRepository = approvalRepository;
		this.agentRunRepository = agentRunRepository;
		this.taskRepository = taskRepository;
		this.auditService = auditService;
		this.auditEventRepository = auditEventRepository;
		this.eventPublisher = eventPublisher;
	}

	@Scheduled(fixedDelay = 300000)
	@Transactional
	public void remindOverdueApprovals() {
		for (Approval approval : approvalRepository.findByStatusAndExpiresAtBefore(ApprovalStatus.PENDING,
				Instant.now())) {
			if (reminderAlreadySent(approval)) {
				continue;
			}
			sendReminder(approval);
		}
	}

	private boolean reminderAlreadySent(Approval approval) {
		return auditEventRepository.findByEntityTypeAndEntityId("approval", approval.getId()).stream()
				.anyMatch(event -> REMINDER_AUDIT_EVENT.equals(event.getEventType()));
	}

	private void sendReminder(Approval approval) {
		String taskTitle = taskRepository.findById(approval.getTaskId()).map(Task::getTitle).orElse("(unknown task)");
		String reviewStatus = agentRunRepository.findById(approval.getRunId()).map(AgentRun::getReviewStatus)
				.map(Enum::name).orElse("NONE");

		// Log-only notification channel in the MVP (Section 6.2).
		log.warn("Approval {} for run {} (\"{}\") has been PENDING for over 24h -- reminder sent",
				approval.getId(), approval.getRunId(), taskTitle);
		auditService.record(null, REMINDER_AUDIT_EVENT, "approval", approval.getId(),
				Map.of("runId", approval.getRunId().toString(), "taskId", approval.getTaskId().toString()));
		eventPublisher.publish("approval.requested", new ApprovalRequestedPayload(approval.getId(),
				approval.getRunId(), taskTitle, reviewStatus));
	}
}
