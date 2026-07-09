package dev.talos.runs;

import dev.talos.audit.AuditService;
import dev.talos.common.ApiException;
import dev.talos.events.EventPublisher;
import dev.talos.projects.Project;
import dev.talos.projects.ProjectConfig;
import dev.talos.projects.ProjectConfigRepository;
import dev.talos.projects.ProjectRepository;
import dev.talos.projects.dto.ProjectSummary;
import dev.talos.runs.dto.InternalLogEntry;
import dev.talos.runs.dto.InternalLogsRequest;
import dev.talos.runs.dto.InternalStepRequest;
import dev.talos.runs.dto.LogEntryResponse;
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
	private final TaskRepository taskRepository;
	private final ProjectRepository projectRepository;
	private final ProjectConfigRepository projectConfigRepository;
	private final AuditService auditService;
	private final EventPublisher eventPublisher;
	private final RunEventBroadcaster broadcaster;

	public RunService(AgentRunRepository agentRunRepository, AgentRunStepRepository agentRunStepRepository,
			AgentRunLogRepository agentRunLogRepository, TaskRepository taskRepository,
			ProjectRepository projectRepository, ProjectConfigRepository projectConfigRepository,
			AuditService auditService, EventPublisher eventPublisher, RunEventBroadcaster broadcaster) {
		this.agentRunRepository = agentRunRepository;
		this.agentRunStepRepository = agentRunStepRepository;
		this.agentRunLogRepository = agentRunLogRepository;
		this.taskRepository = taskRepository;
		this.projectRepository = projectRepository;
		this.projectConfigRepository = projectConfigRepository;
		this.auditService = auditService;
		this.eventPublisher = eventPublisher;
		this.broadcaster = broadcaster;
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
		run = agentRunRepository.save(run);

		auditService.record(actorUserId, "run.status.changed", "run", run.getId(),
				Map.of("from", from.name(), "to", newStatus.name()));

		applyTaskMapping(run, newStatus, actorUserId);

		eventPublisher.publish("run.status.changed",
				new RunStatusChangedPayload(run.getId(), from.name(), newStatus.name()));
		broadcaster.publishStatus(run.getId(), from.name(), newStatus.name());

		return run;
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

	@Transactional
	public AgentRun updateStatus(UUID runId, RunStatus status, String errorMessage) {
		AgentRun run = getOrThrow(runId);
		return transitionRun(run, status, errorMessage, null);
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

	AgentRun getOrThrow(UUID id) {
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
