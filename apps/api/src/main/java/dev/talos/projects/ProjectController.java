package dev.talos.projects;

import dev.talos.common.PageResponse;
import dev.talos.integrations.dto.ProjectEnvironmentResponse;
import dev.talos.projects.dto.CreateProjectRequest;
import dev.talos.projects.dto.ProjectConfigResponse;
import dev.talos.projects.dto.ProjectDetailResponse;
import dev.talos.projects.dto.ProjectSummary;
import dev.talos.projects.dto.SyncConfigRequest;
import dev.talos.projects.dto.UpdateProjectRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/projects")
public class ProjectController {

	private final ProjectService projectService;

	public ProjectController(ProjectService projectService) {
		this.projectService = projectService;
	}

	@GetMapping
	public PageResponse<ProjectSummary> list(
			@RequestParam(required = false) ProjectStatus status,
			@PageableDefault(size = 20) Pageable pageable) {
		return PageResponse.of(projectService.list(status, pageable).map(ProjectSummary::from));
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public ProjectSummary create(@Valid @RequestBody CreateProjectRequest request) {
		return ProjectSummary.from(projectService.create(request));
	}

	@GetMapping("/{id}")
	public ProjectDetailResponse get(@PathVariable UUID id) {
		return projectService.getDetail(id);
	}

	@PutMapping("/{id}")
	public ProjectSummary update(@PathVariable UUID id, @Valid @RequestBody UpdateProjectRequest request) {
		return ProjectSummary.from(projectService.update(id, request));
	}

	@PostMapping("/{id}/sync-config")
	public ProjectConfigResponse syncConfig(@PathVariable UUID id, @Valid @RequestBody SyncConfigRequest request) {
		return ProjectConfigResponse.from(projectService.syncConfig(id, request));
	}

	@GetMapping("/{id}/environments")
	public List<ProjectEnvironmentResponse> environments(@PathVariable UUID id) {
		return projectService.listEnvironments(id);
	}
}
