// SPDX-FileCopyrightText: 2026 Vulkan Technologies
// SPDX-License-Identifier: AGPL-3.0-or-later

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
@Table(name = "agent_run_steps")
public class AgentRunStep {

	@Id
	private UUID id = UuidV7.generate();

	@Column(name = "run_id", nullable = false)
	private UUID runId;

	@Enumerated(EnumType.STRING)
	@Column(name = "step_type", nullable = false, length = 20)
	private StepType stepType;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private StepStatus status;

	@Column
	private String summary;

	@Column(name = "started_at")
	private Instant startedAt;

	@Column(name = "completed_at")
	private Instant completedAt;

	protected AgentRunStep() {
		// JPA
	}

	public AgentRunStep(UUID runId, StepType stepType, StepStatus status) {
		this.runId = runId;
		this.stepType = stepType;
		this.status = status;
		this.startedAt = Instant.now();
		if (status != StepStatus.RUNNING) {
			this.completedAt = Instant.now();
		}
	}

	public UUID getId() {
		return id;
	}

	public UUID getRunId() {
		return runId;
	}

	public StepType getStepType() {
		return stepType;
	}

	public StepStatus getStatus() {
		return status;
	}

	public String getSummary() {
		return summary;
	}

	public Instant getStartedAt() {
		return startedAt;
	}

	public Instant getCompletedAt() {
		return completedAt;
	}

	public void complete(StepStatus status, String summary) {
		this.status = status;
		this.summary = summary;
		this.completedAt = Instant.now();
	}
}
