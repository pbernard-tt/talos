package dev.talos.runs;

import dev.talos.runs.dto.InternalLogsRequest;
import dev.talos.runs.dto.InternalStatusRequest;
import dev.talos.runs.dto.InternalStepRequest;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Section 10.4: service-token-authenticated, called only by the orchestrator/runner supervisor. */
@RestController
@RequestMapping("/internal/v1/runs")
public class InternalRunController {

	private final RunService runService;

	public InternalRunController(RunService runService) {
		this.runService = runService;
	}

	@PostMapping("/{id}/status")
	public RunResponse updateStatus(@PathVariable UUID id, @Valid @RequestBody InternalStatusRequest request) {
		return RunResponse.from(runService.updateStatus(id, request.status(), request.errorMessage()));
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

	@GetMapping("/{id}/context")
	public RunContextResponse getContext(@PathVariable UUID id) {
		return runService.getContext(id);
	}
}
