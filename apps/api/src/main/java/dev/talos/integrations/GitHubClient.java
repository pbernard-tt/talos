package dev.talos.integrations;

/** Section 8.4/9.2: opens PRs and validates connectivity via the GitHub REST API. */
public interface GitHubClient {

	boolean testConnection(String token);

	PullRequestResult createPullRequest(String token, String owner, String repo, String head, String base,
			String title, String body);

	record PullRequestResult(int number, String htmlUrl) {
	}
}
