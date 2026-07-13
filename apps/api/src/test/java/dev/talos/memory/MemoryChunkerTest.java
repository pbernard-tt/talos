// SPDX-FileCopyrightText: 2026 Vulkan Technologies
// SPDX-License-Identifier: AGPL-3.0-or-later

package dev.talos.memory;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MemoryChunkerTest {

	private final MemoryChunker chunker = new MemoryChunker();

	@Test
	void chunksByBudgetWithoutDroppingText() {
		var chunks = chunker.chunk("alpha beta gamma delta epsilon", 12);

		assertThat(chunks).containsExactly("alpha beta", "gamma delta", "epsilon");
	}
}
