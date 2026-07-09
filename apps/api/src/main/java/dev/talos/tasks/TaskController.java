package dev.talos.tasks;

import dev.talos.auth.AuthenticatedUser;
import dev.talos.common.PageResponse;
import dev.talos.runs.RunService;
import dev.talos.runs.dto.RunResponse;
import dev.talos.runs.dto.StartRunRequest;
import dev.talos.tasks.dto.CreateTaskRequest;
import dev.talos.tasks.dto.MoveTaskRequest;
import dev.talos.tasks.dto.PatchTaskRequest;
import dev.talos.tasks.dto.TaskDetailResponse;
import dev.talos.tasks.dto.TaskSummary;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tasks")
public class TaskController {

	private final TaskService taskService;
	private final RunService runService;

	public TaskController(TaskService taskService, RunService runService) {
		this.taskService = taskService;
		this.runService = runService;
	}

	@GetMapping
	public PageResponse<TaskSummary> list(
			@RequestParam(required = false) UUID projectId,
			@RequestParam(required = false) TaskStatus status,
			@PageableDefault(size = 20) Pageable pageable) {
		return PageResponse.of(taskService.list(projectId, status, pageable).map(TaskSummary::from));
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public TaskSummary create(@Valid @RequestBody CreateTaskRequest request,
			@AuthenticationPrincipal AuthenticatedUser principal) {
		return TaskSummary.from(taskService.create(request, principal.id()));
	}

	@GetMapping("/{id}")
	public TaskDetailResponse get(@PathVariable UUID id) {
		return taskService.getDetail(id);
	}

	@PatchMapping("/{id}")
	public TaskSummary update(@PathVariable UUID id, @RequestBody PatchTaskRequest request,
			@AuthenticationPrincipal AuthenticatedUser principal) {
		return TaskSummary.from(taskService.update(id, request, principal.id()));
	}

	@PostMapping("/{id}/move")
	public TaskSummary move(@PathVariable UUID id, @Valid @RequestBody MoveTaskRequest request,
			@AuthenticationPrincipal AuthenticatedUser principal) {
		return TaskSummary.from(taskService.move(id, request, principal.id()));
	}

	@PostMapping("/{id}/start-run")
	@ResponseStatus(HttpStatus.CREATED)
	public RunResponse startRun(@PathVariable UUID id, @RequestBody(required = false) StartRunRequest request,
			@AuthenticationPrincipal AuthenticatedUser principal) {
		return RunResponse.from(runService.startRun(id, request, principal.id()));
	}
}
