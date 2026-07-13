// SPDX-FileCopyrightText: 2026 Vulkan Technologies
// SPDX-License-Identifier: AGPL-3.0-or-later

package dev.talos.recommendations.dto;

import dev.talos.recommendations.AgentStatProjection;

import java.math.BigDecimal;

public record AgentStat(
		String agentKey,
		long totalRuns,
		long completedRuns,
		long riskFlaggedRuns,
		double successRate,
		BigDecimal avgCostUsd) {

	public static AgentStat from(AgentStatProjection projection) {
		long totalRuns = projection.getTotalRuns() == null ? 0 : projection.getTotalRuns();
		long completedRuns = projection.getCompletedRuns() == null ? 0 : projection.getCompletedRuns();
		double successRate = totalRuns == 0 ? 0.0 : (double) completedRuns / totalRuns;
		return new AgentStat(projection.getAgentKey(), totalRuns, completedRuns,
				projection.getRiskFlaggedRuns() == null ? 0 : projection.getRiskFlaggedRuns(), successRate,
				projection.getAvgCostUsd());
	}
}
