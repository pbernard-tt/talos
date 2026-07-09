package dev.talos.runs;

import dev.talos.common.UuidV7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "agent_run_logs", uniqueConstraints = @UniqueConstraint(columnNames = { "run_id", "sequence" }))
public class AgentRunLog {

	@Id
	private UUID id = UuidV7.generate();

	@Column(name = "run_id", nullable = false)
	private UUID runId;

	@Column(name = "step_id")
	private UUID stepId;

	@Column(nullable = false)
	private long sequence;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private LogStream stream;

	@Column(nullable = false)
	private String message;

	@Column(name = "created_at", nullable = false, insertable = false, updatable = false)
	private Instant createdAt;

	protected AgentRunLog() {
		// JPA
	}

	public AgentRunLog(UUID runId, UUID stepId, long sequence, LogStream stream, String message) {
		this.runId = runId;
		this.stepId = stepId;
		this.sequence = sequence;
		this.stream = stream;
		this.message = message;
	}

	public UUID getId() {
		return id;
	}

	public UUID getRunId() {
		return runId;
	}

	public UUID getStepId() {
		return stepId;
	}

	public long getSequence() {
		return sequence;
	}

	public LogStream getStream() {
		return stream;
	}

	public String getMessage() {
		return message;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
