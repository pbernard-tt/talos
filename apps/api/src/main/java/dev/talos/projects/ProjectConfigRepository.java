// SPDX-FileCopyrightText: 2026 Vulkan Technologies
// SPDX-License-Identifier: AGPL-3.0-or-later

package dev.talos.projects;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjectConfigRepository extends JpaRepository<ProjectConfig, UUID> {
	Optional<ProjectConfig> findByProjectIdAndActiveTrue(UUID projectId);

	Optional<ProjectConfig> findTopByProjectIdOrderByVersionDesc(UUID projectId);

	List<ProjectConfig> findByProjectIdOrderByVersionDesc(UUID projectId);
}
