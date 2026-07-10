package dev.talos.integrations;

import dev.talos.common.ApiException;
import dev.talos.integrations.dto.TestIntegrationResponse;
import dev.talos.secrets.IntegrationCredential;
import dev.talos.secrets.IntegrationCredentialRepository;
import dev.talos.secrets.SecretService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Section 10.2/12.2: non-secret integration metadata lives on {@link Integration}; the secret lives behind {@link SecretService}, referenced only by secret_ref. */
@Service
public class IntegrationService {

	private final IntegrationRepository integrationRepository;
	private final IntegrationCredentialRepository integrationCredentialRepository;
	private final SecretService secretService;
	private final GitHubClient gitHubClient;
	private final DeployProvider deployProvider;

	public IntegrationService(IntegrationRepository integrationRepository,
			IntegrationCredentialRepository integrationCredentialRepository, SecretService secretService,
			GitHubClient gitHubClient, DeployProvider deployProvider) {
		this.integrationRepository = integrationRepository;
		this.integrationCredentialRepository = integrationCredentialRepository;
		this.secretService = secretService;
		this.gitHubClient = gitHubClient;
		this.deployProvider = deployProvider;
	}

	@Transactional
	public Integration create(String type, String name, Map<String, Object> configJson, String secretPlaintext,
			String authMode) {
		Integration integration = integrationRepository.save(new Integration(type, name, configJson));
		if (secretPlaintext != null && !secretPlaintext.isBlank()) {
			UUID secretRef = secretService.encrypt(secretPlaintext);
			integrationCredentialRepository
					.save(new IntegrationCredential(integration.getId(), secretRef, authMode, null));
		}
		return integration;
	}

	public List<Integration> list() {
		return integrationRepository.findAll();
	}

	public TestIntegrationResponse test(UUID id) {
		Integration integration = getOrThrow(id);
		String token = resolveToken(integration);
		boolean ok = switch (integration.getType()) {
			case "github" -> gitHubClient.testConnection(token);
			case "dokploy" -> deployProvider.testConnection(baseUrl(integration), token);
			default -> false;
		};
		if (!"github".equals(integration.getType()) && !"dokploy".equals(integration.getType())) {
			return new TestIntegrationResponse(false, "No test implemented for integration type " + integration.getType());
		}
		return new TestIntegrationResponse(ok, ok ? "Connection succeeded" : "Connection failed");
	}

	/**
	 * Resolves the decrypted token for the (single, MVP-scope) enabled GitHub integration -- see
	 * the Phase 9 phase report's "single global GitHub integration" deviation. Deliberately public
	 * (mirrors {@code RunService.getOrThrow}'s relaxed visibility) so the internal-only
	 * git-token/pull-request endpoints in dev.talos.runs can call it; no controller anywhere
	 * returns this value in a response body.
	 */
	public String resolveGitHubToken() {
		Integration integration = integrationRepository.findAll().stream()
				.filter(i -> "github".equals(i.getType()) && i.isEnabled())
				.findFirst()
				.orElseThrow(() -> new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "NO_GITHUB_INTEGRATION",
						"No enabled GitHub integration is configured"));
		return resolveToken(integration);
	}

	/**
	 * Resolves the (single, MVP-scope) enabled Dokploy integration's base URL + decrypted API key.
	 * Same relaxed-visibility rationale as {@link #resolveGitHubToken()}.
	 */
	public DokployCredentials resolveDokployCredentials() {
		Integration integration = integrationRepository.findAll().stream()
				.filter(i -> "dokploy".equals(i.getType()) && i.isEnabled())
				.findFirst()
				.orElseThrow(() -> new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "NO_DOKPLOY_INTEGRATION",
						"No enabled Dokploy integration is configured"));
		return new DokployCredentials(baseUrl(integration), resolveToken(integration));
	}

	private String baseUrl(Integration integration) {
		Object baseUrl = integration.getConfigJson().get("baseUrl");
		if (!(baseUrl instanceof String baseUrlString) || baseUrlString.isBlank()) {
			throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "INTEGRATION_MISSING_BASE_URL",
					"Integration %s has no configJson.baseUrl".formatted(integration.getId()));
		}
		return baseUrlString;
	}

	public record DokployCredentials(String baseUrl, String token) {
	}

	private String resolveToken(Integration integration) {
		List<IntegrationCredential> credentials = integrationCredentialRepository
				.findByIntegrationId(integration.getId());
		if (credentials.isEmpty()) {
			throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "INTEGRATION_HAS_NO_CREDENTIAL",
					"Integration %s has no stored credential".formatted(integration.getId()));
		}
		return secretService.decrypt(credentials.get(0).getSecretRef());
	}

	private Integration getOrThrow(UUID id) {
		return integrationRepository.findById(id)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "INTEGRATION_NOT_FOUND", "Integration not found"));
	}
}
