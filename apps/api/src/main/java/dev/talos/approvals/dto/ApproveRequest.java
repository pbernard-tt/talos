// SPDX-FileCopyrightText: 2026 Vulkan Technologies
// SPDX-License-Identifier: AGPL-3.0-or-later

package dev.talos.approvals.dto;

/** Section 10.2: {notes?} -- notes are optional on approve. */
public record ApproveRequest(String notes) {
}
