// SPDX-FileCopyrightText: 2026 Vulkan Technologies
// SPDX-License-Identifier: AGPL-3.0-or-later

package dev.talos.projects.dto;

import dev.talos.projects.Project;
import dev.talos.projects.ProjectStatus;

import java.time.Instant;
import java.util.UUID;

/** Also used as the "Project" response for single-resource endpoints (Section 10.2) — same fields. */
public record ProjectSummary(
		UUID id,
		String name,
		String slug,
		String repoUrl,
		String defaultBranch,
		String stackType,
		ProjectStatus status,
		Instant createdAt,
		Instant updatedAt) {

	public static ProjectSummary from(Project project) {
		return new ProjectSummary(project.getId(), project.getName(), project.getSlug(), project.getRepoUrl(),
				project.getDefaultBranch(), project.getStackType(), project.getStatus(), project.getCreatedAt(),
				project.getUpdatedAt());
	}
}
