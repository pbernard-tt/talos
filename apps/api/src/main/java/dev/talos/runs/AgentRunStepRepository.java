package dev.talos.runs;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AgentRunStepRepository extends JpaRepository<AgentRunStep, UUID> {
	List<AgentRunStep> findByRunId(UUID runId);
}
