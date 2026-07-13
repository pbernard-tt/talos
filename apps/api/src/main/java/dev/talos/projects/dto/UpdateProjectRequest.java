// SPDX-FileCopyrightText: 2026 Vulkan Technologies
// SPDX-License-Identifier: AGPL-3.0-or-later

package dev.talos.projects.dto;

import dev.talos.projects.ProjectStatus;
import jakarta.validation.constraints.NotBlank;

public record UpdateProjectRequest(
		@NotBlank String name,
		@NotBlank String repoUrl,
		@NotBlank String defaultBranch,
		@NotBlank String stackType,
		ProjectStatus status) {
}
