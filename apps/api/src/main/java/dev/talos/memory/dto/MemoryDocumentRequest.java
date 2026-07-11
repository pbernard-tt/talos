package dev.talos.memory.dto;

import dev.talos.memory.MemorySourceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record MemoryDocumentRequest(
		@NotNull MemorySourceType sourceType,
		String sourceRef,
		String title,
		@NotBlank String content) {
}
