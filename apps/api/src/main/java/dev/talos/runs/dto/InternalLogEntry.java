package dev.talos.runs.dto;

import dev.talos.runs.LogStream;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record InternalLogEntry(@NotNull LogStream stream, long sequence, @NotNull String message,
		Instant timestamp) {
}
