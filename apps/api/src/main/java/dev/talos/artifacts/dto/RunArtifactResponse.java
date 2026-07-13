// SPDX-FileCopyrightText: 2026 Vulkan Technologies
// SPDX-License-Identifier: AGPL-3.0-or-later

package dev.talos.artifacts.dto;

import dev.talos.artifacts.ArtifactKind;
import dev.talos.artifacts.RunArtifact;

import java.time.Instant;
import java.util.UUID;

public record RunArtifactResponse(UUID id, ArtifactKind kind, String name, long sizeBytes, String contentType,
		Instant createdAt) {

	public static RunArtifactResponse from(RunArtifact artifact) {
		return new RunArtifactResponse(artifact.getId(), artifact.getKind(), artifact.getName(),
				artifact.getSizeBytes(), artifact.getContentType(), artifact.getCreatedAt());
	}
}
