// SPDX-FileCopyrightText: 2026 Vulkan Technologies
// SPDX-License-Identifier: AGPL-3.0-or-later

package dev.talos.memory;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MemorySecretMaskerTest {

	private final MemorySecretMasker masker = new MemorySecretMasker();

	@Test
	void masksCommonSecretShapesBeforeMemoryPersistence() {
		String masked = masker.mask("token: ghp_abcdefghijklmnopqrstuvwxyz and OPENAI_API_KEY=sk-abcdefghijklmnop");

		assertThat(masked).doesNotContain("ghp_abcdefghijklmnopqrstuvwxyz");
		assertThat(masked).doesNotContain("sk-abcdefghijklmnop");
		assertThat(masked).contains("token=***");
		assertThat(masked).contains("OPENAI_API_KEY=***");
	}
}
