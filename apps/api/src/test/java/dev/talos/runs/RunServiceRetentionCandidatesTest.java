// SPDX-FileCopyrightText: 2026 Vulkan Technologies
// SPDX-License-Identifier: AGPL-3.0-or-later

package dev.talos.runs;

import dev.talos.approvals.ApprovalRepository;
import dev.talos.audit.AuditService;
import dev.talos.events.EventPublisher;
import dev.talos.integrations.PullRequest;
import dev.talos.integrations.PullRequestRepository;
import dev.talos.integrations.PullRequestStatus;
import dev.talos.memory.MemoryService;
import dev.talos.policy.PolicyScanService;
import dev.talos.projects.Project;
import dev.talos.projects.ProjectConfigRepository;
import dev.talos.projects.ProjectRepository;
import dev.talos.runs.dto.RetentionCandidatesResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Phase 11 (Section 8.3): unit tests for RunService.getRetentionCandidates -- the query the
 * orchestrator's periodic retention sweep uses to decide which workspace directories to delete. */
@ExtendWith(MockitoExtension.class)
class RunServiceRetentionCandidatesTest {

	@Mock
	private AgentRunRepository agentRunRepository;
	@Mock
	private AgentRunStepRepository agentRunStepRepository;
	@Mock
	private AgentRunLogRepository agentRunLogRepository;
	@Mock
	private GitChangeRepository gitChangeRepository;
	@Mock
	private dev.talos.tasks.TaskRepository taskRepository;
	@Mock
	private ProjectRepository projectRepository;
	@Mock
	private ProjectConfigRepository projectConfigRepository;
	@Mock
	private AuditService auditService;
	@Mock
	private EventPublisher eventPublisher;
	@Mock
	private RunEventBroadcaster broadcaster;
	@Mock
	private PolicyScanService policyScanService;
	@Mock
	private ApprovalRepository approvalRepository;
	@Mock
	private PullRequestRepository pullRequestRepository;
	@Mock
	private MemoryService memoryService;

	private RunService runService;

	@BeforeEach
	void setUp() {
		runService = new RunService(agentRunRepository, agentRunStepRepository, agentRunLogRepository,
				gitChangeRepository, taskRepository, projectRepository, projectConfigRepository, auditService,
				eventPublisher, broadcaster, policyScanService, approvalRepository, pullRequestRepository,
				memoryService);
	}

	@Test
	void excludesTerminalRunsThatHaveAnOpenPullRequest() {
		Project project = new Project("Demo", "demo", "git@github.com:org/demo.git", "main", "python");
		UUID projectId = project.getId();

		AgentRun withOpenPr = new AgentRun(UUID.randomUUID(), projectId, "custom-shell", "api_key");
		AgentRun withoutPr = new AgentRun(UUID.randomUUID(), projectId, "custom-shell", "api_key");
		withOpenPr.transitionTo(RunStatus.COMPLETED, null, null);
		withoutPr.transitionTo(RunStatus.COMPLETED, null, null);

		when(agentRunRepository.findByStatusInAndCompletedAtBefore(eq(RunTransitionValidator.terminalStatuses()), any()))
				.thenReturn(List.of(withOpenPr, withoutPr));
		when(pullRequestRepository.findByRunIdInAndStatus(anyCollection(), eq(PullRequestStatus.OPEN)))
				.thenReturn(List.of(new PullRequest(withOpenPr.getId(), "github", 1, "url")));
		when(projectRepository.findAllById(anyCollection())).thenReturn(List.of(project));

		RetentionCandidatesResponse response = runService.getRetentionCandidates(7);

		assertThat(response.candidates()).hasSize(1);
		assertThat(response.candidates().get(0).runId()).isEqualTo(withoutPr.getId());
		assertThat(response.candidates().get(0).projectSlug()).isEqualTo("demo");
	}

	@Test
	void passesACutoffRoughlyMaxAgeDaysInThePast() {
		when(agentRunRepository.findByStatusInAndCompletedAtBefore(any(), any())).thenReturn(List.of());

		runService.getRetentionCandidates(7);

		ArgumentCaptor<Instant> cutoffCaptor = ArgumentCaptor.forClass(Instant.class);
		verify(agentRunRepository).findByStatusInAndCompletedAtBefore(any(), cutoffCaptor.capture());
		Instant expected = Instant.now().minus(7, ChronoUnit.DAYS);
		assertThat(cutoffCaptor.getValue()).isCloseTo(expected, org.assertj.core.api.Assertions.within(5, ChronoUnit.SECONDS));
	}

	@Test
	void noTerminalRunsShortCircuitsWithoutQueryingPullRequestsOrProjects() {
		when(agentRunRepository.findByStatusInAndCompletedAtBefore(any(), any())).thenReturn(List.of());

		RetentionCandidatesResponse response = runService.getRetentionCandidates(7);

		assertThat(response.candidates()).isEmpty();
		verifyNoInteractions(pullRequestRepository);
		verifyNoInteractions(projectRepository);
	}
}
