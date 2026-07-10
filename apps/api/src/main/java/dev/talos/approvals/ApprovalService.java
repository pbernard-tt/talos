package dev.talos.approvals;

import dev.talos.approvals.dto.ApprovalDecidedPayload;
import dev.talos.approvals.dto.ApprovalDetailResponse;
import dev.talos.approvals.dto.ApprovalResponse;
import dev.talos.audit.AuditService;
import dev.talos.common.ApiException;
import dev.talos.events.EventPublisher;
import dev.talos.integrations.DeployService;
import dev.talos.integrations.ProjectEnvironment;
import dev.talos.integrations.ProjectEnvironmentRepository;
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

import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Section 10.2's approval actions: the sole path to WAITING_APPROVAL -> APPROVED/REJECTED (Section 8.2), and (Phase 10) to triggering a gated deploy. */
@Service
public class ApprovalService {

	private static final String APPROVAL_TYPE_DEPLOY = "DEPLOY";

	private final ApprovalRepository approvalRepository;
	private final GitChangeRepository gitChangeRepository;
	private final RunService runService;
	private final ProjectEnvironmentRepository projectEnvironmentRepository;
	private final DeployService deployService;
	private final AuditService auditService;
	private final EventPublisher eventPublisher;

	public ApprovalService(ApprovalRepository approvalRepository, GitChangeRepository gitChangeRepository,
			RunService runService, ProjectEnvironmentRepository projectEnvironmentRepository,
			DeployService deployService, AuditService auditService, EventPublisher eventPublisher) {
		this.approvalRepository = approvalRepository;
		this.gitChangeRepository = gitChangeRepository;
		this.runService = runService;
		this.projectEnvironmentRepository = projectEnvironmentRepository;
		this.deployService = deployService;
		this.auditService = auditService;
		this.eventPublisher = eventPublisher;
	}

	public Page<Approval> list(ApprovalStatus status, UUID runId, String approvalType, Pageable pageable) {
		return approvalRepository.search(status, runId, approvalType, pageable);
	}

	public ApprovalDetailResponse getDetail(UUID id) {
		Approval approval = getOrThrow(id);
		AgentRun run = runService.getOrThrow(approval.getRunId());
		var changes = gitChangeRepository.findByRunId(run.getId()).stream().map(GitChangeResponse::from).toList();
		return new ApprovalDetailResponse(ApprovalResponse.from(approval), RunResponse.from(run), changes);
	}

	public Approval approve(UUID id, UUID actorUserId, String notes) {
		return decide(id, actorUserId, notes, ApprovalStatus.APPROVED);
	}

	public Approval reject(UUID id, UUID actorUserId, String notes) {
		return decide(id, actorUserId, notes, ApprovalStatus.REJECTED);
	}

	/**
	 * Section 8.2 has no run status for "changes requested" -- only APPROVED/REJECTED exist as
	 * outbound edges from WAITING_APPROVAL. The Approval row keeps the distinct CHANGES_REQUESTED
	 * outcome and notes; the run is driven through the existing REJECTED edge so the task returns
	 * to READY for rework (there's no other legal edge back to a re-workable state).
	 */
	public Approval requestChanges(UUID id, UUID actorUserId, String notes) {
		return decide(id, actorUserId, notes, ApprovalStatus.CHANGES_REQUESTED);
	}

	/**
	 * Deliberately NOT @Transactional: the DEPLOY branch calls deployService.triggerNow, an
	 * external HTTP call to Dokploy. Wrapping this whole method would let a provider failure roll
	 * back the approval-decision save made a few lines above it (Spring rolls back the whole
	 * transaction on any unchecked exception) -- discovered live, see DeployControllerIntegrationTest's
	 * deploy_providerTriggerThrows_marksEnvironmentFailed_notSilentlyRolledBack. Each write below
	 * (the approval save, and RunService.transitionRun for the RUN_RESULT branch) already commits
	 * in its own transaction via Spring Data/RunService's own @Transactional methods.
	 */
	private Approval decide(UUID id, UUID actorUserId, String notes, ApprovalStatus approvalStatus) {
		Approval approval = getOrThrow(id);
		requirePending(approval);
		approval.decide(approvalStatus, actorUserId, notes);
		approval = approvalRepository.save(approval);

		if (APPROVAL_TYPE_DEPLOY.equals(approval.getApprovalType())) {
			decideDeploy(approval, approvalStatus, actorUserId);
		} else {
			decideRunResult(approval, approvalStatus, actorUserId);
		}

		auditService.record(actorUserId, "approval." + approvalStatus.name().toLowerCase(Locale.ROOT), "approval",
				approval.getId(), Map.of("runId", approval.getRunId().toString(), "notes",
						notes == null ? "" : notes));
		eventPublisher.publish("approval.decided",
				new ApprovalDecidedPayload(approval.getId(), approval.getRunId(), approvalStatus.name(), actorUserId));

		return approval;
	}

	/** The original Phase 8/9 behavior: drives the run through APPROVED or the shared REJECTED edge. */
	private void decideRunResult(Approval approval, ApprovalStatus approvalStatus, UUID actorUserId) {
		RunStatus runStatus = approvalStatus == ApprovalStatus.APPROVED ? RunStatus.APPROVED : RunStatus.REJECTED;
		AgentRun run = runService.getOrThrow(approval.getRunId());
		runService.transitionRun(run, runStatus, null, actorUserId);
	}

	/** Phase 10: approving a DEPLOY approval triggers Dokploy; reject/request-changes just records the decision. */
	private void decideDeploy(Approval approval, ApprovalStatus approvalStatus, UUID actorUserId) {
		if (approvalStatus != ApprovalStatus.APPROVED) {
			return;
		}
		AgentRun run = runService.getOrThrow(approval.getRunId());
		ProjectEnvironment environment = projectEnvironmentRepository
				.findByProjectIdAndEnvironment(run.getProjectId(), approval.getEnvironment())
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PROJECT_ENVIRONMENT_NOT_FOUND",
						"No project_environments row for %s/%s".formatted(run.getProjectId(), approval.getEnvironment())));
		deployService.triggerNow(environment, run, actorUserId);
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
