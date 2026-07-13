// SPDX-FileCopyrightText: 2026 Vulkan Technologies
// SPDX-License-Identifier: AGPL-3.0-or-later

package dev.talos.auth;

import dev.talos.audit.AuditService;
import dev.talos.common.TalosProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import tools.jackson.databind.ObjectMapper;

import java.util.Arrays;
import java.util.List;

/**
 * Two filter chains: /internal/v1/** authenticates via a shared service token (Section 10.1),
 * everything else via JWT except POST /auth/login and /actuator/health (Section 12.2). Phase 15:
 * real RBAC enforcement via {@code @PreAuthorize} on controller methods (see TaskController,
 * ProjectController, RunController, ApprovalController, IntegrationController, MemoryController,
 * UserController), backed by the {@link #roleHierarchy()} bean below so e.g. `hasRole('MAINTAINER')`
 * also admits OWNER. Endpoints with no {@code @PreAuthorize} require only authentication (VIEWER
 * read-only tier) -- Section 9.3's roles are a strict hierarchy, not independent scopes.
 *
 * <p>Denials from {@code @PreAuthorize} throw {@code AuthorizationDeniedException} from inside the
 * controller method invocation (Spring AOP around the handler), which Spring MVC's own exception
 * resolution catches before it would ever reach a filter-chain-level {@code AccessDeniedHandler} --
 * so that denial is handled in {@link dev.talos.common.GlobalExceptionHandler}, not here (found
 * live: a filter-level handler wired via {@code exceptionHandling().accessDeniedHandler(...)} is
 * simply never invoked for this codebase's method-security-only usage).
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

	private final JwtService jwtService;
	private final JsonAuthenticationEntryPoint authenticationEntryPoint;
	private final TalosProperties talosProperties;
	private final StringRedisTemplate redisTemplate;
	private final ObjectMapper objectMapper;
	private final AuditService auditService;

	public SecurityConfig(JwtService jwtService, JsonAuthenticationEntryPoint authenticationEntryPoint,
			TalosProperties talosProperties, StringRedisTemplate redisTemplate, ObjectMapper objectMapper,
			AuditService auditService) {
		this.jwtService = jwtService;
		this.authenticationEntryPoint = authenticationEntryPoint;
		this.talosProperties = talosProperties;
		this.redisTemplate = redisTemplate;
		this.objectMapper = objectMapper;
		this.auditService = auditService;
	}

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	/** Declared static per Spring Security's guidance, so the method-security infrastructure beans
	 * (which need it) don't trigger early instantiation of this whole configuration class. */
	@Bean
	static RoleHierarchy roleHierarchy() {
		return RoleHierarchyImpl.withDefaultRolePrefix()
				.role("OWNER").implies("MAINTAINER")
				.role("MAINTAINER").implies("REVIEWER")
				.role("REVIEWER").implies("VIEWER")
				.build();
	}

	@Bean
	@Order(1)
	public SecurityFilterChain internalSecurityFilterChain(HttpSecurity http) throws Exception {
		http
				.securityMatcher("/internal/v1/**")
				.csrf(csrf -> csrf.disable())
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.httpBasic(basic -> basic.disable())
				.formLogin(form -> form.disable())
				.exceptionHandling(handling -> handling.authenticationEntryPoint(authenticationEntryPoint))
				.authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
				.addFilterBefore(new InternalTokenAuthenticationFilter(talosProperties.internalApiToken()),
						UsernamePasswordAuthenticationFilter.class);
		return http.build();
	}

	/** talos-web is a separate origin from talos-api in both dev compose (localhost:4200 vs :8080)
	 * and prod (TALOS_WEB_DOMAIN vs TALOS_API_DOMAIN) -- the browser calls the API directly (no
	 * reverse-proxy path rewriting), so without this every fetch from the SPA fails the CORS
	 * preflight. Unset/blank TALOS_CORS_ALLOWED_ORIGINS allows nothing (fail closed, Section 12.2's
	 * "safe defaults everywhere"); the operator must opt in per environment. */
	@Bean
	public CorsConfigurationSource corsConfigurationSource() {
		String configured = talosProperties.corsAllowedOrigins();
		List<String> allowedOrigins = configured == null || configured.isBlank()
				? List.of()
				: Arrays.stream(configured.split(",")).map(String::trim).filter(origin -> !origin.isBlank()).toList();

		CorsConfiguration configuration = new CorsConfiguration();
		configuration.setAllowedOrigins(allowedOrigins);
		configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
		configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Last-Event-ID"));

		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/api/v1/**", configuration);
		return source;
	}

	@Bean
	@Order(2)
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http
				.csrf(csrf -> csrf.disable())
				.cors(cors -> cors.configurationSource(corsConfigurationSource()))
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.httpBasic(basic -> basic.disable())
				.formLogin(form -> form.disable())
				.exceptionHandling(handling -> handling.authenticationEntryPoint(authenticationEntryPoint))
				.authorizeHttpRequests(auth -> auth
						.requestMatchers("/actuator/health").permitAll()
						.requestMatchers("/api/v1/auth/login").permitAll()
						// Section 10.1: /api/v1/webhooks/* authenticates via HMAC signature, not JWT --
						// verified inside GithubWebhookService against TALOS_GITHUB_WEBHOOK_SECRET.
						.requestMatchers("/api/v1/webhooks/**").permitAll()
						.anyRequest().authenticated())
				.addFilterBefore(new JwtAuthenticationFilter(jwtService), UsernamePasswordAuthenticationFilter.class)
				.addFilterBefore(
						new LoginRateLimitFilter(redisTemplate, objectMapper,
								talosProperties.loginRateLimitMaxAttempts(), talosProperties.loginRateLimitWindowSeconds()),
						JwtAuthenticationFilter.class)
				.addFilterAfter(new IntegrationScopeFilter(auditService, objectMapper), JwtAuthenticationFilter.class);
		return http.build();
	}
}
