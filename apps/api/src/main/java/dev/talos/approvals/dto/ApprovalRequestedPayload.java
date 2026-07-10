package dev.talos.approvals.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

/** Section 11 payload for approval.requested (API -> notifiers). */
public record ApprovalRequestedPayload(
		@JsonProperty("approval_id") UUID approvalId,
		@JsonProperty("run_id") UUID runId,
		@JsonProperty("task_title") String taskTitle,
		@JsonProperty("review_status") String reviewStatus) {
}
