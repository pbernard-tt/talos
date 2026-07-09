package dev.talos.runs;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AgentRunRepository extends JpaRepository<AgentRun, UUID> {
	List<AgentRun> findByTaskId(UUID taskId);

	List<AgentRun> findByStatus(RunStatus status);
}
