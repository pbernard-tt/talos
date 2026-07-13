// SPDX-FileCopyrightText: 2026 Vulkan Technologies
// SPDX-License-Identifier: AGPL-3.0-or-later

package dev.talos.memory.dto;

import dev.talos.memory.MemorySearchResult;
import dev.talos.memory.MemorySourceType;

import java.util.UUID;

public record MemorySearchResultResponse(UUID chunkId, UUID documentId, MemorySourceType sourceType,
		String sourceRef, String title, String content, double score) {

	public static MemorySearchResultResponse from(MemorySearchResult result) {
		return new MemorySearchResultResponse(result.chunkId(), result.documentId(), result.sourceType(),
				result.sourceRef(), result.title(), result.content(), result.score());
	}
}
