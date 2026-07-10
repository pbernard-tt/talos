package dev.talos.auth;

import dev.talos.audit.AuditService;
import dev.talos.common.TalosProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import tools.jackson.databind.ObjectMapper;

/**
 * Two filter chains: /internal/v1/** authenticates via a shared service token (Section 10.1),
 * everything else via JWT except POST /auth/login and /actuator/health (Section 12.2). RBAC
 * roles exist in the schema but the MVP runs owner-mode — every authenticated JWT request passes.
 */
@Configuration
@EnableWebSecurity
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

	@Bean
	@Order(2)
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http
				.csrf(csrf -> csrf.disable())
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.httpBasic(basic -> basic.disable())
				.formLogin(form -> form.disable())
				.exceptionHandling(handling -> handling.authenticationEntryPoint(authenticationEntryPoint))
				.authorizeHttpRequests(auth -> auth
						.requestMatchers("/actuator/health").permitAll()
						.requestMatchers("/api/v1/auth/login").permitAll()
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
