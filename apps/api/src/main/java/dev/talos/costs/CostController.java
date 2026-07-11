package dev.talos.costs;

import dev.talos.costs.dto.MonthlyCostSummary;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Section 16 Phase 14: per-project cost visibility, the data behind the dashboard cost widget. */
@RestController
@RequestMapping("/api/v1/projects/{id}/costs")
public class CostController {

	private final CostService costService;

	public CostController(CostService costService) {
		this.costService = costService;
	}

	@GetMapping("/monthly")
	public List<MonthlyCostSummary> monthly(@PathVariable UUID id) {
		return costService.monthlySummary(id);
	}
}
