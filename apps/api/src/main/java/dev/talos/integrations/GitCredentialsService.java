package dev.talos.integrations;

import dev.talos.common.ApiException;
import dev.talos.projects.Project;
import dev.talos.projects.ProjectRepository;
import dev.talos.runs.AgentRun;
import dev.talos.runs.RunStatus;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Section 8.4's credential flow for the runner supervisor's push step: resolves the decrypted
 * GitHub token for a run's project. Guards {@code run.status == APPROVED} -- this is the
 * server-side enforcement behind Phase 9's "unapproved run cannot push" acceptance test, since
 * {@code GET /internal/v1/runs/{id}/git-token} is the only place this token ever leaves talos-api.
 */
@Service
public class GitCredentialsService {

	private final ProjectRepository projectRepository;
	private final IntegrationService integrationService;

	public GitCredentialsService(ProjectRepository projectRepository, IntegrationService integrationService) {
		this.projectRepository = projectRepository;
		this.integrationService = integrationService;
	}

	public GitCredentials resolveForRun(AgentRun run) {
		if (run.getStatus() != RunStatus.APPROVED) {
			throw new ApiException(HttpStatus.CONFLICT, "RUN_NOT_APPROVED",
					"Run %s is not APPROVED (currently %s) -- git credentials are only issued for an approved run"
							.formatted(run.getId(), run.getStatus()));
		}
		Project project = projectRepository.findById(run.getProjectId())
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PROJECT_NOT_FOUND", "Project not found"));
		String token = integrationService.resolveGitHubToken();
		return new GitCredentials(token, "pat", project.getRepoUrl(), project.getDefaultBranch());
	}

	public record GitCredentials(String token, String authMode, String repoUrl, String defaultBranch) {
	}
}
