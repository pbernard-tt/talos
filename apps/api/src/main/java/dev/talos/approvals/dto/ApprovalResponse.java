package dev.talos.approvals.dto;

import dev.talos.approvals.Approval;
import dev.talos.approvals.ApprovalStatus;

import java.time.Instant;
import java.util.UUID;

public record ApprovalResponse(
		UUID id,
		UUID taskId,
		UUID runId,
		String approvalType,
		String requestedAction,
		ApprovalStatus status,
		UUID requestedBy,
		UUID approvedBy,
		Instant approvedAt,
		String notes,
		Instant expiresAt,
		Instant createdAt,
		String environment) {

	public static ApprovalResponse from(Approval approval) {
		return new ApprovalResponse(approval.getId(), approval.getTaskId(), approval.getRunId(),
				approval.getApprovalType(), approval.getRequestedAction(), approval.getStatus(),
				approval.getRequestedBy(), approval.getApprovedBy(), approval.getApprovedAt(), approval.getNotes(),
				approval.getExpiresAt(), approval.getCreatedAt(), approval.getEnvironment());
	}
}
