package dev.talos.artifacts;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RunArtifactRepository extends JpaRepository<RunArtifact, UUID> {
	List<RunArtifact> findByRunId(UUID runId);
}
