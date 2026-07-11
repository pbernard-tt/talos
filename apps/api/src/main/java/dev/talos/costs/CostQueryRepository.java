package dev.talos.costs;

import dev.talos.runs.AgentRun;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Section 16 Phase 14: read-only aggregate queries over agent_runs for the dashboard cost widget.
 * A separate {@link Repository} (rather than adding to AgentRunRepository) keeps this
 * cost-reporting concern out of the run-lifecycle repository that RunService owns.
 */
public interface CostQueryRepository extends Repository<AgentRun, UUID> {

	/** Monthly per-agent totals for a project, newest month first. Runs still in flight (no
	 * completed_at) are excluded -- they have no final usage/cost yet. SUM(cost_usd) is null for a
	 * month with no priced runs (e.g. subscription-only or CustomShellAdapter activity) rather than
	 * a fabricated zero. */
	@Query(value = """
			SELECT agent_key AS agentKey,
			       to_char(completed_at, 'YYYY-MM') AS month,
			       SUM(cost_usd) AS totalCostUsd,
			       COALESCE(SUM(input_tokens), 0) AS totalInputTokens,
			       COALESCE(SUM(output_tokens), 0) AS totalOutputTokens,
			       COUNT(*) AS runCount,
			       COUNT(*) FILTER (WHERE provider_auth_mode = 'subscription_local') AS subscriptionRunCount
			FROM agent_runs
			WHERE project_id = :projectId AND completed_at IS NOT NULL
			GROUP BY agent_key, to_char(completed_at, 'YYYY-MM')
			ORDER BY month DESC, agent_key
			""", nativeQuery = true)
	List<MonthlyCostProjection> aggregateMonthlyCosts(UUID projectId);
}
