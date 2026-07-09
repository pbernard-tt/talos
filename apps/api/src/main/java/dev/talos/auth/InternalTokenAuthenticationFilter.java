package dev.talos.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/** Authenticates /internal/v1/** requests via a shared service token (Section 10.1), not JWT. */
public class InternalTokenAuthenticationFilter extends OncePerRequestFilter {

	public static final String HEADER = "X-Talos-Internal-Token";

	private final String expectedToken;

	public InternalTokenAuthenticationFilter(String expectedToken) {
		this.expectedToken = expectedToken;
	}

	@Override
	protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
			@NonNull FilterChain filterChain) throws ServletException, IOException {
		String token = request.getHeader(HEADER);
		if (token != null && !token.isBlank() && !expectedToken.isBlank() && token.equals(expectedToken)) {
			var authorities = List.of(new SimpleGrantedAuthority("ROLE_SERVICE"));
			var authentication = new UsernamePasswordAuthenticationToken("internal-service", null, authorities);
			SecurityContextHolder.getContext().setAuthentication(authentication);
		}
		filterChain.doFilter(request, response);
	}
}
