package dev.talos.auth.dto;

import dev.talos.auth.Role;

import java.time.Instant;
import java.util.UUID;

/**
 * Section 10.2: POST /api/v1/auth/login -> {token, expiresAt}. Phase 15 adds userId/email/role so
 * the dashboard can hide role-inappropriate actions without decoding the JWT itself -- server-side
 * enforcement is still the only real gate (Section 16 Phase 15 acceptance: "no endpoint relies on
 * UI hiding for protection").
 */
public record LoginResponse(String token, Instant expiresAt, UUID userId, String email, Role role) {
}
