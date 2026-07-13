// SPDX-FileCopyrightText: 2026 Vulkan Technologies
// SPDX-License-Identifier: AGPL-3.0-or-later

package dev.talos.runs.dto;

import dev.talos.runs.StepStatus;
import dev.talos.runs.StepType;
import jakarta.validation.constraints.NotNull;

public record InternalStepRequest(@NotNull StepType stepType, @NotNull StepStatus status, String summary) {
}
