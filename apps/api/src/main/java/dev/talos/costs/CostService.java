// SPDX-FileCopyrightText: 2026 Vulkan Technologies
// SPDX-License-Identifier: AGPL-3.0-or-later

package dev.talos.costs;

import dev.talos.common.ApiException;
import dev.talos.costs.dto.MonthlyCostSummary;
import dev.talos.projects.ProjectRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class CostService {

	private final CostQueryRepository costQueryRepository;
	private final ProjectRepository projectRepository;

	public CostService(CostQueryRepository costQueryRepository, ProjectRepository projectRepository) {
		this.costQueryRepository = costQueryRepository;
		this.projectRepository = projectRepository;
	}

	public List<MonthlyCostSummary> monthlySummary(UUID projectId) {
		if (!projectRepository.existsById(projectId)) {
			throw new ApiException(HttpStatus.NOT_FOUND, "PROJECT_NOT_FOUND", "Project not found");
		}
		return costQueryRepository.aggregateMonthlyCosts(projectId).stream().map(MonthlyCostSummary::from).toList();
	}
}
