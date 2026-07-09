package dev.talos.runs.dto;

import dev.talos.runs.RunStatus;
import jakarta.validation.constraints.NotNull;

public record InternalStatusRequest(@NotNull RunStatus status, String errorMessage) {
}
