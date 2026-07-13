// SPDX-FileCopyrightText: 2026 Vulkan Technologies
// SPDX-License-Identifier: AGPL-3.0-or-later

package dev.talos.common;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Binds the `talos.*` keys in application.yml, sourced from the TALOS_ env vars in Appendix A. */
@ConfigurationProperties(prefix = "talos")
public record TalosProperties(
		String jwtSecret,
		String internalApiToken,
		String adminEmail,
		String adminPassword,
		String secretsKey,
		String githubWebhookSecret,
		String policyFile,
		Integer loginRateLimitMaxAttempts,
		Integer loginRateLimitWindowSeconds,
		// Section 16 Phase 12 Track B: seeded chat trigger service accounts (IntegrationServiceAccountSeeder).
		// Their JWTs are marked integrationScoped and restricted by IntegrationScopeFilter.
		String telegramServiceEmail,
		String telegramServicePassword,
		String whatsappServiceEmail,
		String whatsappServicePassword,
		// Phase 16: ArtifactStore selection (ArtifactStoreConfig) -- "local" (default) or "minio".
		String artifactStoreType,
		String artifactLocalDir,
		String minioEndpoint,
		String minioAccessKey,
		String minioSecretKey,
		String minioBucket,
		Boolean migrateArtifactsOnBoot,
		// Browser-facing talos-web runs on a different origin than talos-api in both dev compose
		// (localhost:4200 vs localhost:8080) and prod (TALOS_WEB_DOMAIN vs TALOS_API_DOMAIN) --
		// comma-separated allow-list. Empty/unset means no origins are allowed (fail closed).
		String corsAllowedOrigins) {
}
