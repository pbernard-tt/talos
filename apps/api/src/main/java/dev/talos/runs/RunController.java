package dev.talos.runs;

import dev.talos.artifacts.ArtifactService;
import dev.talos.artifacts.RunArtifact;
import dev.talos.artifacts.dto.RunArtifactResponse;
import dev.talos.auth.AuthenticatedUser;
import dev.talos.common.PageResponse;
import dev.talos.integrations.DeployService;
import dev.talos.integrations.dto.ProjectEnvironmentResponse;
import dev.talos.runs.dto.DeployTriggerResponse;
import dev.talos.runs.dto.DiffResponse;
import dev.talos.runs.dto.LogEntryResponse;
import dev.talos.runs.dto.PullRequestResponse;
import dev.talos.runs.dto.RunDetailResponse;
import dev.talos.runs.dto.RunResponse;
import org.springframework.core.io.InputStreamResource;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/runs")
public class RunController {

	private final RunService runService;
	private final RunEventBroadcaster broadcaster;
	private final DeployService deployService;
	private final ArtifactService artifactService;

	public RunController(RunService runService, RunEventBroadcaster broadcaster, DeployService deployService,
			ArtifactService artifactService) {
		this.runService = runService;
		this.broadcaster = broadcaster;
		this.deployService = deployService;
		this.artifactService = artifactService;
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

	/** Phase 16: transcripts/patches/test reports/generated docs recorded via the internal artifacts endpoint. */
	@GetMapping("/{id}/artifacts")
	public List<RunArtifactResponse> artifacts(@PathVariable UUID id) {
		runService.getOrThrow(id);
		return artifactService.list(id).stream().map(RunArtifactResponse::from).toList();
	}

	/** Streams the artifact's bytes byte-identical regardless of the configured {@code ArtifactStore} (Phase 16 acceptance). */
	@GetMapping("/{id}/artifacts/{artifactId}/download")
	public ResponseEntity<InputStreamResource> downloadArtifact(@PathVariable UUID id, @PathVariable UUID artifactId) {
		runService.getOrThrow(id);
		RunArtifact artifact = artifactService.getOrThrow(id, artifactId);
		InputStreamResource body = new InputStreamResource(artifactService.download(artifact));
		return ResponseEntity.ok()
				.contentType(MediaType.parseMediaType(artifact.getContentType()))
				.contentLength(artifact.getSizeBytes())
				.header(HttpHeaders.CONTENT_DISPOSITION,
						ContentDisposition.attachment().filename(artifact.getName()).build().toString())
				.body(body);
	}

	@PostMapping("/{id}/deploy")
	@PreAuthorize("hasRole('OWNER')")
	public DeployTriggerResponse deploy(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser principal) {
		return DeployTriggerResponse.from(deployService.requestDeploy(runService.getOrThrow(id), principal.id()));
	}

	@GetMapping("/{id}/deploy")
	public ProjectEnvironmentResponse deployStatus(@PathVariable UUID id) {
		return ProjectEnvironmentResponse.from(deployService.getStatus(runService.getOrThrow(id)));
	}

	@PostMapping("/{id}/cancel")
	@PreAuthorize("hasRole('MAINTAINER')")
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
