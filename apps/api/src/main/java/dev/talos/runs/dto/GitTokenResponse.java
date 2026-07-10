package dev.talos.runs.dto;

import dev.talos.integrations.GitCredentialsService.GitCredentials;

/** Section 8.4's push credential flow: internal-only (never reachable from /api/v1/**). */
public record GitTokenResponse(String token, String authMode, String repoUrl, String defaultBranch) {

	public static GitTokenResponse from(GitCredentials credentials) {
		return new GitTokenResponse(credentials.token(), credentials.authMode(), credentials.repoUrl(),
				credentials.defaultBranch());
	}
}
