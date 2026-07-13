// SPDX-FileCopyrightText: 2026 Vulkan Technologies
// SPDX-License-Identifier: AGPL-3.0-or-later

package dev.talos.runs.dto;

import dev.talos.projects.dto.ProjectSummary;
import dev.talos.tasks.dto.TaskSummary;

import java.util.Map;

/** Orchestrator pipeline bootstrap (Section 10.4's GET /internal/v1/runs/{id}/context). */
public record RunContextResponse(RunResponse run, TaskSummary task, ProjectSummary project,
		Map<String, Object> activeConfig) {
}
