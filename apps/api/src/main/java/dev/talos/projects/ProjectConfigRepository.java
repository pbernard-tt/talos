package dev.talos.projects;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ProjectConfigRepository extends JpaRepository<ProjectConfig, UUID> {
	Optional<ProjectConfig> findByProjectIdAndActiveTrue(UUID projectId);
}
