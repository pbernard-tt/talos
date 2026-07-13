// SPDX-FileCopyrightText: 2026 Vulkan Technologies
// SPDX-License-Identifier: AGPL-3.0-or-later

package dev.talos.auth;

import dev.talos.audit.AuditService;
import dev.talos.auth.dto.CreateUserRequest;
import dev.talos.auth.dto.UpdateUserRequest;
import dev.talos.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Section 16 Phase 15: OWNER-only user management (create, list, role assignment, deactivation). */
@Service
public class UserService {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final AuditService auditService;

	public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder, AuditService auditService) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.auditService = auditService;
	}

	public List<User> list() {
		return userRepository.findAll();
	}

	@Transactional
	public User create(CreateUserRequest request, UUID actorUserId) {
		if (userRepository.existsByEmail(request.email())) {
			throw new ApiException(HttpStatus.CONFLICT, "EMAIL_ALREADY_EXISTS", "A user with this email already exists");
		}
		User user = new User(request.email(), request.name(), passwordEncoder.encode(request.password()), request.role());
		user = userRepository.save(user);
		auditService.record(actorUserId, "user.created", "user", user.getId(),
				Map.of("email", user.getEmail(), "role", user.getRole().name()));
		return user;
	}

	/** Refuses to let an OWNER change their own role or deactivate themselves through this endpoint
	 * -- a lone OWNER doing so would lock the install out of user management entirely; use another
	 * OWNER account instead. */
	@Transactional
	public User update(UUID id, UpdateUserRequest request, UUID actorUserId) {
		if (id.equals(actorUserId) && (request.role() != null || Boolean.FALSE.equals(request.active()))) {
			throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "CANNOT_MODIFY_OWN_ACCOUNT",
					"Use another OWNER account to change your own role or deactivate yourself");
		}
		User user = getOrThrow(id);
		if (request.role() != null) {
			user.changeRole(request.role());
		}
		if (request.active() != null) {
			user.setActive(request.active());
		}
		user = userRepository.save(user);
		auditService.record(actorUserId, "user.updated", "user", user.getId(),
				Map.of("role", user.getRole().name(), "active", user.isActive()));
		return user;
	}

	private User getOrThrow(UUID id) {
		return userRepository.findById(id)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found"));
	}
}
