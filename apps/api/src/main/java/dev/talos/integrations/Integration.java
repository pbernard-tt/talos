package dev.talos.integrations;

import dev.talos.common.UuidV7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Non-secret integration settings (Section 12.2: secrets live only in dev.talos.secrets, referenced by secret_ref). */
@Entity
@Table(name = "integrations")
public class Integration {

	@Id
	private UUID id = UuidV7.generate();

	@Column(nullable = false, length = 30)
	private String type;

	@Column(nullable = false, length = 100)
	private String name;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "config_json", nullable = false)
	private Map<String, Object> configJson = Map.of();

	@Column(nullable = false)
	private boolean enabled = true;

	@Column(name = "created_at", nullable = false, insertable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
	private Instant updatedAt;

	protected Integration() {
		// JPA
	}

	public Integration(String type, String name, Map<String, Object> configJson) {
		this.type = type;
		this.name = name;
		this.configJson = configJson != null ? configJson : Map.of();
	}

	public UUID getId() {
		return id;
	}

	public String getType() {
		return type;
	}

	public String getName() {
		return name;
	}

	public Map<String, Object> getConfigJson() {
		return configJson;
	}

	public boolean isEnabled() {
		return enabled;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}
}
