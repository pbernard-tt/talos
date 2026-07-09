package dev.talos.projects;

import dev.talos.common.UuidV7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "projects")
public class Project {

	@Id
	private UUID id = UuidV7.generate();

	@Column(nullable = false, length = 200)
	private String name;

	@Column(nullable = false, unique = true, length = 100)
	private String slug;

	@Column(name = "repo_url", nullable = false, length = 500)
	private String repoUrl;

	@Column(name = "default_branch", nullable = false, length = 200)
	private String defaultBranch;

	@Column(name = "stack_type", nullable = false, length = 50)
	private String stackType;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private ProjectStatus status = ProjectStatus.ACTIVE;

	@Generated(event = EventType.INSERT)
	@Column(name = "created_at", nullable = false, insertable = false, updatable = false)
	private Instant createdAt;

	@Generated(event = EventType.INSERT)
	@Column(name = "updated_at", nullable = false, insertable = false)
	private Instant updatedAt;

	protected Project() {
		// JPA
	}

	public Project(String name, String slug, String repoUrl, String defaultBranch, String stackType) {
		this.name = name;
		this.slug = slug;
		this.repoUrl = repoUrl;
		this.defaultBranch = defaultBranch;
		this.stackType = stackType;
	}

	public UUID getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public String getSlug() {
		return slug;
	}

	public String getRepoUrl() {
		return repoUrl;
	}

	public String getDefaultBranch() {
		return defaultBranch;
	}

	public String getStackType() {
		return stackType;
	}

	public ProjectStatus getStatus() {
		return status;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	public void update(String name, String repoUrl, String defaultBranch, String stackType, ProjectStatus status) {
		this.name = name;
		this.repoUrl = repoUrl;
		this.defaultBranch = defaultBranch;
		this.stackType = stackType;
		if (status != null) {
			this.status = status;
		}
	}

	@PreUpdate
	void onUpdate() {
		this.updatedAt = Instant.now();
	}
}
