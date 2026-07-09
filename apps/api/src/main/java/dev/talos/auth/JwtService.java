package dev.talos.auth;

import dev.talos.common.TalosProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

/** Issues and validates the 24h JWTs described in Section 12.2 of the plan. */
@Service
public class JwtService {

	private static final Duration TOKEN_TTL = Duration.ofHours(24);

	private final SecretKey key;

	public JwtService(TalosProperties properties) {
		this.key = Keys.hmacShaKeyFor(properties.jwtSecret().getBytes(StandardCharsets.UTF_8));
	}

	public record IssuedToken(String token, Instant expiresAt) {
	}

	public IssuedToken issue(User user) {
		Instant now = Instant.now();
		Instant expiresAt = now.plus(TOKEN_TTL);
		String token = Jwts.builder()
				.subject(user.getId().toString())
				.claim("email", user.getEmail())
				.claim("role", user.getRole().name())
				.issuedAt(Date.from(now))
				.expiration(Date.from(expiresAt))
				.signWith(key)
				.compact();
		return new IssuedToken(token, expiresAt);
	}

	/** Returns the validated claims, or empty if the token is missing, malformed, expired, or has a bad signature. */
	public Optional<AuthenticatedUser> validate(String token) {
		try {
			Jws<Claims> jws = Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
			Claims claims = jws.getPayload();
			UUID userId = UUID.fromString(claims.getSubject());
			String email = claims.get("email", String.class);
			Role role = Role.valueOf(claims.get("role", String.class));
			return Optional.of(new AuthenticatedUser(userId, email, role));
		} catch (JwtException | IllegalArgumentException e) {
			return Optional.empty();
		}
	}
}
