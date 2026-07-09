package dev.talos.auth.dto;

import java.time.Instant;

/** Section 10.2: POST /api/v1/auth/login -> {token, expiresAt}. */
public record LoginResponse(String token, Instant expiresAt) {
}
