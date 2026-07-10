package dev.talos.integrations;

import dev.talos.common.ApiException;
import org.springframework.http.HttpStatus;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Extracts {owner, repo} from a project's repo_url (https or git@ form) for the GitHub REST API. */
public record GitHubRepoRef(String owner, String repo) {

	private static final Pattern HTTPS = Pattern.compile("github\\.com[/:]([^/]+)/([^/.]+)(?:\\.git)?/?$");

	public static GitHubRepoRef parse(String repoUrl) {
		Matcher matcher = HTTPS.matcher(repoUrl);
		if (!matcher.find()) {
			throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "UNSUPPORTED_REPO_URL",
					"Could not parse a GitHub owner/repo from repo_url: %s".formatted(repoUrl));
		}
		return new GitHubRepoRef(matcher.group(1), matcher.group(2));
	}
}
