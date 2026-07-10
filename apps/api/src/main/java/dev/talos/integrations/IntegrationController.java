package dev.talos.integrations;

import dev.talos.integrations.dto.IntegrationCreateRequest;
import dev.talos.integrations.dto.IntegrationResponse;
import dev.talos.integrations.dto.TestIntegrationResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Section 10.2: configure GitHub/Dokploy/provider credentials. */
@RestController
@RequestMapping("/api/v1/integrations")
public class IntegrationController {

	private final IntegrationService integrationService;

	public IntegrationController(IntegrationService integrationService) {
		this.integrationService = integrationService;
	}

	@GetMapping
	public List<IntegrationResponse> list() {
		return integrationService.list().stream().map(IntegrationResponse::from).toList();
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public IntegrationResponse create(@Valid @RequestBody IntegrationCreateRequest request) {
		return IntegrationResponse.from(integrationService.create(request.type(), request.name(),
				request.configJson(), request.secret(), request.authMode()));
	}

	@PostMapping("/{id}/test")
	public TestIntegrationResponse test(@PathVariable UUID id) {
		return integrationService.test(id);
	}
}
