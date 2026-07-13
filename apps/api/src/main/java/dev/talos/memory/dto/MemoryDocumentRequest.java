// SPDX-FileCopyrightText: 2026 Vulkan Technologies
// SPDX-License-Identifier: AGPL-3.0-or-later

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
