package dev.talos.runs;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface GitChangeRepository extends JpaRepository<GitChange, UUID> {
	List<GitChange> findByRunId(UUID runId);
}
