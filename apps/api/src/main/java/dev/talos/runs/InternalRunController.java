package dev.talos.runs;

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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Section 10.4: service-token-authenticated, called only by the orchestrator/runner supervisor. */
@RestController
@RequestMapping("/internal/v1/runs")
public class InternalRunController {

	private final RunService runService;
	private final GitCredentialsService gitCredentialsService;
	private final PullRequestService pullRequestService;

	public InternalRunController(RunService runService, GitCredentialsService gitCredentialsService,
			PullRequestService pullRequestService) {
		this.runService = runService;
		this.gitCredentialsService = gitCredentialsService;
		this.pullRequestService = pullRequestService;
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
}
