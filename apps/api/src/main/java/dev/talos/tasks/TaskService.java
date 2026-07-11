package dev.talos.tasks;

import dev.talos.audit.AuditService;
import dev.talos.common.ApiException;
import dev.talos.projects.ProjectRepository;
import dev.talos.projects.dto.RunSummary;
import dev.talos.runs.AgentRunRepository;
import dev.talos.tasks.dto.CreateTaskRequest;
import dev.talos.tasks.dto.MoveTaskRequest;
import dev.talos.tasks.dto.PatchTaskRequest;
import dev.talos.tasks.dto.TaskDetailResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class TaskService {

	/** Section 16 Phase 12 Track B: provenance values a client may set explicitly; anything else falls back to DASHBOARD. */
	private static final Set<String> KNOWN_SOURCES = Set.of("DASHBOARD", "WEBHOOK", "TELEGRAM", "WHATSAPP");

	private final TaskRepository taskRepository;
	private final ProjectRepository projectRepository;
	private final AgentRunRepository agentRunRepository;
	private final AuditService auditService;

	public TaskService(TaskRepository taskRepository, ProjectRepository projectRepository,
			AgentRunRepository agentRunRepository, AuditService auditService) {
		this.taskRepository = taskRepository;
		this.projectRepository = projectRepository;
		this.agentRunRepository = agentRunRepository;
		this.auditService = auditService;
	}

	@Transactional
	public Task create(CreateTaskRequest request, UUID actorUserId) {
		if (!projectRepository.existsById(request.projectId())) {
			throw new ApiException(HttpStatus.NOT_FOUND, "PROJECT_NOT_FOUND", "Project not found");
		}
		String source = request.source() != null && KNOWN_SOURCES.contains(request.source())
				? request.source()
				: "DASHBOARD";
		Task task = new Task(request.projectId(), request.title(), request.description(), actorUserId, source);
		task.updatePartial(null, null, request.priority(), request.riskLevel(), request.assignedAgentKey());
		task = taskRepository.save(task);
		auditService.record(actorUserId, "task.created", "task", task.getId(), Map.of("title", task.getTitle()));
		return task;
	}

	public Page<Task> list(UUID projectId, TaskStatus status, Pageable pageable) {
		if (projectId != null && status != null) {
			return taskRepository.findByProjectIdAndStatus(projectId, status, pageable);
		}
		if (projectId != null) {
			return taskRepository.findByProjectId(projectId, pageable);
		}
		if (status != null) {
			return taskRepository.findByStatus(status, pageable);
		}
		return taskRepository.findAll(pageable);
	}

	public TaskDetailResponse getDetail(UUID id) {
		Task task = getOrThrow(id);
		var runs = agentRunRepository.findByTaskId(id).stream().map(RunSummary::from).toList();
		return TaskDetailResponse.from(task, runs);
	}

	@Transactional
	public Task update(UUID id, PatchTaskRequest request, UUID actorUserId) {
		Task task = getOrThrow(id);
		task.updatePartial(request.title(), request.description(), request.priority(), request.riskLevel(),
				request.assignedAgentKey());
		task = taskRepository.save(task);
		auditService.record(actorUserId, "task.updated", "task", task.getId(), Map.of());
		return task;
	}

	@Transactional
	public Task move(UUID id, MoveTaskRequest request, UUID actorUserId) {
		Task task = getOrThrow(id);
		if (!TaskTransitionValidator.isLegal(task.getStatus(), request.status())) {
			throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "ILLEGAL_TRANSITION",
					"Cannot move task from %s to %s".formatted(task.getStatus(), request.status()));
		}
		task.move(request.status(), request.boardPosition());
		task = taskRepository.save(task);
		auditService.record(actorUserId, "task.moved", "task", task.getId(),
				Map.of("status", task.getStatus().name(), "boardPosition", task.getBoardPosition()));
		return task;
	}

	private Task getOrThrow(UUID id) {
		return taskRepository.findById(id)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TASK_NOT_FOUND", "Task not found"));
	}
}
