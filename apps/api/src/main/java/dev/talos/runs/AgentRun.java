package dev.talos.runs;

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
@Table(name = "agent_runs")
public class AgentRun {

	@Id
	private UUID id = UuidV7.generate();

	@Column(name = "task_id", nullable = false)
	private UUID taskId;

	@Column(name = "project_id", nullable = false)
	private UUID projectId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private RunStatus status = RunStatus.CREATED;

	@Column(name = "agent_key", nullable = false, length = 50)
	private String agentKey;

	@Column(name = "provider_auth_mode", nullable = false, length = 30)
	private String providerAuthMode = "api_key";

	@Column
	private String prompt;

	@Column(name = "branch_name", length = 300)
	private String branchName;

	@Column(name = "workspace_path", length = 500)
	private String workspacePath;

	@Column
	private String summary;

	@Enumerated(EnumType.STRING)
	@Column(name = "test_status", nullable = false, length = 20)
	private TestStatus testStatus = TestStatus.NOT_RUN;

	@Enumerated(EnumType.STRING)
	@Column(name = "review_status", nullable = false, length = 20)
	private ReviewStatus reviewStatus = ReviewStatus.CLEAN;

	@Column(name = "error_message")
	private String errorMessage;

	@Column(name = "exit_code")
	private Integer exitCode;

	@Column(name = "timeout_at")
	private Instant timeoutAt;

	@Column(name = "started_at")
	private Instant startedAt;

	@Column(name = "completed_at")
	private Instant completedAt;

	@Column(name = "created_at", nullable = false, insertable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
	private Instant updatedAt;

	protected AgentRun() {
		// JPA
	}

	public AgentRun(UUID taskId, UUID projectId, String agentKey, String providerAuthMode) {
		this.taskId = taskId;
		this.projectId = projectId;
		this.agentKey = agentKey;
		this.providerAuthMode = providerAuthMode;
	}

	public UUID getId() {
		return id;
	}

	public UUID getTaskId() {
		return taskId;
	}

	public UUID getProjectId() {
		return projectId;
	}

	public RunStatus getStatus() {
		return status;
	}

	public String getAgentKey() {
		return agentKey;
	}

	public String getProviderAuthMode() {
		return providerAuthMode;
	}

	public String getPrompt() {
		return prompt;
	}

	public String getBranchName() {
		return branchName;
	}

	public String getWorkspacePath() {
		return workspacePath;
	}

	public String getSummary() {
		return summary;
	}

	public TestStatus getTestStatus() {
		return testStatus;
	}

	public ReviewStatus getReviewStatus() {
		return reviewStatus;
	}

	public String getErrorMessage() {
		return errorMessage;
	}

	public Integer getExitCode() {
		return exitCode;
	}

	public Instant getTimeoutAt() {
		return timeoutAt;
	}

	public Instant getStartedAt() {
		return startedAt;
	}

	public Instant getCompletedAt() {
		return completedAt;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}
}
