package dev.talos.runs.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

public record InternalChangesRequest(@NotNull List<GitChangeDto> files, String diffArtifactRef) {
}
