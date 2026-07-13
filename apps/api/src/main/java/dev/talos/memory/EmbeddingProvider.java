// SPDX-FileCopyrightText: 2026 Vulkan Technologies
// SPDX-License-Identifier: AGPL-3.0-or-later

package dev.talos.memory;

public interface EmbeddingProvider {
	int dimensions();

	float[] embed(String text);
}
