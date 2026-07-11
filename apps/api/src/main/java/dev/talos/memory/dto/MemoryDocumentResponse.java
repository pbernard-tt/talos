package dev.talos.memory.dto;

import dev.talos.memory.MemoryDocument;
import dev.talos.memory.MemorySourceType;

import java.time.Instant;
import java.util.UUID;

public record MemoryDocumentResponse(UUID id, UUID projectId, MemorySourceType sourceType, String sourceRef,
		String title, Instant createdAt) {

	public static MemoryDocumentResponse from(MemoryDocument document) {
		return new MemoryDocumentResponse(document.getId(), document.getProjectId(), document.getSourceType(),
				document.getSourceRef(), document.getTitle(), document.getCreatedAt());
	}
}
