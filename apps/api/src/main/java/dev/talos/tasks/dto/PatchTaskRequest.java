// SPDX-FileCopyrightText: 2026 Vulkan Technologies
// SPDX-License-Identifier: AGPL-3.0-or-later

package dev.talos.tasks.dto;

import dev.talos.tasks.TaskPriority;
import dev.talos.tasks.TaskRiskLevel;

/** Partial update (Section 10.4): only non-null fields are applied. */
public record PatchTaskRequest(
		String title,
		String description,
		TaskPriority priority,
		TaskRiskLevel riskLevel,
		String assignedAgentKey) {
}
