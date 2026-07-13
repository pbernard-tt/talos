// SPDX-FileCopyrightText: 2026 Vulkan Technologies
// SPDX-License-Identifier: AGPL-3.0-or-later

package dev.talos.memory;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface MemoryDocumentRepository extends JpaRepository<MemoryDocument, UUID> {
	Optional<MemoryDocument> findByProjectIdAndSourceTypeAndSourceRefAndContentHash(UUID projectId,
			MemorySourceType sourceType, String sourceRef, String contentHash);
}
