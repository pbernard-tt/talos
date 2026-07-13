// SPDX-FileCopyrightText: 2026 Vulkan Technologies
// SPDX-License-Identifier: AGPL-3.0-or-later

package dev.talos.auth;

import java.util.UUID;

/**
 * The Spring Security principal set by {@link JwtAuthenticationFilter} from a validated JWT's claims.
 * {@code integrationScoped} marks a chat trigger service account (Section 16 Phase 12 Track B): true only
 * for JWTs issued to the seeded Telegram/WhatsApp accounts, and enforced by {@link IntegrationScopeFilter}
 * against a hard endpoint allow-list -- ahead of the general Phase 15 RBAC matrix, which does not exist yet.
 */
public record AuthenticatedUser(UUID id, String email, Role role, boolean integrationScoped) {
}
