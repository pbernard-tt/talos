// SPDX-FileCopyrightText: 2026 Vulkan Technologies
// SPDX-License-Identifier: AGPL-3.0-or-later

package dev.talos.projects.dto;

import dev.talos.projects.ProjectConfig;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record ProjectConfigResponse(
		UUID id,
		UUID projectId,
		String configYaml,
		Map<String, Object> parsedJson,
		int version,
		boolean active,
		Instant createdAt) {

	public static ProjectConfigResponse from(ProjectConfig config) {
		return new ProjectConfigResponse(config.getId(), config.getProjectId(), config.getConfigYaml(),
				config.getParsedJson(), config.getVersion(), config.isActive(), config.getCreatedAt());
	}
}
