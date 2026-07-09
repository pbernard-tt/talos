package dev.talos.runs.dto;

import dev.talos.runs.StepStatus;
import dev.talos.runs.StepType;
import jakarta.validation.constraints.NotNull;

public record InternalStepRequest(@NotNull StepType stepType, @NotNull StepStatus status, String summary) {
}
