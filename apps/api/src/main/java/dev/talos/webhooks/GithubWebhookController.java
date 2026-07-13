// SPDX-FileCopyrightText: 2026 Vulkan Technologies
// SPDX-License-Identifier: AGPL-3.0-or-later

package dev.talos.webhooks;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * Section 10.2: {@code POST /api/v1/webhooks/github} -- HMAC-verified (no JWT, see Section 10.1's
 * base-path conventions and SecurityConfig's permitAll), always 204 on success. The body is taken
 * as raw bytes because the HMAC is computed over the exact wire payload; parsing happens only
 * after the signature checks out.
 */
@RestController
public class GithubWebhookController {

	private final GithubWebhookService githubWebhookService;

	public GithubWebhookController(GithubWebhookService githubWebhookService) {
		this.githubWebhookService = githubWebhookService;
	}

	@PostMapping("/api/v1/webhooks/github")
	public ResponseEntity<Void> github(
			@RequestHeader(value = "X-Hub-Signature-256", required = false) String signature,
			@RequestHeader(value = "X-GitHub-Event", required = false) String event,
			@RequestBody byte[] payload) {
		githubWebhookService.handle(event, signature, payload);
		return ResponseEntity.noContent().build();
	}
}
