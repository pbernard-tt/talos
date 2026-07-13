// SPDX-FileCopyrightText: 2026 Vulkan Technologies
// SPDX-License-Identifier: AGPL-3.0-or-later

package dev.talos.runs.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

/** Section 11 payload for run.status.changed. */
public record RunStatusChangedPayload(@JsonProperty("run_id") UUID runId, String from, String to) {
}
