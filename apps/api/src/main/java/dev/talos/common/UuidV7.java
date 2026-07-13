// SPDX-FileCopyrightText: 2026 Vulkan Technologies
// SPDX-License-Identifier: AGPL-3.0-or-later

package dev.talos.common;

import com.github.f4b6a3.uuid.UuidCreator;

import java.util.UUID;

/** UUID v7 (time-ordered, RFC 9562), generated application-side per Section 9.1 of the plan. */
public final class UuidV7 {

	private UuidV7() {
	}

	public static UUID generate() {
		return UuidCreator.getTimeOrderedEpoch();
	}
}
