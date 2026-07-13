// SPDX-FileCopyrightText: 2026 Vulkan Technologies
// SPDX-License-Identifier: AGPL-3.0-or-later

package dev.talos.runs;

import dev.talos.artifacts.ArtifactKind;
import dev.talos.artifacts.ArtifactService;
import dev.talos.artifacts.dto.RunArtifactResponse;
import dev.talos.integrations.GitCredentialsService;
import dev.talos.integrations.PullRequestService;
import dev.talos.runs.dto.GitTokenResponse;
import dev.talos.runs.dto.InternalChangesRequest;
import dev.talos.runs.dto.InternalLogsRequest;
import dev.talos.runs.dto.InternalPullRequestRequest;
import dev.talos.runs.dto.InternalStatusRequest;
import dev.talos.runs.dto.InternalStepRequest;
import dev.talos.runs.dto.PullRequestResponse;
import dev.talos.runs.dto.RetentionCandidatesResponse;
import dev.talos.runs.dto.RunContextResponse;
import dev.talos.runs.dto.RunResponse;
import dev.talos.runs.dto.StepResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

/** Section 10.4: service-token-authenticated, called only by the orchestrator/runner supervisor. */
@RestController
@RequestMapping("/internal/v1/runs")
public class InternalRunController {

	private final RunService runService;
	private final GitCredentialsService gitCredentialsService;
	private final PullRequestService pullRequestService;
	private final ArtifactService artifactService;

	public InternalRunController(RunService runService, GitCredentialsService gitCredentialsService,
			PullRequestService pullRequestService, ArtifactService artifactService) {
		this.runService = runService;
		this.gitCredentialsService = gitCredentialsService;
		this.pullRequestService = pullRequestService;
		this.artifactService = artifactService;
	}

	@PostMapping("/{id}/status")
	public RunResponse updateStatus(@PathVariable UUID id, @Valid @RequestBody InternalStatusRequest request) {
		return RunResponse.from(runService.updateStatus(id, request));
	}

	@PostMapping("/{id}/steps")
	public StepResponse recordStep(@PathVariable UUID id, @Valid @RequestBody InternalStepRequest request) {
		return StepResponse.from(runService.recordStep(id, request));
	}

	@PostMapping("/{id}/logs")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void ingestLogs(@PathVariable UUID id, @Valid @RequestBody InternalLogsRequest request) {
		runService.ingestLogs(id, request);
	}

	@PostMapping("/{id}/changes")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void recordChanges(@PathVariable UUID id, @Valid @RequestBody InternalChangesRequest request) {
		runService.recordChanges(id, request);
	}

	@GetMapping("/{id}/context")
	public RunContextResponse getContext(@PathVariable UUID id) {
		return runService.getContext(id);
	}

	/** Phase 11 (Section 8.3): terminal runs older than maxAgeDays with no OPEN PR, for the orchestrator's retention sweep. */
	@GetMapping("/retention-candidates")
	public RetentionCandidatesResponse retentionCandidates(@RequestParam(defaultValue = "7") int maxAgeDays) {
		return runService.getRetentionCandidates(maxAgeDays);
	}

	/** Section 8.4's push credential flow: guards run.status == APPROVED (Phase 9's "unapproved run cannot push" test). */
	@GetMapping("/{id}/git-token")
	public GitTokenResponse gitToken(@PathVariable UUID id) {
		AgentRun run = runService.getOrThrow(id);
		return GitTokenResponse.from(gitCredentialsService.resolveForRun(run));
	}

	/** Called by the orchestrator once the runner supervisor has pushed {@code branchName}; opens the PR and completes the run. */
	@PostMapping("/{id}/pull-request")
	public PullRequestResponse createPullRequest(@PathVariable UUID id,
			@Valid @RequestBody InternalPullRequestRequest request) {
		AgentRun run = runService.getOrThrow(id);
		return PullRequestResponse.from(pullRequestService.createForRun(run, request.branchName(), request.commitSha()));
	}

	/** Phase 16 (Section 10.4's reserved contract line): called directly by the runner supervisor
	 * (not relayed through the orchestrator) since it already holds the service token for a
	 * possible direct runner -> talos-api call (Appendix A). */
	@PostMapping(value = "/{id}/artifacts", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@ResponseStatus(HttpStatus.CREATED)
	public RunArtifactResponse uploadArtifact(@PathVariable UUID id, @RequestParam ArtifactKind kind,
			@RequestParam String name, @RequestParam("file") MultipartFile file) throws IOException {
		String contentType = file.getContentType() != null ? file.getContentType() : MediaType.APPLICATION_OCTET_STREAM_VALUE;
		return RunArtifactResponse
				.from(artifactService.store(id, kind, name, file.getInputStream(), file.getSize(), contentType));
	}

	/** Phase 11's retention sweep, extended (Phase 16): called once the runner supervisor has deleted the workspace for {@code id}. */
	@DeleteMapping("/{id}/artifacts")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deleteArtifacts(@PathVariable UUID id) {
		artifactService.deleteForRun(id);
	}
}
