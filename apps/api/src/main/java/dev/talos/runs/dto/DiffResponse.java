// SPDX-FileCopyrightText: 2026 Vulkan Technologies
// SPDX-License-Identifier: AGPL-3.0-or-later

package dev.talos.runs.dto;

import java.util.List;

/** Section 10.2: GET /api/v1/runs/{id}/diff -> {files, diff}. */
public record DiffResponse(List<GitChangeResponse> files, String diff) {
}
