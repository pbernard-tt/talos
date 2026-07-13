// SPDX-FileCopyrightText: 2026 Vulkan Technologies
// SPDX-License-Identifier: AGPL-3.0-or-later

package dev.talos.projects;

import dev.talos.common.ApiException;
import dev.talos.integrations.ProjectEnvironmentRepository;
import dev.talos.integrations.dto.ProjectEnvironmentResponse;
import dev.talos.projects.dto.CreateProjectRequest;
import dev.talos.projects.dto.ProjectConfigResponse;
import dev.talos.projects.dto.ProjectDetailResponse;
import dev.talos.projects.dto.RunSummary;
import dev.talos.projects.dto.SyncConfigRequest;
import dev.talos.projects.dto.UpdateProjectRequest;
import dev.talos.runs.AgentRunRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class ProjectService {

	private final ProjectRepository projectRepository;
	private final ProjectConfigRepository projectConfigRepository;
	private final AgentRunRepository agentRunRepository;
	private final ProjectEnvironmentRepository projectEnvironmentRepository;
	private final TalosConfigParser configParser;

	public ProjectService(ProjectRepository projectRepository, ProjectConfigRepository projectConfigRepository,
			AgentRunRepository agentRunRepository, ProjectEnvironmentRepository projectEnvironmentRepository,
			TalosConfigParser configParser) {
		this.projectRepository = projectRepository;
		this.projectConfigRepository = projectConfigRepository;
		this.agentRunRepository = agentRunRepository;
		this.projectEnvironmentRepository = projectEnvironmentRepository;
		this.configParser = configParser;
	}

	@Transactional
	public Project create(CreateProjectRequest request) {
		String defaultBranch = (request.defaultBranch() == null || request.defaultBranch().isBlank())
				? "main"
				: request.defaultBranch();
		String slug = generateUniqueSlug(request.name());
		Project project = new Project(request.name(), slug, request.repoUrl(), defaultBranch, request.stackType());
		return projectRepository.save(project);
	}

	public Page<Project> list(ProjectStatus status, Pageable pageable) {
		return status != null
				? projectRepository.findByStatus(status, pageable)
				: projectRepository.findAll(pageable);
	}

	public ProjectDetailResponse getDetail(UUID id) {
		Project project = getOrThrow(id);
		ProjectConfigResponse activeConfig = projectConfigRepository.findByProjectIdAndActiveTrue(id)
				.map(ProjectConfigResponse::from)
				.orElse(null);
		List<ProjectConfigResponse> configHistory = projectConfigRepository.findByProjectIdOrderByVersionDesc(id)
				.stream()
				.map(ProjectConfigResponse::from)
				.toList();
		List<RunSummary> recentRuns = agentRunRepository.findTop5ByProjectIdOrderByCreatedAtDesc(id).stream()
				.map(RunSummary::from)
				.toList();
		return ProjectDetailResponse.from(project, activeConfig, configHistory, recentRuns);
	}

	public List<ProjectEnvironmentResponse> listEnvironments(UUID id) {
		getOrThrow(id);
		return projectEnvironmentRepository.findByProjectId(id).stream().map(ProjectEnvironmentResponse::from).toList();
	}

	@Transactional
	public Project update(UUID id, UpdateProjectRequest request) {
		Project project = getOrThrow(id);
		project.update(request.name(), request.repoUrl(), request.defaultBranch(), request.stackType(),
				request.status());
		return projectRepository.save(project);
	}

	@Transactional
	public ProjectConfig syncConfig(UUID id, SyncConfigRequest request) {
		getOrThrow(id);

		TalosConfigParser.Result result = configParser.parse(request.configYaml());
		if (result instanceof TalosConfigParser.Result.Invalid invalid) {
			throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "INVALID_CONFIG",
					"talos.yaml failed schema validation", Map.copyOf(invalid.fieldErrors()));
		}
		TalosConfigParser.Result.Valid valid = (TalosConfigParser.Result.Valid) result;

		projectConfigRepository.findByProjectIdAndActiveTrue(id).ifPresent(existing -> {
			existing.setActive(false);
			projectConfigRepository.save(existing);
		});

		int nextVersion = projectConfigRepository.findTopByProjectIdOrderByVersionDesc(id)
				.map(c -> c.getVersion() + 1)
				.orElse(1);

		ProjectConfig config = new ProjectConfig(id, request.configYaml(), valid.parsedJson(), nextVersion);
		return projectConfigRepository.save(config);
	}

	private Project getOrThrow(UUID id) {
		return projectRepository.findById(id)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PROJECT_NOT_FOUND", "Project not found"));
	}

	private String generateUniqueSlug(String name) {
		String base = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-+|-+$)", "");
		if (base.isBlank()) {
			base = "project";
		}
		String candidate = base;
		int suffix = 2;
		while (projectRepository.existsBySlug(candidate)) {
			candidate = base + "-" + suffix++;
		}
		return candidate;
	}
}
