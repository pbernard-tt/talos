// SPDX-FileCopyrightText: 2026 Vulkan Technologies
// SPDX-License-Identifier: AGPL-3.0-or-later

package dev.talos.tasks.dto;

import dev.talos.tasks.TaskStatus;
import jakarta.validation.constraints.NotNull;

public record MoveTaskRequest(@NotNull TaskStatus status, @NotNull Integer boardPosition) {
}
