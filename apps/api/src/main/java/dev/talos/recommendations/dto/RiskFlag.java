package dev.talos.recommendations.dto;

import dev.talos.recommendations.RiskFlagProjection;

public record RiskFlag(String fileArea, long riskFlaggedRunCount) {

	public static RiskFlag from(RiskFlagProjection projection) {
		return new RiskFlag(projection.getFileArea(),
				projection.getRiskFlaggedRunCount() == null ? 0 : projection.getRiskFlaggedRunCount());
	}
}
