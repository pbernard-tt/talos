package dev.talos.tasks.dto;

import dev.talos.tasks.TaskStatus;
import jakarta.validation.constraints.NotNull;

public record MoveTaskRequest(@NotNull TaskStatus status, @NotNull Integer boardPosition) {
}
