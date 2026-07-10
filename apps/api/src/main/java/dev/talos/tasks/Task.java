package dev.talos.tasks;

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
@Table(name = "tasks")
public class Task {

	@Id
	private UUID id = UuidV7.generate();

	@Column(name = "project_id", nullable = false)
	private UUID projectId;

	@Column(nullable = false, length = 300)
	private String title;

	@Column
	private String description;

	@Column(nullable = false, length = 30)
	private String source = "DASHBOARD";

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private TaskStatus status = TaskStatus.BACKLOG;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private TaskPriority priority = TaskPriority.MEDIUM;

	@Enumerated(EnumType.STRING)
	@Column(name = "risk_level", nullable = false, length = 10)
	private TaskRiskLevel riskLevel = TaskRiskLevel.NORMAL;

	@Column(name = "board_position", nullable = false)
	private int boardPosition = 0;

	@Column(name = "requested_by")
	private UUID requestedBy;

	@Column(name = "assigned_agent_key", length = 50)
	private String assignedAgentKey;

	@Generated(event = EventType.INSERT)
	@Column(name = "created_at", nullable = false, insertable = false, updatable = false)
	private Instant createdAt;

	@Generated(event = EventType.INSERT)
	@Column(name = "updated_at", nullable = false, insertable = false)
	private Instant updatedAt;

	protected Task() {
		// JPA
	}

	public Task(UUID projectId, String title, String description, UUID requestedBy) {
		this(projectId, title, description, requestedBy, "DASHBOARD");
	}

	public Task(UUID projectId, String title, String description, UUID requestedBy, String source) {
		this.projectId = projectId;
		this.title = title;
		this.description = description;
		this.requestedBy = requestedBy;
		this.source = source;
	}

	public UUID getId() {
		return id;
	}

	public UUID getProjectId() {
		return projectId;
	}

	public String getTitle() {
		return title;
	}

	public String getDescription() {
		return description;
	}

	public String getSource() {
		return source;
	}

	public TaskStatus getStatus() {
		return status;
	}

	public TaskPriority getPriority() {
		return priority;
	}

	public TaskRiskLevel getRiskLevel() {
		return riskLevel;
	}

	public int getBoardPosition() {
		return boardPosition;
	}

	public UUID getRequestedBy() {
		return requestedBy;
	}

	public String getAssignedAgentKey() {
		return assignedAgentKey;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	public void updatePartial(String title, String description, TaskPriority priority, TaskRiskLevel riskLevel,
			String assignedAgentKey) {
		if (title != null) {
			this.title = title;
		}
		if (description != null) {
			this.description = description;
		}
		if (priority != null) {
			this.priority = priority;
		}
		if (riskLevel != null) {
			this.riskLevel = riskLevel;
		}
		if (assignedAgentKey != null) {
			this.assignedAgentKey = assignedAgentKey;
		}
	}

	public void move(TaskStatus status, int boardPosition) {
		this.status = status;
		this.boardPosition = boardPosition;
	}

	@PreUpdate
	void onUpdate() {
		this.updatedAt = Instant.now();
	}
}
