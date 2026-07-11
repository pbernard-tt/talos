package dev.talos.recommendations;

import java.math.BigDecimal;

public interface AgentStatProjection {
	String getAgentKey();

	Long getTotalRuns();

	Long getCompletedRuns();

	Long getRiskFlaggedRuns();

	BigDecimal getAvgCostUsd();
}
