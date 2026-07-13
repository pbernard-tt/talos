// SPDX-FileCopyrightText: 2026 Vulkan Technologies
// SPDX-License-Identifier: AGPL-3.0-or-later

package dev.talos.common;

import java.util.Map;

/** Error envelope returned on every non-2xx response (Section 10.1). */
public record ErrorResponse(ErrorBody error) {

	public record ErrorBody(String code, String message, Map<String, Object> details) {
		public ErrorBody(String code, String message) {
			this(code, message, Map.of());
		}
	}

	public static ErrorResponse of(String code, String message) {
		return new ErrorResponse(new ErrorBody(code, message));
	}

	public static ErrorResponse of(String code, String message, Map<String, Object> details) {
		return new ErrorResponse(new ErrorBody(code, message, details));
	}
}
