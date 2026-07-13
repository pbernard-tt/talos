// SPDX-FileCopyrightText: 2026 Vulkan Technologies
// SPDX-License-Identifier: AGPL-3.0-or-later

package dev.talos.runs.dto;

import dev.talos.integrations.PullRequest;

import java.time.Instant;
import java.util.UUID;

public record PullRequestResponse(UUID id, UUID runId, String provider, Integer prNumber, String url, String status,
		Instant createdAt) {

	public static PullRequestResponse from(PullRequest pullRequest) {
		return new PullRequestResponse(pullRequest.getId(), pullRequest.getRunId(), pullRequest.getProvider(),
				pullRequest.getPrNumber(), pullRequest.getUrl(), pullRequest.getStatus().name(),
				pullRequest.getCreatedAt());
	}
}
