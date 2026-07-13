// SPDX-FileCopyrightText: 2026 Vulkan Technologies
// SPDX-License-Identifier: AGPL-3.0-or-later

package dev.talos.recommendations;

import java.math.BigDecimal;

public interface AgentStatProjection {
	String getAgentKey();

	Long getTotalRuns();

	Long getCompletedRuns();

	Long getRiskFlaggedRuns();

	BigDecimal getAvgCostUsd();
}
