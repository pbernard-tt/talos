package dev.talos.approvals;

import dev.talos.common.UuidV7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "approvals")
public class Approval {

	@Id
	private UUID id = UuidV7.generate();

	@Column(name = "task_id", nullable = false)
	private UUID taskId;

	@Column(name = "run_id", nullable = false)
	private UUID runId;

	@Column(name = "approval_type", nullable = false, length = 30)
	private String approvalType;

	@Column(name = "requested_action", nullable = false, length = 200)
	private String requestedAction;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private ApprovalStatus status = ApprovalStatus.PENDING;

	@Column(name = "requested_by")
	private UUID requestedBy;

	@Column(name = "approved_by")
	private UUID approvedBy;

	@Column(name = "approved_at")
	private Instant approvedAt;

	@Column
	private String notes;

	@Column(name = "expires_at")
	private Instant expiresAt;

	@Generated(event = EventType.INSERT)
	@Column(name = "created_at", nullable = false, insertable = false, updatable = false)
	private Instant createdAt;

	/** Phase 10: which environment a DEPLOY-type approval is for; null for RUN_RESULT approvals. */
	@Column(length = 50)
	private String environment;

	protected Approval() {
		// JPA
	}

	public Approval(UUID taskId, UUID runId, String approvalType, String requestedAction, UUID requestedBy,
			Instant expiresAt) {
		this(taskId, runId, approvalType, requestedAction, requestedBy, expiresAt, null);
	}

	public Approval(UUID taskId, UUID runId, String approvalType, String requestedAction, UUID requestedBy,
			Instant expiresAt, String environment) {
		this.taskId = taskId;
		this.runId = runId;
		this.approvalType = approvalType;
		this.requestedAction = requestedAction;
		this.requestedBy = requestedBy;
		this.expiresAt = expiresAt;
		this.environment = environment;
	}

	public UUID getId() {
		return id;
	}

	public UUID getTaskId() {
		return taskId;
	}

	public UUID getRunId() {
		return runId;
	}

	public String getApprovalType() {
		return approvalType;
	}

	public String getRequestedAction() {
		return requestedAction;
	}

	public ApprovalStatus getStatus() {
		return status;
	}

	public UUID getRequestedBy() {
		return requestedBy;
	}

	public UUID getApprovedBy() {
		return approvedBy;
	}

	public Instant getApprovedAt() {
		return approvedAt;
	}

	public String getNotes() {
		return notes;
	}

	public Instant getExpiresAt() {
		return expiresAt;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public String getEnvironment() {
		return environment;
	}

	/** Section 10.2's approve/reject/request-changes actions (Phase 8): a PENDING approval decided exactly once. */
	public void decide(ApprovalStatus status, UUID approvedBy, String notes) {
		this.status = status;
		this.approvedBy = approvedBy;
		this.approvedAt = Instant.now();
		this.notes = notes;
	}
}
