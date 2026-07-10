package dev.talos.runs;

import dev.talos.approvals.Approval;
import dev.talos.approvals.ApprovalRepository;
import dev.talos.approvals.dto.ApprovalRequestedPayload;
import dev.talos.audit.AuditService;
import dev.talos.common.ApiException;
import dev.talos.events.EventPublisher;
import dev.talos.integrations.PullRequestRepository;
import dev.talos.policy.PolicyScanService;
import dev.talos.projects.Project;
import dev.talos.projects.ProjectConfig;
import dev.talos.projects.ProjectConfigRepository;
import dev.talos.projects.ProjectRepository;
import dev.talos.projects.dto.ProjectSummary;
import dev.talos.runs.dto.DiffResponse;
import dev.talos.runs.dto.GitChangeDto;
import dev.talos.runs.dto.GitChangeResponse;
import dev.talos.runs.dto.InternalChangesRequest;
import dev.talos.runs.dto.InternalLogEntry;
import dev.talos.runs.dto.InternalLogsRequest;
import dev.talos.runs.dto.InternalStepRequest;
import dev.talos.runs.dto.LogEntryResponse;
import dev.talos.runs.dto.PullRequestResponse;
import dev.talos.runs.dto.RunCancelRequestedPayload;
import dev.talos.runs.dto.RunContextResponse;
import dev.talos.runs.dto.RunDetailResponse;
import dev.talos.runs.dto.RunResponse;
import dev.talos.runs.dto.RunStatusChangedPayload;
import dev.talos.runs.dto.StartRunRequest;
import dev.talos.runs.dto.StepResponse;
import dev.talos.runs.dto.TaskRunRequestedPayload;
import dev.talos.tasks.Task;
import dev.talos.tasks.TaskRepository;
import dev.talos.tasks.TaskStatus;
import dev.talos.tasks.dto.TaskSummary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class RunService {

	private final AgentRunRepository agentRunRepository;
	private final AgentRunStepRepository agentRunStepRepository;
	private final AgentRunLogRepository agentRunLogRepository;
	private final GitChangeRepository gitChangeRepository;
	private final TaskRepository taskRepository;
	private final ProjectRepository projectRepository;
	private final ProjectConfigRepository projectConfigRepository;
	private final AuditService auditService;
	private final EventPublisher eventPublisher;
	private final RunEventBroadcaster broadcaster;
	private final PolicyScanService policyScanService;
	private final ApprovalRepository approvalRepository;
	private final PullRequestRepository pullRequestRepository;

	public RunService(AgentRunRepository agentRunRepository, AgentRunStepRepository agentRunStepRepository,
			AgentRunLogRepository agentRunLogRepository, GitChangeRepository gitChangeRepository,
			TaskRepository taskRepository, ProjectRepository projectRepository,
			ProjectConfigRepository projectConfigRepository, AuditService auditService,
			EventPublisher eventPublisher, RunEventBroadcaster broadcaster, PolicyScanService policyScanService,
			ApprovalRepository approvalRepository, PullRequestRepository pullRequestRepository) {
		this.agentRunRepository = agentRunRepository;
		this.agentRunStepRepository = agentRunStepRepository;
		this.agentRunLogRepository = agentRunLogRepository;
		this.gitChangeRepository = gitChangeRepository;
		this.taskRepository = taskRepository;
		this.projectRepository = projectRepository;
		this.projectConfigRepository = projectConfigRepository;
		this.auditService = auditService;
		this.eventPublisher = eventPublisher;
		this.broadcaster = broadcaster;
		this.policyScanService = policyScanService;
		this.approvalRepository = approvalRepository;
		this.pullRequestRepository = pullRequestRepository;
	}

	@Transactional
	public AgentRun startRun(UUID taskId, StartRunRequest request, UUID actorUserId) {
		Task task = taskRepository.findById(taskId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TASK_NOT_FOUND", "Task not found"));

		if (agentRunRepository.existsByTaskIdAndStatusNotIn(taskId, RunTransitionValidator.terminalStatuses())) {
			throw new ApiException(HttpStatus.CONFLICT, "ACTIVE_RUN_EXISTS",
					"Task already has an active (non-terminal) run");
		}

		String agentKey = resolveAgentKey(task.getProjectId(), request == null ? null : request.agentKey());
		String authMode = (request == null || request.authMode() == null || request.authMode().isBlank())
				? "api_key"
				: request.authMode();

		AgentRun run = new AgentRun(taskId, task.getProjectId(), agentKey, authMode);
		run = agentRunRepository.save(run);
		auditService.record(actorUserId, "run.created", "run", run.getId(),
				Map.of("taskId", taskId.toString(), "agentKey", agentKey));

		run = transitionRun(run, RunStatus.QUEUED, null, actorUserId);

		eventPublisher.publish("task.run.requested",
				new TaskRunRequestedPayload(run.getId(), taskId, task.getProjectId(), agentKey, authMode));

		return run;
	}

	/** The one path every run status change flows through: validates, sets timeout_at, audits, applies Section 8.2's task<->run mapping, and publishes run.status.changed. */
	@Transactional
	public AgentRun transitionRun(AgentRun run, RunStatus newStatus, String errorMessage, UUID actorUserId) {
		RunStatus from = run.getStatus();
		if (!RunTransitionValidator.isLegal(from, newStatus)) {
			auditService.record(actorUserId, "run.transition.rejected", "run", run.getId(),
					Map.of("from", from.name(), "to", newStatus.name()));
			throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "ILLEGAL_RUN_TRANSITION",
					"Cannot transition run from %s to %s".formatted(from, newStatus));
		}

		Duration timeout = RunTransitionValidator.timeoutFor(newStatus);
		Instant timeoutAt = timeout != null ? Instant.now().plus(timeout) : null;
		run.transitionTo(newStatus, timeoutAt, errorMessage);
		if (newStatus == RunStatus.WAITING_APPROVAL) {
			run.setReviewStatus(policyScanService.scan(run));
		}
		run = agentRunRepository.save(run);

		auditService.record(actorUserId, "run.status.changed", "run", run.getId(),
				Map.of("from", from.name(), "to", newStatus.name()));

		applyTaskMapping(run, newStatus, actorUserId);

		eventPublisher.publish("run.status.changed",
				new RunStatusChangedPayload(run.getId(), from.name(), newStatus.name()));
		broadcaster.publishStatus(run.getId(), from.name(), newStatus.name());

		if (newStatus == RunStatus.WAITING_APPROVAL) {
			createApproval(run);
		}

		return run;
	}

	/** Section 8.1 step 10: auto-creates the PENDING approval a moment a run reaches WAITING_APPROVAL. */
	private void createApproval(AgentRun run) {
		Task task = taskRepository.findById(run.getTaskId())
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TASK_NOT_FOUND", "Task not found"));
		Approval approval = new Approval(run.getTaskId(), run.getId(), "RUN_RESULT", "Review run results", null,
				Instant.now().plus(Duration.ofHours(24)));
		approval = approvalRepository.save(approval);
		auditService.record(null, "approval.requested", "approval", approval.getId(),
				Map.of("runId", run.getId().toString(), "taskId", run.getTaskId().toString()));
		eventPublisher.publish("approval.requested", new ApprovalRequestedPayload(approval.getId(), run.getId(),
				task.getTitle(), run.getReviewStatus().name()));
	}

	/** Section 8.2's "Task <-> run status mapping." CANCELLED restores READY (see phase report -- no column holds "status before the run"). */
	private void applyTaskMapping(AgentRun run, RunStatus to, UUID actorUserId) {
		TaskStatus newTaskStatus = switch (to) {
			case QUEUED, PREPARING_WORKSPACE, RUNNING_AGENT, RUNNING_TESTS, REVIEWING -> TaskStatus.RUNNING;
			case WAITING_APPROVAL, APPROVED -> TaskStatus.REVIEW;
			case COMPLETED -> TaskStatus.DONE;
			case REJECTED, CANCELLED -> TaskStatus.READY;
			case FAILED -> TaskStatus.BLOCKED;
			case CREATED -> null;
		};
		if (newTaskStatus == null) {
			return;
		}
		Task task = taskRepository.findById(run.getTaskId())
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TASK_NOT_FOUND", "Task not found"));
		task.move(newTaskStatus, task.getBoardPosition());
		taskRepository.save(task);
		auditService.record(actorUserId, "task.status.changed", "task", task.getId(),
				Map.of("status", newTaskStatus.name(), "causeRunId", run.getId().toString()));
	}

	public Page<AgentRun> list(UUID projectId, RunStatus status, Pageable pageable) {
		if (projectId != null && status != null) {
			return agentRunRepository.findByProjectIdAndStatus(projectId, status, pageable);
		}
		if (projectId != null) {
			return agentRunRepository.findByProjectId(projectId, pageable);
		}
		if (status != null) {
			return agentRunRepository.findByStatus(status, pageable);
		}
		return agentRunRepository.findAll(pageable);
	}

	public RunDetailResponse getDetail(UUID id) {
		AgentRun run = getOrThrow(id);
		List<StepResponse> steps = agentRunStepRepository.findByRunId(id).stream().map(StepResponse::from).toList();
		return RunDetailResponse.from(run, steps);
	}

	public Page<LogEntryResponse> getLogs(UUID id, long afterSequence, int size) {
		getOrThrow(id);
		Pageable pageable = PageRequest.of(0, size, Sort.by("sequence").ascending());
		return agentRunLogRepository.findByRunIdAndSequenceGreaterThan(id, afterSequence, pageable)
				.map(LogEntryResponse::from);
	}

	/**
	 * Section 10.4's orchestrator-only endpoint. APPROVED/REJECTED are excluded here on purpose:
	 * Section 8.2 marks WAITING_APPROVAL -> APPROVED/REJECTED "API, human decision" only, so those
	 * two transitions are reachable exclusively through ApprovalService (Phase 8) -- otherwise the
	 * orchestrator's service token alone could push a run past human review.
	 */
	@Transactional
	public AgentRun updateStatus(UUID runId, dev.talos.runs.dto.InternalStatusRequest request) {
		if (request.status() == RunStatus.APPROVED || request.status() == RunStatus.REJECTED) {
			throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "APPROVAL_REQUIRED_FOR_TRANSITION",
					"Run transitions to %s require a human decision via /api/v1/approvals, not the internal endpoint"
							.formatted(request.status()));
		}
		AgentRun run = getOrThrow(runId);
		run.applyPipelineDetails(request.testStatus(), request.workspacePath(), request.branchName(),
				request.prompt(), request.summary(), request.exitCode());
		return transitionRun(run, request.status(), request.errorMessage(), null);
	}

	/**
	 * Section 8.2 cancel edge, API-initiated. Transitions the run to CANCELLED immediately (the
	 * run row is authoritative), then publishes run.cancel.requested so an orchestrator actively
	 * processing this run can kill its process tree. This is a Phase 6 extension of Section 11's
	 * event table -- see packages/contracts/events/run.cancel.requested.json for the rationale.
	 */
	@Transactional
	public AgentRun cancel(UUID runId, UUID actorUserId) {
		AgentRun run = getOrThrow(runId);
		run = transitionRun(run, RunStatus.CANCELLED, null, actorUserId);
		eventPublisher.publish("run.cancel.requested", new RunCancelRequestedPayload(run.getId()));
		return run;
	}

	@Transactional
	public void recordChanges(UUID runId, InternalChangesRequest request) {
		AgentRun run = getOrThrow(runId);
		for (GitChangeDto file : request.files()) {
			gitChangeRepository.save(new GitChange(runId, file.filePath(), file.changeType(), file.additions(),
					file.deletions(), false));
		}
		if (request.diffPatch() != null) {
			run.setDiffPatch(request.diffPatch());
			agentRunRepository.save(run);
		}
		auditService.record(null, "run.changes.recorded", "run", runId,
				Map.of("fileCount", request.files().size(), "diffArtifactRef",
						request.diffArtifactRef() == null ? "" : request.diffArtifactRef()));
	}

	@Transactional
	public AgentRunStep recordStep(UUID runId, InternalStepRequest request) {
		getOrThrow(runId);
		AgentRunStep step;
		if (request.status() == StepStatus.RUNNING) {
			step = agentRunStepRepository.save(new AgentRunStep(runId, request.stepType(), StepStatus.RUNNING));
		} else {
			Optional<AgentRunStep> open = agentRunStepRepository
					.findFirstByRunIdAndStepTypeAndStatusOrderByStartedAtDesc(runId, request.stepType(),
							StepStatus.RUNNING);
			if (open.isPresent()) {
				step = open.get();
				step.complete(request.status(), request.summary());
				step = agentRunStepRepository.save(step);
			} else {
				step = agentRunStepRepository.save(new AgentRunStep(runId, request.stepType(), request.status()));
			}
		}
		broadcaster.publishStep(runId, step.getStepType().name(), step.getStatus().name());
		return step;
	}

	@Transactional
	public void ingestLogs(UUID runId, InternalLogsRequest request) {
		getOrThrow(runId);
		for (InternalLogEntry entry : request.entries()) {
			AgentRunLog log = new AgentRunLog(runId, null, entry.sequence(), entry.stream(), entry.message());
			log = agentRunLogRepository.save(log);
			broadcaster.publishLog(runId, LogEntryResponse.from(log));
		}
	}

	public DiffResponse getDiff(UUID id) {
		AgentRun run = getOrThrow(id);
		List<GitChangeResponse> files = gitChangeRepository.findByRunId(id).stream().map(GitChangeResponse::from)
				.toList();
		return new DiffResponse(files, run.getDiffPatch());
	}

	public PullRequestResponse getPullRequest(UUID id) {
		getOrThrow(id);
		return pullRequestRepository.findByRunId(id).stream().findFirst().map(PullRequestResponse::from)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PULL_REQUEST_NOT_FOUND",
						"No pull request has been opened for run %s yet".formatted(id)));
	}

	public RunContextResponse getContext(UUID runId) {
		AgentRun run = getOrThrow(runId);
		Task task = taskRepository.findById(run.getTaskId())
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TASK_NOT_FOUND", "Task not found"));
		Project project = projectRepository.findById(run.getProjectId())
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PROJECT_NOT_FOUND", "Project not found"));
		Map<String, Object> activeConfig = projectConfigRepository.findByProjectIdAndActiveTrue(project.getId())
				.map(ProjectConfig::getParsedJson)
				.orElse(null);
		return new RunContextResponse(RunResponse.from(run), TaskSummary.from(task), ProjectSummary.from(project),
				activeConfig);
	}

	/** Package-visibility relaxed for ApprovalService (Phase 8), which drives transitionRun on approve/reject. */
	public AgentRun getOrThrow(UUID id) {
		return agentRunRepository.findById(id)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "RUN_NOT_FOUND", "Run not found"));
	}

	private String resolveAgentKey(UUID projectId, String requestedAgentKey) {
		if (requestedAgentKey != null && !requestedAgentKey.isBlank()) {
			return requestedAgentKey;
		}
		ProjectConfig config = projectConfigRepository.findByProjectIdAndActiveTrue(projectId)
				.orElseThrow(() -> new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "NO_ACTIVE_CONFIG",
						"Project has no active talos.yaml to default agentKey from; pass one explicitly"));
		Object agents = config.getParsedJson().get("agents");
		if (agents instanceof Map<?, ?> agentsMap) {
			Object preferred = agentsMap.get("preferred");
			if (preferred instanceof String preferredKey && !preferredKey.isBlank()) {
				return preferredKey;
			}
		}
		return "claude-code";
	}
}
