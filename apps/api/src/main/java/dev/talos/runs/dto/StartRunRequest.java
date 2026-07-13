// SPDX-FileCopyrightText: 2026 Vulkan Technologies
// SPDX-License-Identifier: AGPL-3.0-or-later

package dev.talos.runs.dto;

/** {@code agentKey}/{@code authMode} default from the project's active talos.yaml when omitted. */
public record StartRunRequest(String agentKey, String authMode) {
}
