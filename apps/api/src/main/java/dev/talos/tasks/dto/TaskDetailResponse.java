// SPDX-FileCopyrightText: 2026 Vulkan Technologies
// SPDX-License-Identifier: AGPL-3.0-or-later

package dev.talos.tasks.dto;

import dev.talos.projects.dto.RunSummary;
import dev.talos.tasks.Task;
import dev.talos.tasks.TaskPriority;
import dev.talos.tasks.TaskRiskLevel;
import dev.talos.tasks.TaskStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TaskDetailResponse(
		UUID id,
		UUID projectId,
		String title,
		String description,
		String source,
		TaskStatus status,
		TaskPriority priority,
		TaskRiskLevel riskLevel,
		int boardPosition,
		UUID requestedBy,
		String assignedAgentKey,
		Instant createdAt,
		Instant updatedAt,
		List<RunSummary> runs) {

	public static TaskDetailResponse from(Task task, List<RunSummary> runs) {
		return new TaskDetailResponse(task.getId(), task.getProjectId(), task.getTitle(), task.getDescription(),
				task.getSource(), task.getStatus(), task.getPriority(), task.getRiskLevel(), task.getBoardPosition(),
				task.getRequestedBy(), task.getAssignedAgentKey(), task.getCreatedAt(), task.getUpdatedAt(), runs);
	}
}
