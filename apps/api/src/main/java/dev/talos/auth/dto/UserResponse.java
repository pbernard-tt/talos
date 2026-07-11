package dev.talos.auth.dto;

import dev.talos.auth.Role;
import dev.talos.auth.User;

import java.time.Instant;
import java.util.UUID;

/** Section 16 Phase 15: never includes passwordHash. */
public record UserResponse(UUID id, String email, String name, Role role, boolean active, Instant createdAt,
		Instant updatedAt) {

	public static UserResponse from(User user) {
		return new UserResponse(user.getId(), user.getEmail(), user.getName(), user.getRole(), user.isActive(),
				user.getCreatedAt(), user.getUpdatedAt());
	}
}
