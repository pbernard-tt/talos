package dev.talos.projects.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateProjectRequest(
		@NotBlank String name,
		@NotBlank String repoUrl,
		String defaultBranch,
		@NotBlank String stackType) {
}
