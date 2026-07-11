package dev.talos.memory;

import java.util.UUID;

public record MemorySearchResult(UUID chunkId, UUID documentId, MemorySourceType sourceType,
		String sourceRef, String title, String content, double score) {
}
