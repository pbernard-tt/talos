package dev.talos.runs.dto;

import dev.talos.runs.AgentRunStep;
import dev.talos.runs.StepStatus;
import dev.talos.runs.StepType;

import java.time.Instant;
import java.util.UUID;

public record StepResponse(
		UUID id,
		UUID runId,
		StepType stepType,
		StepStatus status,
		String summary,
		Instant startedAt,
		Instant completedAt) {

	public static StepResponse from(AgentRunStep step) {
		return new StepResponse(step.getId(), step.getRunId(), step.getStepType(), step.getStatus(),
				step.getSummary(), step.getStartedAt(), step.getCompletedAt());
	}
}
