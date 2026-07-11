package dev.talos.recommendations;

import dev.talos.recommendations.dto.RecommendationResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Section 16 Phase 14: advisory suggested-agent/risk-flag signals for the task form and Command
 * Center. See RecommendationService for the "never auto-selects, never auto-executes" contract. */
@RestController
@RequestMapping("/api/v1/projects/{id}/recommendations")
public class RecommendationController {

	private final RecommendationService recommendationService;

	public RecommendationController(RecommendationService recommendationService) {
		this.recommendationService = recommendationService;
	}

	@GetMapping
	public RecommendationResponse get(@PathVariable UUID id) {
		return recommendationService.getRecommendations(id);
	}
}
