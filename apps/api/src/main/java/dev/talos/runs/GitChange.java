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

import java.util.UUID;

@Entity
@Table(name = "git_changes")
public class GitChange {

	@Id
	private UUID id = UuidV7.generate();

	@Column(name = "run_id", nullable = false)
	private UUID runId;

	@Column(name = "file_path", nullable = false, length = 1000)
	private String filePath;

	@Enumerated(EnumType.STRING)
	@Column(name = "change_type", nullable = false, length = 10)
	private GitChangeType changeType;

	@Column(nullable = false)
	private int additions = 0;

	@Column(nullable = false)
	private int deletions = 0;

	@Column(name = "risk_flagged", nullable = false)
	private boolean riskFlagged = false;

	@Column(name = "matched_pattern", length = 200)
	private String matchedPattern;

	protected GitChange() {
		// JPA
	}

	public GitChange(UUID runId, String filePath, GitChangeType changeType, int additions, int deletions,
			boolean riskFlagged) {
		this.runId = runId;
		this.filePath = filePath;
		this.changeType = changeType;
		this.additions = additions;
		this.deletions = deletions;
		this.riskFlagged = riskFlagged;
	}

	public UUID getId() {
		return id;
	}

	public UUID getRunId() {
		return runId;
	}

	public String getFilePath() {
		return filePath;
	}

	public GitChangeType getChangeType() {
		return changeType;
	}

	public int getAdditions() {
		return additions;
	}

	public int getDeletions() {
		return deletions;
	}

	public boolean isRiskFlagged() {
		return riskFlagged;
	}

	public String getMatchedPattern() {
		return matchedPattern;
	}

	/** Section 12.1's post-run scan (Phase 8): flags this row and records which policy.yaml/talos.yaml pattern matched. */
	public void flag(String matchedPattern) {
		this.riskFlagged = true;
		this.matchedPattern = matchedPattern;
	}
}
