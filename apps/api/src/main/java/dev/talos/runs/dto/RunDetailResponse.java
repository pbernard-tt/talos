package dev.talos.runs.dto;

import dev.talos.runs.AgentRun;
import dev.talos.runs.ReviewStatus;
import dev.talos.runs.RunStatus;
import dev.talos.runs.TestStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record RunDetailResponse(
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
		Instant updatedAt,
		List<StepResponse> steps) {

	public static RunDetailResponse from(AgentRun run, List<StepResponse> steps) {
		return new RunDetailResponse(run.getId(), run.getTaskId(), run.getProjectId(), run.getStatus(),
				run.getAgentKey(), run.getProviderAuthMode(), run.getPrompt(), run.getBranchName(),
				run.getWorkspacePath(), run.getSummary(), run.getTestStatus(), run.getReviewStatus(),
				run.getErrorMessage(), run.getExitCode(), run.getTimeoutAt(), run.getStartedAt(),
				run.getCompletedAt(), run.getCreatedAt(), run.getUpdatedAt(), steps);
	}
}
