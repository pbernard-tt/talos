package dev.talos.runs.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record InternalLogsRequest(@NotEmpty @Valid List<InternalLogEntry> entries) {
}
