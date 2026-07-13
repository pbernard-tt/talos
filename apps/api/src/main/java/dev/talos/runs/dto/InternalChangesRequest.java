// SPDX-FileCopyrightText: 2026 Vulkan Technologies
// SPDX-License-Identifier: AGPL-3.0-or-later

package dev.talos.runs.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

public record InternalChangesRequest(@NotNull List<GitChangeDto> files, String diffArtifactRef, String diffPatch) {
}
