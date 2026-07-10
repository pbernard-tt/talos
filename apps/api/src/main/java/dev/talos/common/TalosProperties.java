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
		String policyFile) {
}
