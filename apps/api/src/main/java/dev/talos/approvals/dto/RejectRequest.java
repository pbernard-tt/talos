// SPDX-FileCopyrightText: 2026 Vulkan Technologies
// SPDX-License-Identifier: AGPL-3.0-or-later

package dev.talos.approvals.dto;

import jakarta.validation.constraints.NotBlank;

/** Section 10.2: {notes} -- notes are required on reject. */
public record RejectRequest(@NotBlank String notes) {
}
