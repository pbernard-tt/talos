// SPDX-FileCopyrightText: 2026 Vulkan Technologies
// SPDX-License-Identifier: AGPL-3.0-or-later

package dev.talos.runs.dto;

import jakarta.validation.constraints.NotBlank;

/** POST /internal/v1/runs/{id}/pull-request: the orchestrator supplies what the runner supervisor's push step produced. */
public record InternalPullRequestRequest(@NotBlank String branchName, @NotBlank String commitSha) {
}
