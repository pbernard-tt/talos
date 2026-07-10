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
 * Talks to the real Dokploy REST API (verified against docs.dokploy.com via Context7 at
 * implementation time, Phase 10): {@code POST /api/application.deploy} to trigger, {@code GET
 * /api/deployment.all?applicationId=} to poll. Status is Dokploy's {@code applicationStatus} enum
 * (idle|running|done|error, confirmed via the analogous postgres.changeStatus endpoint -- the
 * deployment list's exact per-item shape isn't documented beyond "an array of deployments", so
 * this defensively reads the last array element's status/createdAt/errMessage fields and treats
 * any other shape as still in progress rather than failing loudly.
 */
@Component
public class DokployDeployProvider implements DeployProvider {

	private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
	private final JsonMapper jsonMapper = JsonMapper.builder().build();

	@Override
	public boolean testConnection(String baseUrl, String token) {
		HttpRequest request = baseRequest(baseUrl, "/api/project.all", token).GET().build();
		try {
			HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
			return response.statusCode() == 200;
		} catch (Exception e) {
			return false;
		}
	}

	@Override
	public void trigger(String baseUrl, String token, String appId, String title) {
		String requestBody = jsonMapper.createObjectNode()
				.put("applicationId", appId)
				.put("title", title)
				.toString();
		HttpRequest request = baseRequest(baseUrl, "/api/application.deploy", token)
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(requestBody))
				.build();
		HttpResponse<String> response;
		try {
			response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
		} catch (Exception e) {
			throw new ApiException(HttpStatus.BAD_GATEWAY, "DOKPLOY_UNREACHABLE",
					"Could not reach Dokploy: " + e.getMessage());
		}
		if (response.statusCode() != 200) {
			throw new ApiException(HttpStatus.BAD_GATEWAY, "DOKPLOY_DEPLOY_TRIGGER_FAILED",
					"Dokploy deploy trigger failed with status %d: %s".formatted(response.statusCode(), response.body()));
		}
	}

	@Override
	public DeployPollResult pollLatestStatus(String baseUrl, String token, String appId) {
		HttpRequest request = baseRequest(baseUrl, "/api/deployment.all?applicationId=" + appId, token).GET().build();
		HttpResponse<String> response;
		try {
			response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
		} catch (Exception e) {
			return DeployPollResult.inProgress();
		}
		if (response.statusCode() != 200) {
			return DeployPollResult.inProgress();
		}
		JsonNode deployments = jsonMapper.readTree(response.body());
		if (!deployments.isArray() || deployments.isEmpty()) {
			return DeployPollResult.inProgress();
		}
		JsonNode latest = deployments.get(deployments.size() - 1);
		String status = latest.at("/status").asString(null);
		return switch (status == null ? "" : status) {
			case "done" -> DeployPollResult.succeeded();
			case "error" -> DeployPollResult.failed(latest.at("/errorMessage").asString("Dokploy deployment failed"));
			default -> DeployPollResult.inProgress(); // idle | running | unrecognized
		};
	}

	private HttpRequest.Builder baseRequest(String baseUrl, String path, String token) {
		return HttpRequest.newBuilder()
				.uri(URI.create(baseUrl + path))
				.timeout(Duration.ofSeconds(15))
				.header("Accept", "application/json")
				.header("x-api-key", token);
	}
}
