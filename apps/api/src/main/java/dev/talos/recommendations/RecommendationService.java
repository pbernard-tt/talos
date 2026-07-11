package dev.talos.recommendations;

import dev.talos.common.ApiException;
import dev.talos.projects.ProjectRepository;
import dev.talos.recommendations.dto.AgentStat;
import dev.talos.recommendations.dto.RecommendationResponse;
import dev.talos.recommendations.dto.RiskFlag;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Section 16 Phase 14: advisory-only recommendation signals computed from a project's run history.
 * Never auto-selects an agent and never auto-executes anything -- the operator always confirms.
 */
@Service
public class RecommendationService {

	private static final double CHEAPEST_CAPABLE_MIN_SUCCESS_RATE = 0.5;

	private final RunOutcomeQueryRepository runOutcomeQueryRepository;
	private final RiskFlagQueryRepository riskFlagQueryRepository;
	private final ProjectRepository projectRepository;

	public RecommendationService(RunOutcomeQueryRepository runOutcomeQueryRepository,
			RiskFlagQueryRepository riskFlagQueryRepository, ProjectRepository projectRepository) {
		this.runOutcomeQueryRepository = runOutcomeQueryRepository;
		this.riskFlagQueryRepository = riskFlagQueryRepository;
		this.projectRepository = projectRepository;
	}

	public RecommendationResponse getRecommendations(UUID projectId) {
		if (!projectRepository.existsById(projectId)) {
			throw new ApiException(HttpStatus.NOT_FOUND, "PROJECT_NOT_FOUND", "Project not found");
		}

		List<AgentStat> agentStats = runOutcomeQueryRepository.aggregateAgentStats(projectId).stream()
				.map(AgentStat::from).toList();
		List<RiskFlag> riskFlags = riskFlagQueryRepository.aggregateRiskFlagsByFileArea(projectId).stream()
				.map(RiskFlag::from).toList();

		String suggestedAgentKey = agentStats.stream()
				.filter(stat -> stat.totalRuns() > 0)
				.max(Comparator.comparingDouble(AgentStat::successRate)
						.thenComparing(Comparator.comparingLong(AgentStat::totalRuns).reversed()))
				.map(AgentStat::agentKey)
				.orElse(null);

		String cheapestCapableAgentKey = agentStats.stream()
				.filter(stat -> stat.avgCostUsd() != null && stat.successRate() >= CHEAPEST_CAPABLE_MIN_SUCCESS_RATE)
				.min(Comparator.comparing(AgentStat::avgCostUsd))
				.map(AgentStat::agentKey)
				.orElse(null);

		return new RecommendationResponse(suggestedAgentKey, cheapestCapableAgentKey, agentStats, riskFlags);
	}
}
