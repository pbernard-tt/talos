package dev.talos.integrations.dto;

import dev.talos.integrations.Integration;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Section 10.2: "secrets never returned" -- deliberately has no secret/token field. */
public record IntegrationResponse(UUID id, String type, String name, Map<String, Object> configJson,
		boolean enabled, Instant createdAt) {

	public static IntegrationResponse from(Integration integration) {
		return new IntegrationResponse(integration.getId(), integration.getType(), integration.getName(),
				integration.getConfigJson(), integration.isEnabled(), integration.getCreatedAt());
	}
}
