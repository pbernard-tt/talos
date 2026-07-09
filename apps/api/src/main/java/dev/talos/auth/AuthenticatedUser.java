package dev.talos.auth;

import java.util.UUID;

/** The Spring Security principal set by {@link JwtAuthenticationFilter} from a validated JWT's claims. */
public record AuthenticatedUser(UUID id, String email, Role role) {
}
