package dev.talos.projects.dto;

import dev.talos.runs.AgentRun;

import java.time.Instant;
import java.util.UUID;

/** Minimal run summary embedded in ProjectDetailResponse.recentRuns; the full RunSummary lands with Phase 5's GET /api/v1/runs. */
public record RunSummary(UUID id, UUID taskId, String status, String agentKey, Instant createdAt) {

	public static RunSummary from(AgentRun run) {
		return new RunSummary(run.getId(), run.getTaskId(), run.getStatus().name(), run.getAgentKey(),
				run.getCreatedAt());
	}
}
