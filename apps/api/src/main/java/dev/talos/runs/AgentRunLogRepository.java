package dev.talos.runs;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AgentRunLogRepository extends JpaRepository<AgentRunLog, UUID> {
	List<AgentRunLog> findByRunIdAndSequenceGreaterThanOrderBySequenceAsc(UUID runId, long sequence);
}
