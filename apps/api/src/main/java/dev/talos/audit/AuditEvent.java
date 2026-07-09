package dev.talos.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.generator.EventType;
import org.hibernate.type.SqlTypes;
import dev.talos.common.UuidV7;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "audit_events")
public class AuditEvent {

	@Id
	private UUID id = UuidV7.generate();

	@Column(name = "actor_user_id")
	private UUID actorUserId;

	@Column(name = "event_type", nullable = false, length = 100)
	private String eventType;

	@Column(name = "entity_type", nullable = false, length = 50)
	private String entityType;

	@Column(name = "entity_id")
	private UUID entityId;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "details_json", nullable = false)
	private Map<String, Object> detailsJson = Map.of();

	@Generated(event = EventType.INSERT)
	@Column(name = "created_at", nullable = false, insertable = false, updatable = false)
	private Instant createdAt;

	protected AuditEvent() {
		// JPA
	}

	public AuditEvent(UUID actorUserId, String eventType, String entityType, UUID entityId,
			Map<String, Object> detailsJson) {
		this.actorUserId = actorUserId;
		this.eventType = eventType;
		this.entityType = entityType;
		this.entityId = entityId;
		this.detailsJson = detailsJson != null ? detailsJson : Map.of();
	}

	public UUID getId() {
		return id;
	}

	public UUID getActorUserId() {
		return actorUserId;
	}

	public String getEventType() {
		return eventType;
	}

	public String getEntityType() {
		return entityType;
	}

	public UUID getEntityId() {
		return entityId;
	}

	public Map<String, Object> getDetailsJson() {
		return detailsJson;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
