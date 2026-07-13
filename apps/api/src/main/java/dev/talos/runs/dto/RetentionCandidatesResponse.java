// SPDX-FileCopyrightText: 2026 Vulkan Technologies
// SPDX-License-Identifier: AGPL-3.0-or-later

package dev.talos.runs.dto;

import java.util.List;

public record RetentionCandidatesResponse(List<RetentionCandidateResponse> candidates) {
}
