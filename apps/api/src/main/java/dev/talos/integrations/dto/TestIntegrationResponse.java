package dev.talos.integrations.dto;

/** Section 10.2: POST /api/v1/integrations/{id}/test -> {ok, message}. */
public record TestIntegrationResponse(boolean ok, String message) {
}
