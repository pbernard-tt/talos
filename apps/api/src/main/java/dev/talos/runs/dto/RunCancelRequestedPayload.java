// SPDX-FileCopyrightText: 2026 Vulkan Technologies
// SPDX-License-Identifier: AGPL-3.0-or-later

package dev.talos.runs.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

/** Phase 6 extension payload for run.cancel.requested (see packages/contracts/events/run.cancel.requested.json). */
public record RunCancelRequestedPayload(@JsonProperty("run_id") UUID runId) {
}
