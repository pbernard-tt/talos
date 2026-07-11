package dev.talos.runs;

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

import java.math.BigDecimal;
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

	@Column(name = "diff_patch")
	private String diffPatch;

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

	/** Phase 14 (Section 16): normalized usage metadata, adapter-reported when available. Null when
	 * the adapter can't report it (e.g. CustomShellAdapter, or a coding agent whose usage schema
	 * isn't yet parsed) -- callers must degrade gracefully rather than treat this as an error. */
	@Column(name = "input_tokens")
	private Integer inputTokens;

	@Column(name = "output_tokens")
	private Integer outputTokens;

	/** Never set for a subscription_local run even if the adapter reported one (Section 13: never
	 * estimate/attribute a price to subscription usage) -- enforced in RunService, not here. */
	@Column(name = "cost_usd")
	private BigDecimal costUsd;

	@Column(name = "cost_model")
	private String costModel;

	@Column(name = "timeout_at")
	private Instant timeoutAt;

	@Column(name = "started_at")
	private Instant startedAt;

	@Column(name = "completed_at")
	private Instant completedAt;

	@Generated(event = EventType.INSERT)
	@Column(name = "created_at", nullable = false, insertable = false, updatable = false)
	private Instant createdAt;

	@Generated(event = EventType.INSERT)
	@Column(name = "updated_at", nullable = false, insertable = false)
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

	public String getDiffPatch() {
		return diffPatch;
	}

	public void setDiffPatch(String diffPatch) {
		this.diffPatch = diffPatch;
	}

	public void setReviewStatus(ReviewStatus reviewStatus) {
		this.reviewStatus = reviewStatus;
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

	public Integer getInputTokens() {
		return inputTokens;
	}

	public Integer getOutputTokens() {
		return outputTokens;
	}

	public BigDecimal getCostUsd() {
		return costUsd;
	}

	public String getCostModel() {
		return costModel;
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

	/** Sets the given transition's timeout deadline. startedAt is stamped once, on first entering RUNNING_AGENT; completedAt on reaching any terminal status. */
	public void transitionTo(RunStatus newStatus, Instant timeoutAt, String errorMessage) {
		this.status = newStatus;
		this.timeoutAt = timeoutAt;
		if (errorMessage != null) {
			this.errorMessage = errorMessage;
		}
		if (newStatus == RunStatus.RUNNING_AGENT && this.startedAt == null) {
			this.startedAt = Instant.now();
		}
		if (isTerminal(newStatus)) {
			this.completedAt = Instant.now();
		}
	}

	private static boolean isTerminal(RunStatus status) {
		return status == RunStatus.COMPLETED || status == RunStatus.FAILED || status == RunStatus.CANCELLED
				|| status == RunStatus.REJECTED;
	}

	/** Phase 6: the pipeline supplies whichever of these it has just produced alongside a status
	 * transition (see InternalStatusRequest). Phase 14 adds usage/cost: {@code costUsd} is the
	 * caller's already-policy-adjusted value (RunService nulls it out for subscription_local runs
	 * before calling this) so this entity stays a plain field-merge with no auth-mode knowledge. */
	public void applyPipelineDetails(TestStatus testStatus, String workspacePath, String branchName, String prompt,
			String summary, Integer exitCode, Integer inputTokens, Integer outputTokens, BigDecimal costUsd,
			String costModel) {
		if (testStatus != null) {
			this.testStatus = testStatus;
		}
		if (workspacePath != null) {
			this.workspacePath = workspacePath;
		}
		if (branchName != null) {
			this.branchName = branchName;
		}
		if (prompt != null) {
			this.prompt = prompt;
		}
		if (summary != null) {
			this.summary = summary;
		}
		if (exitCode != null) {
			this.exitCode = exitCode;
		}
		if (inputTokens != null) {
			this.inputTokens = inputTokens;
		}
		if (outputTokens != null) {
			this.outputTokens = outputTokens;
		}
		if (costUsd != null) {
			this.costUsd = costUsd;
		}
		if (costModel != null) {
			this.costModel = costModel;
		}
	}

	@PreUpdate
	void onUpdate() {
		this.updatedAt = Instant.now();
	}
}
