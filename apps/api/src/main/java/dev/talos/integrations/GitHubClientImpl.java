// SPDX-FileCopyrightText: 2026 Vulkan Technologies
// SPDX-License-Identifier: AGPL-3.0-or-later

package dev.talos.integrations;

import dev.talos.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Talks to the real GitHub REST API (Section 8.4). {@code apiBase} is overridable only so tests
 * can point this at an embedded mock server -- production always uses api.github.com.
 */
@Component
public class GitHubClientImpl implements GitHubClient {

	private final String apiBase;
	private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
	private final JsonMapper jsonMapper = JsonMapper.builder().build();

	public GitHubClientImpl() {
		this("https://api.github.com");
	}

	GitHubClientImpl(String apiBase) {
		this.apiBase = apiBase;
	}

	@Override
	public boolean testConnection(String token) {
		HttpRequest request = baseRequest("/user", token).GET().build();
		try {
			HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
			return response.statusCode() == 200;
		} catch (Exception e) {
			return false;
		}
	}

	@Override
	public PullRequestResult createPullRequest(String token, String owner, String repo, String head, String base,
			String title, String body) {
		String requestBody = jsonMapper.createObjectNode()
				.put("title", title)
				.put("body", body)
				.put("head", head)
				.put("base", base)
				.toString();
		HttpRequest request = baseRequest("/repos/%s/%s/pulls".formatted(owner, repo), token)
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(requestBody))
				.build();
		HttpResponse<String> response;
		try {
			response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
		} catch (Exception e) {
			throw new ApiException(HttpStatus.BAD_GATEWAY, "GITHUB_UNREACHABLE",
					"Could not reach GitHub: " + e.getMessage());
		}
		if (response.statusCode() != 201) {
			throw new ApiException(HttpStatus.BAD_GATEWAY, "GITHUB_PR_CREATE_FAILED",
					"GitHub PR creation failed with status %d: %s".formatted(response.statusCode(), response.body()));
		}
		JsonNode json = jsonMapper.readTree(response.body());
		return new PullRequestResult(json.at("/number").asInt(), json.at("/html_url").asString());
	}

	private HttpRequest.Builder baseRequest(String path, String token) {
		return HttpRequest.newBuilder()
				.uri(URI.create(apiBase + path))
				.timeout(Duration.ofSeconds(15))
				.header("Authorization", "Bearer " + token)
				.header("Accept", "application/vnd.github+json")
				.header("X-GitHub-Api-Version", "2022-11-28");
	}
}
