package dev.talos.artifacts;

import dev.talos.common.UuidV7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "run_artifacts")
public class RunArtifact {

	@Id
	private UUID id = UuidV7.generate();

	@Column(name = "run_id", nullable = false)
	private UUID runId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private ArtifactKind kind;

	@Column(nullable = false)
	private String name;

	@Column(name = "storage_key", nullable = false, length = 500)
	private String storageKey;

	@Column(name = "content_type", nullable = false, length = 100)
	private String contentType;

	@Column(name = "size_bytes", nullable = false)
	private long sizeBytes;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt = Instant.now();

	protected RunArtifact() {
		// JPA
	}

	public RunArtifact(UUID runId, ArtifactKind kind, String name, String storageKey, String contentType,
			long sizeBytes) {
		this.runId = runId;
		this.kind = kind;
		this.name = name;
		this.storageKey = storageKey;
		this.contentType = contentType;
		this.sizeBytes = sizeBytes;
	}

	public UUID getId() {
		return id;
	}

	public UUID getRunId() {
		return runId;
	}

	public ArtifactKind getKind() {
		return kind;
	}

	public String getName() {
		return name;
	}

	public String getStorageKey() {
		return storageKey;
	}

	public String getContentType() {
		return contentType;
	}

	public long getSizeBytes() {
		return sizeBytes;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
