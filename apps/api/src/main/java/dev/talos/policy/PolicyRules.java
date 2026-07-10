package dev.talos.policy;

import java.util.ArrayList;
import java.util.List;

/**
 * Section 12.3's policy.yaml, parsed. `mergedWith` folds in a project's talos.yaml `rules`
 * (Section 14): those entries use semantic tokens (e.g. "production_deploy") rather than
 * policy.yaml's literal globs/substrings, so a project's own literal patterns pass through this
 * merge unchanged while semantic tokens simply never match anything (documented interpretation,
 * see docs/phase-reports/phase-8-report.md).
 */
public record PolicyRules(
		List<String> blockedCommands,
		List<String> approvalRequiredCommands,
		List<String> blockedFilePatterns,
		List<String> approvalRequiredFilePatterns) {

	public PolicyRules mergedWith(List<String> extraForbidden, List<String> extraApprovalRequired) {
		return new PolicyRules(
				concat(blockedCommands, extraForbidden),
				concat(approvalRequiredCommands, extraApprovalRequired),
				concat(blockedFilePatterns, extraForbidden),
				concat(approvalRequiredFilePatterns, extraApprovalRequired));
	}

	private static List<String> concat(List<String> base, List<String> extra) {
		if (extra == null || extra.isEmpty()) {
			return base;
		}
		List<String> merged = new ArrayList<>(base);
		merged.addAll(extra);
		return merged;
	}
}
