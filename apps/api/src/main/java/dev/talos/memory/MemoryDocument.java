package dev.talos.memory;

import dev.talos.common.UuidV7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "memory_documents", uniqueConstraints = @UniqueConstraint(columnNames = {
		"project_id", "source_type", "source_ref", "content_hash"
}))
public class MemoryDocument {

	@Id
	private UUID id = UuidV7.generate();

	@Column(name = "project_id", nullable = false)
	private UUID projectId;

	@Enumerated(EnumType.STRING)
	@Column(name = "source_type", nullable = false, length = 30)
	private MemorySourceType sourceType;

	@Column(name = "source_ref", nullable = false, length = 500)
	private String sourceRef;

	@Column(nullable = false, length = 300)
	private String title;

	@Column(nullable = false)
	private String content;

	@Column(name = "content_hash", nullable = false, length = 64)
	private String contentHash;

	@Generated(event = EventType.INSERT)
	@Column(name = "created_at", nullable = false, insertable = false, updatable = false)
	private Instant createdAt;

	protected MemoryDocument() {
		// JPA
	}

	public MemoryDocument(UUID projectId, MemorySourceType sourceType, String sourceRef, String title,
			String content, String contentHash) {
		this.projectId = projectId;
		this.sourceType = sourceType;
		this.sourceRef = sourceRef;
		this.title = title;
		this.content = content;
		this.contentHash = contentHash;
	}

	public UUID getId() {
		return id;
	}

	public UUID getProjectId() {
		return projectId;
	}

	public MemorySourceType getSourceType() {
		return sourceType;
	}

	public String getSourceRef() {
		return sourceRef;
	}

	public String getTitle() {
		return title;
	}

	public String getContent() {
		return content;
	}

	public String getContentHash() {
		return contentHash;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
