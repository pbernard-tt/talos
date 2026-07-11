package dev.talos.recommendations;

import dev.talos.runs.GitChange;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

import java.util.List;
import java.util.UUID;

/** Section 16 Phase 14: "this file area has failed review twice" risk-flag signal. Reuses the
 * Section 12.1 policy scan's per-file risk_flagged marker (git_changes) -- the automated review
 * outcome, not a human decision -- grouped by the file's top-level path segment. */
public interface RiskFlagQueryRepository extends Repository<GitChange, UUID> {

	@Query(value = """
			SELECT split_part(gc.file_path, '/', 1) AS fileArea,
			       COUNT(DISTINCT gc.run_id) AS riskFlaggedRunCount
			FROM git_changes gc
			JOIN agent_runs ar ON ar.id = gc.run_id
			WHERE ar.project_id = :projectId AND gc.risk_flagged = true
			GROUP BY split_part(gc.file_path, '/', 1)
			HAVING COUNT(DISTINCT gc.run_id) >= 2
			ORDER BY riskFlaggedRunCount DESC, fileArea
			""", nativeQuery = true)
	List<RiskFlagProjection> aggregateRiskFlagsByFileArea(UUID projectId);
}
