package dev.talos.runs.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

/** Section 11 payload for task.run.requested. */
public record TaskRunRequestedPayload(
		@JsonProperty("run_id") UUID runId,
		@JsonProperty("task_id") UUID taskId,
		@JsonProperty("project_id") UUID projectId,
		@JsonProperty("agent_key") String agentKey,
		@JsonProperty("auth_mode") String authMode) {
}
