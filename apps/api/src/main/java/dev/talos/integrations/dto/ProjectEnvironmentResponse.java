// SPDX-FileCopyrightText: 2026 Vulkan Technologies
// SPDX-License-Identifier: AGPL-3.0-or-later

package dev.talos.integrations.dto;

import dev.talos.integrations.ProjectEnvironment;

import java.time.Instant;
import java.util.UUID;

public record ProjectEnvironmentResponse(UUID id, UUID projectId, String environment, String provider, String appId,
		boolean approvalRequired, String lastDeployStatus, Instant lastDeployedAt, UUID lastRunId) {

	public static ProjectEnvironmentResponse from(ProjectEnvironment env) {
		return new ProjectEnvironmentResponse(env.getId(), env.getProjectId(), env.getEnvironment(),
				env.getProvider(), env.getAppId(), env.isApprovalRequired(),
				env.getLastDeployStatus() == null ? null : env.getLastDeployStatus().name(), env.getLastDeployedAt(),
				env.getLastRunId());
	}
}
