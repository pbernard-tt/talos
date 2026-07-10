package dev.talos.auth;

import dev.talos.common.ErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.Duration;

/**
 * Section 12.2: rate-limits POST /api/v1/auth/login, the one unauthenticated (and therefore most
 * brute-forceable) endpoint -- a Redis-backed fixed-window counter (INCR + EXPIRE) keyed by client
 * IP, reusing the Redis dependency already load-bearing for locks/SSE rather than a new library.
 * Every other endpoint requires a JWT already, so a blanket limiter isn't needed and would risk
 * throttling legitimate SSE/polling traffic.
 */
public class LoginRateLimitFilter extends OncePerRequestFilter {

	private static final String LOGIN_PATH = "/api/v1/auth/login";
	private static final String KEY_PREFIX = "talos:ratelimit:login:";

	private final StringRedisTemplate redisTemplate;
	private final ObjectMapper objectMapper;
	private final int maxAttempts;
	private final Duration window;

	public LoginRateLimitFilter(StringRedisTemplate redisTemplate, ObjectMapper objectMapper, int maxAttempts,
			int windowSeconds) {
		this.redisTemplate = redisTemplate;
		this.objectMapper = objectMapper;
		this.maxAttempts = maxAttempts;
		this.window = Duration.ofSeconds(windowSeconds);
	}

	@Override
	protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
			@NonNull FilterChain filterChain) throws ServletException, IOException {
		if (!"POST".equalsIgnoreCase(request.getMethod()) || !LOGIN_PATH.equals(request.getRequestURI())) {
			filterChain.doFilter(request, response);
			return;
		}

		String key = KEY_PREFIX + clientIp(request);
		Long attempts = redisTemplate.opsForValue().increment(key);
		if (attempts != null && attempts == 1L) {
			redisTemplate.expire(key, window);
		}
		if (attempts != null && attempts > maxAttempts) {
			response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
			response.setContentType(MediaType.APPLICATION_JSON_VALUE);
			objectMapper.writeValue(response.getWriter(),
					ErrorResponse.of("RATE_LIMITED", "Too many login attempts, try again later"));
			return;
		}
		filterChain.doFilter(request, response);
	}

	/**
	 * Trusts X-Forwarded-For's first hop when present. Talos is deployed behind Dokploy/Traefik
	 * (Section 18), which sets this on every proxied request; accepted residual risk if talos-api is
	 * ever exposed directly without a reverse proxy in front, a caller could spoof a fresh IP per
	 * request and evade the limiter -- out of scope for this pass, noted in docs/security-model.md.
	 */
	private static String clientIp(HttpServletRequest request) {
		String forwardedFor = request.getHeader("X-Forwarded-For");
		if (forwardedFor != null && !forwardedFor.isBlank()) {
			return forwardedFor.split(",")[0].trim();
		}
		return request.getRemoteAddr();
	}
}
