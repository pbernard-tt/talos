// SPDX-FileCopyrightText: 2026 Vulkan Technologies
// SPDX-License-Identifier: AGPL-3.0-or-later

package dev.talos.runs.dto;

import dev.talos.approvals.dto.ApprovalResponse;
import dev.talos.integrations.DeployService.DeployRequestResult;
import dev.talos.integrations.dto.ProjectEnvironmentResponse;

/** POST /runs/{id}/deploy (Phase 10): discriminated on approvalRequired -- approval is present only when true. */
public record DeployTriggerResponse(boolean approvalRequired, ApprovalResponse approval,
		ProjectEnvironmentResponse environment) {

	public static DeployTriggerResponse from(DeployRequestResult result) {
		return new DeployTriggerResponse(result.approvalRequired(),
				result.approval() == null ? null : ApprovalResponse.from(result.approval()),
				ProjectEnvironmentResponse.from(result.environment()));
	}
}
