package dev.talos.runs.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

/** Section 11 payload for run.status.changed. */
public record RunStatusChangedPayload(@JsonProperty("run_id") UUID runId, String from, String to) {
}
