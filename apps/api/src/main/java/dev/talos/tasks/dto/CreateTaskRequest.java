package dev.talos.tasks.dto;

import dev.talos.tasks.TaskPriority;
import dev.talos.tasks.TaskRiskLevel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateTaskRequest(
		@NotNull UUID projectId,
		@NotBlank String title,
		String description,
		TaskPriority priority,
		TaskRiskLevel riskLevel,
		String source) {
}
