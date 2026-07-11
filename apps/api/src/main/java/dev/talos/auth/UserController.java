package dev.talos.auth;

import dev.talos.auth.dto.CreateUserRequest;
import dev.talos.auth.dto.UpdateUserRequest;
import dev.talos.auth.dto.UserResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Section 16 Phase 15: user management (create, list, role assignment, deactivation) -- OWNER only. */
@RestController
@RequestMapping("/api/v1/users")
@PreAuthorize("hasRole('OWNER')")
public class UserController {

	private final UserService userService;

	public UserController(UserService userService) {
		this.userService = userService;
	}

	@GetMapping
	public List<UserResponse> list() {
		return userService.list().stream().map(UserResponse::from).toList();
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public UserResponse create(@Valid @RequestBody CreateUserRequest request,
			@AuthenticationPrincipal AuthenticatedUser principal) {
		return UserResponse.from(userService.create(request, principal.id()));
	}

	@PatchMapping("/{id}")
	public UserResponse update(@PathVariable UUID id, @RequestBody UpdateUserRequest request,
			@AuthenticationPrincipal AuthenticatedUser principal) {
		return UserResponse.from(userService.update(id, request, principal.id()));
	}
}
