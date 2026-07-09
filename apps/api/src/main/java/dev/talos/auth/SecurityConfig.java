package dev.talos.auth;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * All /api/v1/** except POST /auth/login require a JWT; /actuator/health is public (Section 12.2).
 * RBAC roles exist in the schema but the MVP runs owner-mode — every authenticated request passes.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

	private final JwtService jwtService;
	private final JsonAuthenticationEntryPoint authenticationEntryPoint;

	public SecurityConfig(JwtService jwtService, JsonAuthenticationEntryPoint authenticationEntryPoint) {
		this.jwtService = jwtService;
		this.authenticationEntryPoint = authenticationEntryPoint;
	}

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	@Bean
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
				.addFilterBefore(new JwtAuthenticationFilter(jwtService), UsernamePasswordAuthenticationFilter.class);
		return http.build();
	}
}
