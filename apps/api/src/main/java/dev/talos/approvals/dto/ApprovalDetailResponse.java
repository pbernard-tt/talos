package dev.talos.approvals.dto;

import dev.talos.runs.dto.GitChangeResponse;
import dev.talos.runs.dto.RunResponse;

import java.util.List;

/** Section 10.2: GET /api/v1/approvals/{id} -> approval + run + diff summary. */
public record ApprovalDetailResponse(ApprovalResponse approval, RunResponse run, List<GitChangeResponse> changes) {
}
