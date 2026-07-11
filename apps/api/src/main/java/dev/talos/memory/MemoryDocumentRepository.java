package dev.talos.memory;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface MemoryDocumentRepository extends JpaRepository<MemoryDocument, UUID> {
	Optional<MemoryDocument> findByProjectIdAndSourceTypeAndSourceRefAndContentHash(UUID projectId,
			MemorySourceType sourceType, String sourceRef, String contentHash);
}
