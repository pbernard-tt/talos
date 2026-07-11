package dev.talos.auth;

import dev.talos.audit.AuditService;
import dev.talos.auth.dto.LoginRequest;
import dev.talos.auth.dto.LoginResponse;
import dev.talos.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class AuthService {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtService jwtService;
	private final AuditService auditService;

	public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService,
			AuditService auditService) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.jwtService = jwtService;
		this.auditService = auditService;
	}

	public LoginResponse login(LoginRequest request) {
		// active is checked in the same filter as the password match (not a separate later check)
		// so a deactivated account gets the identical generic INVALID_CREDENTIALS response --
		// deactivation status is not observable to the caller, same as email existence today.
		User user = userRepository.findByEmail(request.email())
				.filter(u -> u.isActive() && passwordEncoder.matches(request.password(), u.getPasswordHash()))
				.orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS",
						"Invalid email or password"));

		JwtService.IssuedToken issued = jwtService.issue(user);
		auditService.record(user.getId(), "user.login", "user", user.getId(), Map.of());
		return new LoginResponse(issued.token(), issued.expiresAt(), user.getId(), user.getEmail(), user.getRole());
	}
}
