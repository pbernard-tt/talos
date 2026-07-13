// SPDX-FileCopyrightText: 2026 Vulkan Technologies
// SPDX-License-Identifier: AGPL-3.0-or-later

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
		String source,
		/** Phase 14: lets the dashboard set an agent at creation time, e.g. following a
		 * recommendations hint (Section 16) -- an explicit operator choice, never auto-selected. */
		String assignedAgentKey) {
}
