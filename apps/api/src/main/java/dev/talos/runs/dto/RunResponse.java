package dev.talos.runs.dto;

import dev.talos.runs.AgentRun;
import dev.talos.runs.ReviewStatus;
import dev.talos.runs.RunStatus;
import dev.talos.runs.TestStatus;

import java.time.Instant;
import java.util.UUID;

/** Also used as the "Run" response for single-resource endpoints (Section 10.2) — same fields. */
public record RunResponse(
		UUID id,
		UUID taskId,
		UUID projectId,
		RunStatus status,
		String agentKey,
		String providerAuthMode,
		String prompt,
		String branchName,
		String workspacePath,
		String summary,
		TestStatus testStatus,
		ReviewStatus reviewStatus,
		String errorMessage,
		Integer exitCode,
		Instant timeoutAt,
		Instant startedAt,
		Instant completedAt,
		Instant createdAt,
		Instant updatedAt) {

	public static RunResponse from(AgentRun run) {
		return new RunResponse(run.getId(), run.getTaskId(), run.getProjectId(), run.getStatus(), run.getAgentKey(),
				run.getProviderAuthMode(), run.getPrompt(), run.getBranchName(), run.getWorkspacePath(),
				run.getSummary(), run.getTestStatus(), run.getReviewStatus(), run.getErrorMessage(),
				run.getExitCode(), run.getTimeoutAt(), run.getStartedAt(), run.getCompletedAt(), run.getCreatedAt(),
				run.getUpdatedAt());
	}
}
