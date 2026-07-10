package dev.talos.integrations.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

/** Section 11 payload for deploy.requested/deploy.completed/deploy.failed (API -> notifiers, Phase 10). */
public record DeployEventPayload(
		@JsonProperty("run_id") UUID runId,
		@JsonProperty("project_id") UUID projectId,
		String environment,
		@JsonProperty("dokploy_app_id") String dokployAppId,
		String error) {
}
