// SPDX-FileCopyrightText: 2026 Vulkan Technologies
// SPDX-License-Identifier: AGPL-3.0-or-later

package dev.talos.costs.dto;

import dev.talos.costs.MonthlyCostProjection;

import java.math.BigDecimal;

/** Section 16 Phase 14: GET /api/v1/projects/{id}/costs/monthly response row -- one per
 * (agentKey, month) with at least one terminal run. */
public record MonthlyCostSummary(
		String agentKey,
		String month,
		BigDecimal totalCostUsd,
		long totalInputTokens,
		long totalOutputTokens,
		long runCount,
		long subscriptionRunCount) {

	public static MonthlyCostSummary from(MonthlyCostProjection projection) {
		return new MonthlyCostSummary(
				projection.getAgentKey(),
				projection.getMonth(),
				projection.getTotalCostUsd(),
				projection.getTotalInputTokens() == null ? 0 : projection.getTotalInputTokens(),
				projection.getTotalOutputTokens() == null ? 0 : projection.getTotalOutputTokens(),
				projection.getRunCount() == null ? 0 : projection.getRunCount(),
				projection.getSubscriptionRunCount() == null ? 0 : projection.getSubscriptionRunCount());
	}
}
