// SPDX-FileCopyrightText: 2026 Vulkan Technologies
// SPDX-License-Identifier: AGPL-3.0-or-later

package dev.talos.integrations;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjectEnvironmentRepository extends JpaRepository<ProjectEnvironment, UUID> {
	Optional<ProjectEnvironment> findByProjectIdAndEnvironment(UUID projectId, String environment);

	List<ProjectEnvironment> findByProjectId(UUID projectId);

	List<ProjectEnvironment> findByLastDeployStatus(DeployStatus status);
}
