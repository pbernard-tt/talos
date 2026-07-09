package dev.talos.projects.dto;

import dev.talos.projects.Project;
import dev.talos.projects.ProjectStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Section 10.2: GET /api/v1/projects/{id} -> Project + activeConfig + last 5 runs. */
public record ProjectDetailResponse(
		UUID id,
		String name,
		String slug,
		String repoUrl,
		String defaultBranch,
		String stackType,
		ProjectStatus status,
		Instant createdAt,
		Instant updatedAt,
		ProjectConfigResponse activeConfig,
		List<RunSummary> recentRuns) {

	public static ProjectDetailResponse from(Project project, ProjectConfigResponse activeConfig,
			List<RunSummary> recentRuns) {
		return new ProjectDetailResponse(project.getId(), project.getName(), project.getSlug(),
				project.getRepoUrl(), project.getDefaultBranch(), project.getStackType(), project.getStatus(),
				project.getCreatedAt(), project.getUpdatedAt(), activeConfig, recentRuns);
	}
}
