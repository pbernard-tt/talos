package dev.talos.integrations;

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

/** Phase 10 (Section 9.4 deferred this here): a project's deploy target, synced from talos.yaml's deploy: block. */
@Entity
@Table(name = "project_environments")
public class ProjectEnvironment {

	@Id
	private UUID id = UuidV7.generate();

	@Column(name = "project_id", nullable = false)
	private UUID projectId;

	@Column(nullable = false, length = 50)
	private String environment;

	@Column(nullable = false, length = 30)
	private String provider = "dokploy";

	@Column(name = "app_id", nullable = false, length = 200)
	private String appId;

	@Column(name = "approval_required", nullable = false)
	private boolean approvalRequired = true;

	@Enumerated(EnumType.STRING)
	@Column(name = "last_deploy_status", length = 20)
	private DeployStatus lastDeployStatus;

	@Column(name = "last_deployed_at")
	private Instant lastDeployedAt;

	@Column(name = "last_run_id")
	private UUID lastRunId;

	@Generated(event = EventType.INSERT)
	@Column(name = "created_at", nullable = false, insertable = false, updatable = false)
	private Instant createdAt;

	@Generated(event = EventType.INSERT)
	@Column(name = "updated_at", nullable = false, insertable = false)
	private Instant updatedAt;

	protected ProjectEnvironment() {
		// JPA
	}

	public ProjectEnvironment(UUID projectId, String environment, String provider, String appId,
			boolean approvalRequired) {
		this.projectId = projectId;
		this.environment = environment;
		this.provider = provider;
		this.appId = appId;
		this.approvalRequired = approvalRequired;
	}

	public UUID getId() {
		return id;
	}

	public UUID getProjectId() {
		return projectId;
	}

	public String getEnvironment() {
		return environment;
	}

	public String getProvider() {
		return provider;
	}

	public String getAppId() {
		return appId;
	}

	public boolean isApprovalRequired() {
		return approvalRequired;
	}

	public DeployStatus getLastDeployStatus() {
		return lastDeployStatus;
	}

	public Instant getLastDeployedAt() {
		return lastDeployedAt;
	}

	public UUID getLastRunId() {
		return lastRunId;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	/** Re-syncs the deploy target from the project's current talos.yaml deploy: block (Phase 10). */
	public void syncFromConfig(String provider, String appId, boolean approvalRequired) {
		this.provider = provider;
		this.appId = appId;
		this.approvalRequired = approvalRequired;
	}

	/** Called when DeployService.triggerNow fires the provider call. */
	public void markTriggered(UUID runId) {
		this.lastDeployStatus = DeployStatus.RUNNING;
		this.lastRunId = runId;
	}

	/** Called by DeployStatusPoller once a deployment reaches a terminal Dokploy state. */
	public void markTerminal(DeployStatus status) {
		this.lastDeployStatus = status;
		this.lastDeployedAt = Instant.now();
	}

	@PreUpdate
	void onUpdate() {
		this.updatedAt = Instant.now();
	}
}
