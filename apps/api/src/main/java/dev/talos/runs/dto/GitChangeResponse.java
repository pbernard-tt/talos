package dev.talos.runs.dto;

import dev.talos.runs.GitChange;
import dev.talos.runs.GitChangeType;

/**
 * Output-only counterpart to {@link GitChangeDto} (Phase 8): adds the policy-scan result
 * (Section 12.1's review gate) for the Review Center's file list and risk panel.
 */
public record GitChangeResponse(String filePath, GitChangeType changeType, int additions, int deletions,
		boolean riskFlagged, String matchedPattern) {

	public static GitChangeResponse from(GitChange change) {
		return new GitChangeResponse(change.getFilePath(), change.getChangeType(), change.getAdditions(),
				change.getDeletions(), change.isRiskFlagged(), change.getMatchedPattern());
	}
}
