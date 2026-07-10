package dev.talos.runs;

import dev.talos.auth.AuthenticatedUser;
import dev.talos.common.PageResponse;
import dev.talos.runs.dto.DiffResponse;
import dev.talos.runs.dto.LogEntryResponse;
import dev.talos.runs.dto.PullRequestResponse;
import dev.talos.runs.dto.RunDetailResponse;
import dev.talos.runs.dto.RunResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/runs")
public class RunController {

	private final RunService runService;
	private final RunEventBroadcaster broadcaster;

	public RunController(RunService runService, RunEventBroadcaster broadcaster) {
		this.runService = runService;
		this.broadcaster = broadcaster;
	}

	@GetMapping
	public PageResponse<RunResponse> list(
			@RequestParam(required = false) UUID projectId,
			@RequestParam(required = false) RunStatus status,
			@PageableDefault(size = 20) Pageable pageable) {
		return PageResponse.of(runService.list(projectId, status, pageable).map(RunResponse::from));
	}

	@GetMapping("/{id}")
	public RunDetailResponse get(@PathVariable UUID id) {
		return runService.getDetail(id);
	}

	@GetMapping("/{id}/logs")
	public PageResponse<LogEntryResponse> logs(@PathVariable UUID id,
			@RequestParam(defaultValue = "0") long afterSequence,
			@RequestParam(defaultValue = "500") int size) {
		return PageResponse.of(runService.getLogs(id, afterSequence, size));
	}

	@GetMapping("/{id}/diff")
	public DiffResponse diff(@PathVariable UUID id) {
		return runService.getDiff(id);
	}

	@GetMapping("/{id}/pull-request")
	public PullRequestResponse pullRequest(@PathVariable UUID id) {
		return runService.getPullRequest(id);
	}

	@PostMapping("/{id}/cancel")
	public RunResponse cancel(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser principal) {
		return RunResponse.from(runService.cancel(id, principal.id()));
	}

	/** Section 10.3: reconnect uses Last-Event-ID (the last log sequence the client already has); falls back to afterSequence for a fresh connection. */
	@GetMapping(value = "/{id}/events/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public SseEmitter stream(@PathVariable UUID id,
			@RequestHeader(value = "Last-Event-ID", required = false) String lastEventId,
			@RequestParam(required = false, defaultValue = "0") long afterSequence) {
		long resumeFrom = lastEventId != null ? Long.parseLong(lastEventId) : afterSequence;
		return broadcaster.subscribe(id, resumeFrom);
	}
}
