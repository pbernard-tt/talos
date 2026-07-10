package dev.talos.approvals;

import dev.talos.approvals.dto.ApprovalDetailResponse;
import dev.talos.approvals.dto.ApprovalResponse;
import dev.talos.approvals.dto.ApproveRequest;
import dev.talos.approvals.dto.RejectRequest;
import dev.talos.approvals.dto.RequestChangesRequest;
import dev.talos.auth.AuthenticatedUser;
import dev.talos.common.PageResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Section 10.2: the Review Center's approval actions. */
@RestController
@RequestMapping("/api/v1/approvals")
public class ApprovalController {

	private final ApprovalService approvalService;

	public ApprovalController(ApprovalService approvalService) {
		this.approvalService = approvalService;
	}

	@GetMapping
	public PageResponse<ApprovalResponse> list(
			@RequestParam(required = false) ApprovalStatus status,
			@RequestParam(required = false) UUID runId,
			@RequestParam(required = false) String type,
			@PageableDefault(size = 20) Pageable pageable) {
		return PageResponse.of(approvalService.list(status, runId, type, pageable).map(ApprovalResponse::from));
	}

	@GetMapping("/{id}")
	public ApprovalDetailResponse get(@PathVariable UUID id) {
		return approvalService.getDetail(id);
	}

	@PostMapping("/{id}/approve")
	public ApprovalResponse approve(@PathVariable UUID id, @RequestBody(required = false) ApproveRequest request,
			@AuthenticationPrincipal AuthenticatedUser principal) {
		String notes = request == null ? null : request.notes();
		return ApprovalResponse.from(approvalService.approve(id, principal.id(), notes));
	}

	@PostMapping("/{id}/reject")
	public ApprovalResponse reject(@PathVariable UUID id, @Valid @RequestBody RejectRequest request,
			@AuthenticationPrincipal AuthenticatedUser principal) {
		return ApprovalResponse.from(approvalService.reject(id, principal.id(), request.notes()));
	}

	@PostMapping("/{id}/request-changes")
	public ApprovalResponse requestChanges(@PathVariable UUID id, @Valid @RequestBody RequestChangesRequest request,
			@AuthenticationPrincipal AuthenticatedUser principal) {
		return ApprovalResponse.from(approvalService.requestChanges(id, principal.id(), request.notes()));
	}
}
