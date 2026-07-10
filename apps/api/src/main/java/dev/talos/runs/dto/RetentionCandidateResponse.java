package dev.talos.runs.dto;

import java.util.UUID;

public record RetentionCandidateResponse(UUID runId, String projectSlug) {
}
