// SPDX-FileCopyrightText: 2026 Vulkan Technologies
// SPDX-License-Identifier: AGPL-3.0-or-later

package dev.talos.policy;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** One test per pattern class from Section 12.3's example policy.yaml. */
class PolicyMatcherTest {

	@Test
	void fileBlockedPattern_matchesNestedEnvFile() {
		assertThat(PolicyMatcher.matchesFile(".env*", "backend/.env.local")).isTrue();
		assertThat(PolicyMatcher.matchesFile(".env*", ".env")).isTrue();
		assertThat(PolicyMatcher.firstFileMatch(List.of("*.pem", ".env*"), "config/service.env")).isNull();
	}

	@Test
	void fileBlockedPattern_matchesPemAndNestedSecretsDir() {
		assertThat(PolicyMatcher.matchesFile("*.pem", "certs/server.pem")).isTrue();
		assertThat(PolicyMatcher.matchesFile("**/secrets/**", "app/secrets/api-key.txt")).isTrue();
	}

	@Test
	void fileApprovalRequiredPattern_matchesWorkflowsAndMigrations() {
		assertThat(PolicyMatcher.matchesFile(".github/workflows/**", ".github/workflows/ci.yml")).isTrue();
		assertThat(PolicyMatcher.matchesFile("**/db/migration/**", "apps/api/src/main/resources/db/migration/V001.sql"))
				.isTrue();
	}

	@Test
	void commandBlockedPattern_matchesSubstring() {
		assertThat(PolicyMatcher.matchesCommand("rm -rf /", "running: rm -rf / --no-preserve-root")).isTrue();
		assertThat(PolicyMatcher.firstCommandMatch(List.of("chmod 777", "docker system prune"), "chmod 777 app.sh"))
				.isEqualTo("chmod 777");
	}

	@Test
	void commandApprovalRequiredPattern_matchesSubstring() {
		assertThat(PolicyMatcher.matchesCommand("sudo", "sudo apt-get install foo")).isTrue();
		assertThat(PolicyMatcher.matchesCommand("psql", "psql -U talos -d talos")).isTrue();
	}

	@Test
	void nonMatchingFileOrCommand_isClean() {
		assertThat(PolicyMatcher.firstFileMatch(List.of(".env*", "*.pem"), "src/main/java/App.java")).isNull();
		assertThat(PolicyMatcher.firstCommandMatch(List.of("sudo", "psql"), "./mvnw test")).isNull();
	}
}
