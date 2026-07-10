package dev.talos.integrations.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.Map;

/** Section 10.2: {type, name, configJson, secret?}. authMode mirrors integration_credentials.auth_mode (Section 9.2). */
public record IntegrationCreateRequest(@NotBlank String type, @NotBlank String name, Map<String, Object> configJson,
		String secret, String authMode) {
}
