// SPDX-FileCopyrightText: 2026 Vulkan Technologies
// SPDX-License-Identifier: AGPL-3.0-or-later

package dev.talos.artifacts;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RunArtifactRepository extends JpaRepository<RunArtifact, UUID> {
	List<RunArtifact> findByRunId(UUID runId);
}
