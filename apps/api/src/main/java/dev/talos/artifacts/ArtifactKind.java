// SPDX-FileCopyrightText: 2026 Vulkan Technologies
// SPDX-License-Identifier: AGPL-3.0-or-later

package dev.talos.artifacts;

/** Section 4.2's artifact classes; the bucket/key layout in {@link ArtifactService} is partitioned by this. */
public enum ArtifactKind {
	DIFF_PATCH,
	TRANSCRIPT,
	TEST_REPORT,
	GENERATED_DOC
}
