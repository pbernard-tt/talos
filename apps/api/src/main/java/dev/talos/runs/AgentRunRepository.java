package dev.talos.runs;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface AgentRunRepository extends JpaRepository<AgentRun, UUID> {
	List<AgentRun> findByTaskId(UUID taskId);

	List<AgentRun> findByStatus(RunStatus status);

	List<AgentRun> findTop5ByProjectIdOrderByCreatedAtDesc(UUID projectId);

	Page<AgentRun> findByProjectId(UUID projectId, Pageable pageable);

	Page<AgentRun> findByStatus(RunStatus status, Pageable pageable);

	Page<AgentRun> findByProjectIdAndStatus(UUID projectId, RunStatus status, Pageable pageable);

	boolean existsByTaskIdAndStatusNotIn(UUID taskId, Collection<RunStatus> statuses);

	List<AgentRun> findByStatusInAndTimeoutAtBefore(Collection<RunStatus> statuses, Instant instant);

	List<AgentRun> findByStatusInAndCompletedAtBefore(Collection<RunStatus> statuses, Instant instant);
}
