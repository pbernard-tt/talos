// SPDX-FileCopyrightText: 2026 Vulkan Technologies
// SPDX-License-Identifier: AGPL-3.0-or-later

package dev.talos.memory;

import java.util.UUID;

public record MemorySearchResult(UUID chunkId, UUID documentId, MemorySourceType sourceType,
		String sourceRef, String title, String content, double score) {
}
