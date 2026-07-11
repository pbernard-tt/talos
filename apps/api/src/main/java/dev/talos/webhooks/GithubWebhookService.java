package dev.talos.webhooks;

import dev.talos.audit.AuditService;
import dev.talos.common.ApiException;
import dev.talos.common.TalosProperties;
import dev.talos.integrations.PullRequest;
import dev.talos.integrations.PullRequestRepository;
import dev.talos.integrations.PullRequestStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;

/**
 * Closes the PR-status loop the plan assigns to the GitHub webhook: {@code pull_requests.status}
 * is OPEN from PullRequestService and only this webhook moves it to MERGED/CLOSED (or back to
 * OPEN on reopen). Retention (RunService.getRetentionCandidates) excludes runs with an OPEN PR,
 * so without these transitions merged-PR workspaces would be retained forever.
 *
 * <p>Rows are matched by the PR's html URL, which PullRequestService stored verbatim from the
 * GitHub API and the webhook payload echoes -- PR numbers alone collide across repositories.
 * Events for PRs Talos didn't open (or event types other than pull_request) are acknowledged and
 * ignored: GitHub webhooks are repo-wide, so unknown PRs are expected, not errors.
 */
@Service
public class GithubWebhookService {

	private static final Logger log = LoggerFactory.getLogger(GithubWebhookService.class);
	private static final String SIGNATURE_PREFIX = "sha256=";

	private final PullRequestRepository pullRequestRepository;
	private final AuditService auditService;
	private final ObjectMapper objectMapper;
	private final TalosProperties talosProperties;

	public GithubWebhookService(PullRequestRepository pullRequestRepository, AuditService auditService,
			ObjectMapper objectMapper, TalosProperties talosProperties) {
		this.pullRequestRepository = pullRequestRepository;
		this.auditService = auditService;
		this.objectMapper = objectMapper;
		this.talosProperties = talosProperties;
	}

	@Transactional
	public void handle(String event, String signature, byte[] payload) {
		verifySignature(signature, payload);

		if (!"pull_request".equals(event)) {
			return;
		}

		JsonNode root = objectMapper.readTree(payload);
		String action = root.path("action").asString("");
		JsonNode pullRequest = root.path("pull_request");
		String htmlUrl = pullRequest.path("html_url").asString("");

		PullRequestStatus newStatus = switch (action) {
			case "closed" -> pullRequest.path("merged").asBoolean(false)
					? PullRequestStatus.MERGED
					: PullRequestStatus.CLOSED;
			case "reopened" -> PullRequestStatus.OPEN;
			default -> null;
		};
		if (newStatus == null || htmlUrl.isEmpty()) {
			return;
		}

		for (PullRequest pr : pullRequestRepository.findByProviderAndUrl("github", htmlUrl)) {
			if (pr.getStatus() == newStatus) {
				continue;
			}
			PullRequestStatus previous = pr.getStatus();
			pr.setStatus(newStatus);
			pullRequestRepository.save(pr);
			auditService.record(null, "pr.status.changed", "pull_request", pr.getId(),
					Map.of("prNumber", pr.getPrNumber(), "from", previous.name(), "to", newStatus.name()));
			log.info("PR {} (run {}) moved {} -> {} via GitHub webhook", htmlUrl, pr.getRunId(), previous, newStatus);
		}
	}

	private void verifySignature(String signature, byte[] payload) {
		String secret = talosProperties.githubWebhookSecret();
		if (secret == null || secret.isBlank()) {
			// Fail closed: with no secret configured nothing can be verified, so nothing is accepted.
			throw new ApiException(HttpStatus.UNAUTHORIZED, "WEBHOOK_NOT_CONFIGURED",
					"TALOS_GITHUB_WEBHOOK_SECRET is not configured; refusing unverified webhook delivery");
		}
		if (signature == null || !signature.startsWith(SIGNATURE_PREFIX)) {
			throw new ApiException(HttpStatus.UNAUTHORIZED, "WEBHOOK_SIGNATURE_INVALID",
					"Missing or malformed X-Hub-Signature-256 header");
		}
		byte[] expected = hmacSha256(secret, payload);
		byte[] provided;
		try {
			provided = HexFormat.of().parseHex(signature.substring(SIGNATURE_PREFIX.length()));
		} catch (IllegalArgumentException e) {
			throw new ApiException(HttpStatus.UNAUTHORIZED, "WEBHOOK_SIGNATURE_INVALID",
					"Missing or malformed X-Hub-Signature-256 header");
		}
		if (!MessageDigest.isEqual(expected, provided)) {
			throw new ApiException(HttpStatus.UNAUTHORIZED, "WEBHOOK_SIGNATURE_INVALID",
					"X-Hub-Signature-256 does not match the payload");
		}
	}

	private static byte[] hmacSha256(String secret, byte[] payload) {
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
			return mac.doFinal(payload);
		} catch (java.security.GeneralSecurityException e) {
			throw new IllegalStateException("HmacSHA256 unavailable", e);
		}
	}
}
