package dev.talos.projects;

import dev.talos.common.UuidV7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** A versioned snapshot of a project's talos.yaml. Application invariant: at most one is_active=true row per project. */
@Entity
@Table(name = "project_configs", uniqueConstraints = @UniqueConstraint(columnNames = { "project_id", "version" }))
public class ProjectConfig {

	@Id
	private UUID id = UuidV7.generate();

	@Column(name = "project_id", nullable = false)
	private UUID projectId;

	@Lob
	@Column(name = "config_yaml", nullable = false)
	private String configYaml;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "parsed_json", nullable = false)
	private Map<String, Object> parsedJson;

	@Column(nullable = false)
	private int version;

	@Column(name = "is_active", nullable = false)
	private boolean active = true;

	@Column(name = "created_at", nullable = false, insertable = false, updatable = false)
	private Instant createdAt;

	protected ProjectConfig() {
		// JPA
	}

	public ProjectConfig(UUID projectId, String configYaml, Map<String, Object> parsedJson, int version) {
		this.projectId = projectId;
		this.configYaml = configYaml;
		this.parsedJson = parsedJson;
		this.version = version;
	}

	public UUID getId() {
		return id;
	}

	public UUID getProjectId() {
		return projectId;
	}

	public String getConfigYaml() {
		return configYaml;
	}

	public Map<String, Object> getParsedJson() {
		return parsedJson;
	}

	public int getVersion() {
		return version;
	}

	public boolean isActive() {
		return active;
	}

	public void setActive(boolean active) {
		this.active = active;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
