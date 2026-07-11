package dev.talos.auth.dto;

import dev.talos.auth.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Section 16 Phase 15: OWNER-only user creation. Direct creation with an operator-set password,
 * not a token-based email invite -- this codebase has no mail/notification infrastructure to
 * support one (documented as a phase deviation from the plan's "create/invite" wording).
 */
public record CreateUserRequest(
		@NotBlank @Email String email,
		@NotBlank String name,
		@NotBlank @Size(min = 8) String password,
		@NotNull Role role) {
}
