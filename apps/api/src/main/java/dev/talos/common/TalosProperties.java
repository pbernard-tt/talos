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
		Boolean migrateArtifactsOnBoot) {
}
