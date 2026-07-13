// SPDX-FileCopyrightText: 2026 Vulkan Technologies
// SPDX-License-Identifier: AGPL-3.0-or-later

package dev.talos.integrations;

import dev.talos.common.UuidV7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "pull_requests")
public class PullRequest {

	@Id
	private UUID id = UuidV7.generate();

	@Column(name = "run_id", nullable = false)
	private UUID runId;

	@Column(nullable = false, length = 20)
	private String provider = "github";

	@Column(name = "pr_number")
	private Integer prNumber;

	@Column(length = 500)
	private String url;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private PullRequestStatus status = PullRequestStatus.OPEN;

	@Generated(event = EventType.INSERT)
	@Column(name = "created_at", nullable = false, insertable = false, updatable = false)
	private Instant createdAt;

	protected PullRequest() {
		// JPA
	}

	public PullRequest(UUID runId, String provider, Integer prNumber, String url) {
		this.runId = runId;
		this.provider = provider;
		this.prNumber = prNumber;
		this.url = url;
	}

	public UUID getId() {
		return id;
	}

	public UUID getRunId() {
		return runId;
	}

	public String getProvider() {
		return provider;
	}

	public Integer getPrNumber() {
		return prNumber;
	}

	public String getUrl() {
		return url;
	}

	public PullRequestStatus getStatus() {
		return status;
	}

	public void setStatus(PullRequestStatus status) {
		this.status = status;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
