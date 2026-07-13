// SPDX-FileCopyrightText: 2026 Vulkan Technologies
// SPDX-License-Identifier: AGPL-3.0-or-later

package dev.talos.recommendations.dto;

import java.util.List;

/**
 * Section 16 Phase 14: GET /api/v1/projects/{id}/recommendations response. Advisory only --
 * suggestedAgentKey/cheapestCapableAgentKey are hints for the task form and Command Center; the
 * operator always confirms, nothing here is auto-applied.
 */
public record RecommendationResponse(
		String suggestedAgentKey,
		String cheapestCapableAgentKey,
		List<AgentStat> agentStats,
		List<RiskFlag> riskFlags) {
}
