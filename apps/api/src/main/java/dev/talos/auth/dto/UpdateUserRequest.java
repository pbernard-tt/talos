// SPDX-FileCopyrightText: 2026 Vulkan Technologies
// SPDX-License-Identifier: AGPL-3.0-or-later

package dev.talos.auth.dto;

import dev.talos.auth.Role;

/** Section 16 Phase 15: OWNER-only role assignment / deactivation. Only non-null fields are applied. */
public record UpdateUserRequest(Role role, Boolean active) {
}
