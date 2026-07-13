// SPDX-FileCopyrightText: 2026 Vulkan Technologies
// SPDX-License-Identifier: AGPL-3.0-or-later

package dev.talos.projects.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateProjectRequest(
		@NotBlank String name,
		@NotBlank String repoUrl,
		String defaultBranch,
		@NotBlank String stackType) {
}
