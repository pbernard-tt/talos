package dev.talos.integrations.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

/** Section 11 payload for pr.created (API -> notifiers). */
public record PrCreatedPayload(
		@JsonProperty("run_id") UUID runId,
		@JsonProperty("pr_url") String prUrl,
		@JsonProperty("pr_number") Integer prNumber) {
}
