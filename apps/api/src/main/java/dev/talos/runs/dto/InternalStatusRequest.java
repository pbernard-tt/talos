// SPDX-FileCopyrightText: 2026 Vulkan Technologies
// SPDX-License-Identifier: AGPL-3.0-or-later

package dev.talos.runs.dto;

import dev.talos.runs.RunStatus;
import dev.talos.runs.TestStatus;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Section 10.4's general-purpose run-transition endpoint. The optional fields beyond
 * status/errorMessage are a Phase 6 extension: the pipeline naturally produces workspace_path and
 * branchName right when it transitions PREPARING_WORKSPACE -> RUNNING_AGENT, prompt/exitCode right
 * when it transitions RUNNING_AGENT -> RUNNING_TESTS, and testStatus right when it transitions
 * RUNNING_TESTS -> REVIEWING -- reusing this endpoint keeps Section 10.4's enumerated endpoint list
 * unchanged rather than adding new ones for each field. Phase 14 adds inputTokens/outputTokens/
 * costUsd/costModel, reported alongside exitCode right after agent execution.
 */
public record InternalStatusRequest(@NotNull RunStatus status, String errorMessage, TestStatus testStatus,
		String workspacePath, String branchName, String prompt, String summary, Integer exitCode,
		Integer inputTokens, Integer outputTokens, BigDecimal costUsd, String costModel) {
}
