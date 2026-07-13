// SPDX-FileCopyrightText: 2026 Vulkan Technologies
// SPDX-License-Identifier: AGPL-3.0-or-later

package dev.talos.approvals.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

/** Section 11 payload for approval.decided (API -> orchestrator, talos.orchestrator.approvals). */
public record ApprovalDecidedPayload(
		@JsonProperty("approval_id") UUID approvalId,
		@JsonProperty("run_id") UUID runId,
		String status,
		@JsonProperty("decided_by") UUID decidedBy) {
}
