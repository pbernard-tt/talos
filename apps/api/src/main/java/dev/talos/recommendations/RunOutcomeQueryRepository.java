package dev.talos.recommendations;

import dev.talos.runs.AgentRun;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

import java.util.List;
import java.util.UUID;

/** Section 16 Phase 14: per-agent outcome/cost aggregates for the "suggested agent" and
 * "cheapest-capable-agent" recommendation signals. A separate {@link Repository} (rather than
 * adding to AgentRunRepository) keeps this advisory-signal concern out of the run-lifecycle
 * repository. */
public interface RunOutcomeQueryRepository extends Repository<AgentRun, UUID> {

	/** Per-agent totals for a project, across every run that reached a terminal state. */
	@Query(value = """
			SELECT agent_key AS agentKey,
			       COUNT(*) AS totalRuns,
			       COUNT(*) FILTER (WHERE status = 'COMPLETED') AS completedRuns,
			       COUNT(*) FILTER (WHERE review_status = 'RISK_FLAGGED') AS riskFlaggedRuns,
			       AVG(cost_usd) AS avgCostUsd
			FROM agent_runs
			WHERE project_id = :projectId AND completed_at IS NOT NULL
			GROUP BY agent_key
			""", nativeQuery = true)
	List<AgentStatProjection> aggregateAgentStats(UUID projectId);
}
