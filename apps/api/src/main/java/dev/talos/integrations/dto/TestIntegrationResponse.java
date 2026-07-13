// SPDX-FileCopyrightText: 2026 Vulkan Technologies
// SPDX-License-Identifier: AGPL-3.0-or-later

package dev.talos.integrations.dto;

/** Section 10.2: POST /api/v1/integrations/{id}/test -> {ok, message}. */
public record TestIntegrationResponse(boolean ok, String message) {
}
