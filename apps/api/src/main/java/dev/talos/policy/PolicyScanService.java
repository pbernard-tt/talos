// SPDX-FileCopyrightText: 2026 Vulkan Technologies
// SPDX-License-Identifier: AGPL-3.0-or-later

package dev.talos.policy;

import dev.talos.audit.AuditService;
import dev.talos.projects.ProjectConfig;
import dev.talos.projects.ProjectConfigRepository;
import dev.talos.runs.AgentRun;
import dev.talos.runs.AgentRunLog;
import dev.talos.runs.AgentRunLogRepository;
import dev.talos.runs.GitChange;
import dev.talos.runs.GitChangeRepository;
import dev.talos.runs.ReviewStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Section 12.1's post-run scan, the review gate: runs once a run reaches WAITING_APPROVAL (called
 * from RunService). Flags matching git_changes rows with the pattern that matched (surfaced in the
 * Review Center) and records matched command patterns in the run.policy.scanned audit event.
 */
@Service
public class PolicyScanService {

	private final GitChangeRepository gitChangeRepository;
	private final AgentRunLogRepository agentRunLogRepository;
	private final ProjectConfigRepository projectConfigRepository;
	private final PolicyConfig policyConfig;
	private final AuditService auditService;

	public PolicyScanService(GitChangeRepository gitChangeRepository, AgentRunLogRepository agentRunLogRepository,
			ProjectConfigRepository projectConfigRepository, PolicyConfig policyConfig, AuditService auditService) {
		this.gitChangeRepository = gitChangeRepository;
		this.agentRunLogRepository = agentRunLogRepository;
		this.projectConfigRepository = projectConfigRepository;
		this.policyConfig = policyConfig;
		this.auditService = auditService;
	}

	@Transactional
	public ReviewStatus scan(AgentRun run) {
		PolicyRules rules = mergedRulesFor(run.getProjectId());

		List<String> matchedFilePatterns = new ArrayList<>();
		boolean anyFileFlagged = false;
		for (GitChange change : gitChangeRepository.findByRunId(run.getId())) {
			String matched = firstMatch(rules, change.getFilePath());
			if (matched != null) {
				change.flag(matched);
				gitChangeRepository.save(change);
				matchedFilePatterns.add(matched);
				anyFileFlagged = true;
			}
		}

		Set<String> matchedCommandPatterns = new LinkedHashSet<>();
		for (AgentRunLog log : agentRunLogRepository.findByRunIdOrderBySequenceAsc(run.getId())) {
			String blocked = PolicyMatcher.firstCommandMatch(rules.blockedCommands(), log.getMessage());
			if (blocked != null) {
				matchedCommandPatterns.add(blocked);
			}
			String approvalRequired = PolicyMatcher.firstCommandMatch(rules.approvalRequiredCommands(),
					log.getMessage());
			if (approvalRequired != null) {
				matchedCommandPatterns.add(approvalRequired);
			}
		}

		ReviewStatus status = (anyFileFlagged || !matchedCommandPatterns.isEmpty()) ? ReviewStatus.RISK_FLAGGED
				: ReviewStatus.CLEAN;

		auditService.record(null, "run.policy.scanned", "run", run.getId(),
				Map.of("matchedFilePatterns", matchedFilePatterns, "matchedCommandPatterns",
						List.copyOf(matchedCommandPatterns), "reviewStatus", status.name()));

		return status;
	}

	private String firstMatch(PolicyRules rules, String filePath) {
		String blocked = PolicyMatcher.firstFileMatch(rules.blockedFilePatterns(), filePath);
		if (blocked != null) {
			return blocked;
		}
		return PolicyMatcher.firstFileMatch(rules.approvalRequiredFilePatterns(), filePath);
	}

	private PolicyRules mergedRulesFor(java.util.UUID projectId) {
		PolicyRules base = policyConfig.rules();
		return projectConfigRepository.findByProjectIdAndActiveTrue(projectId)
				.map(ProjectConfig::getParsedJson)
				.map(json -> merge(base, json))
				.orElse(base);
	}

	@SuppressWarnings("unchecked")
	private PolicyRules merge(PolicyRules base, Map<String, Object> parsedJson) {
		if (!(parsedJson.get("rules") instanceof Map<?, ?> rulesMap)) {
			return base;
		}
		return base.mergedWith(stringList(rulesMap.get("forbidden")), stringList(rulesMap.get("require_approval_for")));
	}

	private List<String> stringList(Object value) {
		if (!(value instanceof List<?> list)) {
			return List.of();
		}
		List<String> out = new ArrayList<>();
		for (Object item : list) {
			out.add(String.valueOf(item));
		}
		return out;
	}
}
