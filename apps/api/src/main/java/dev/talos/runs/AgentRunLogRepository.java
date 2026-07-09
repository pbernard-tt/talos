package dev.talos.runs;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AgentRunLogRepository extends JpaRepository<AgentRunLog, UUID> {
	List<AgentRunLog> findByRunIdAndSequenceGreaterThanOrderBySequenceAsc(UUID runId, long sequence);

	Page<AgentRunLog> findByRunIdAndSequenceGreaterThan(UUID runId, long sequence, Pageable pageable);
}
