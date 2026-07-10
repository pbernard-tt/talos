package dev.talos.approvals;

import dev.talos.approvals.dto.ApprovalDecidedPayload;
import dev.talos.approvals.dto.ApprovalDetailResponse;
import dev.talos.approvals.dto.ApprovalResponse;
import dev.talos.audit.AuditService;
import dev.talos.common.ApiException;
import dev.talos.events.EventPublisher;
import dev.talos.runs.AgentRun;
import dev.talos.runs.GitChangeRepository;
import dev.talos.runs.RunService;
import dev.talos.runs.RunStatus;
import dev.talos.runs.dto.GitChangeResponse;
import dev.talos.runs.dto.RunResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Section 10.2's approval actions: the sole path to WAITING_APPROVAL -> APPROVED/REJECTED (Section 8.2). */
@Service
public class ApprovalService {

	private final ApprovalRepository approvalRepository;
	private final GitChangeRepository gitChangeRepository;
	private final RunService runService;
	private final AuditService auditService;
	private final EventPublisher eventPublisher;

	public ApprovalService(ApprovalRepository approvalRepository, GitChangeRepository gitChangeRepository,
			RunService runService, AuditService auditService, EventPublisher eventPublisher) {
		this.approvalRepository = approvalRepository;
		this.gitChangeRepository = gitChangeRepository;
		this.runService = runService;
		this.auditService = auditService;
		this.eventPublisher = eventPublisher;
	}

	public Page<Approval> list(ApprovalStatus status, UUID runId, Pageable pageable) {
		if (status != null && runId != null) {
			return approvalRepository.findByStatusAndRunId(status, runId, pageable);
		}
		if (status != null) {
			return approvalRepository.findByStatus(status, pageable);
		}
		if (runId != null) {
			return approvalRepository.findByRunId(runId, pageable);
		}
		return approvalRepository.findAll(pageable);
	}

	public ApprovalDetailResponse getDetail(UUID id) {
		Approval approval = getOrThrow(id);
		AgentRun run = runService.getOrThrow(approval.getRunId());
		var changes = gitChangeRepository.findByRunId(run.getId()).stream().map(GitChangeResponse::from).toList();
		return new ApprovalDetailResponse(ApprovalResponse.from(approval), RunResponse.from(run), changes);
	}

	@Transactional
	public Approval approve(UUID id, UUID actorUserId, String notes) {
		return decide(id, actorUserId, notes, ApprovalStatus.APPROVED, RunStatus.APPROVED);
	}

	@Transactional
	public Approval reject(UUID id, UUID actorUserId, String notes) {
		return decide(id, actorUserId, notes, ApprovalStatus.REJECTED, RunStatus.REJECTED);
	}

	/**
	 * Section 8.2 has no run status for "changes requested" -- only APPROVED/REJECTED exist as
	 * outbound edges from WAITING_APPROVAL. The Approval row keeps the distinct CHANGES_REQUESTED
	 * outcome and notes; the run is driven through the existing REJECTED edge so the task returns
	 * to READY for rework (there's no other legal edge back to a re-workable state).
	 */
	@Transactional
	public Approval requestChanges(UUID id, UUID actorUserId, String notes) {
		return decide(id, actorUserId, notes, ApprovalStatus.CHANGES_REQUESTED, RunStatus.REJECTED);
	}

	private Approval decide(UUID id, UUID actorUserId, String notes, ApprovalStatus approvalStatus,
			RunStatus runStatus) {
		Approval approval = getOrThrow(id);
		requirePending(approval);
		approval.decide(approvalStatus, actorUserId, notes);
		approval = approvalRepository.save(approval);

		AgentRun run = runService.getOrThrow(approval.getRunId());
		runService.transitionRun(run, runStatus, null, actorUserId);

		auditService.record(actorUserId, "approval." + approvalStatus.name().toLowerCase(Locale.ROOT), "approval",
				approval.getId(), Map.of("runId", approval.getRunId().toString(), "notes",
						notes == null ? "" : notes));
		eventPublisher.publish("approval.decided",
				new ApprovalDecidedPayload(approval.getId(), approval.getRunId(), approvalStatus.name(), actorUserId));

		return approval;
	}

	private void requirePending(Approval approval) {
		if (approval.getStatus() != ApprovalStatus.PENDING) {
			throw new ApiException(HttpStatus.CONFLICT, "APPROVAL_ALREADY_DECIDED",
					"Approval %s is already %s".formatted(approval.getId(), approval.getStatus()));
		}
	}

	private Approval getOrThrow(UUID id) {
		return approvalRepository.findById(id)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "APPROVAL_NOT_FOUND", "Approval not found"));
	}
}
