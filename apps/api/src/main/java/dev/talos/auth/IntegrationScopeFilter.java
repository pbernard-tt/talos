// SPDX-FileCopyrightText: 2026 Vulkan Technologies
// SPDX-License-Identifier: AGPL-3.0-or-later

package dev.talos.auth;

import dev.talos.audit.AuditService;
import dev.talos.common.ErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Section 16 Phase 12 Track B: a chat trigger service account's JWT ({@code integrationScoped=true},
 * set by {@link JwtService}) may only create/read tasks, read projects, read run status, and list
 * approvals -- never approve/reject/deploy/push or touch secrets/integrations. This is a purpose-built
 * allow-list, not the general Phase 15 RBAC matrix, which does not exist yet; every other authenticated
 * request is unaffected. A denied request still writes an audit row, per the Track B acceptance
 * criteria's "any other sender produces ... an audit row" spirit applied to over-scope access attempts.
 */
public class IntegrationScopeFilter extends OncePerRequestFilter {

	private record AllowedEndpoint(String method, String pathPattern) {
	}

	// AntPathMatcher patterns: "*" matches exactly one path segment (not "/"), matching
	// e.g. GET /api/v1/tasks/{id} without matching /api/v1/tasks/{id}/move or /start-run.
	private static final List<AllowedEndpoint> ALLOWED_ENDPOINTS = List.of(
			new AllowedEndpoint("GET", "/api/v1/projects"),
			new AllowedEndpoint("GET", "/api/v1/projects/*"),
			new AllowedEndpoint("GET", "/api/v1/tasks"),
			new AllowedEndpoint("POST", "/api/v1/tasks"),
			new AllowedEndpoint("GET", "/api/v1/tasks/*"),
			new AllowedEndpoint("GET", "/api/v1/runs/*"),
			new AllowedEndpoint("GET", "/api/v1/approvals"),
			new AllowedEndpoint("GET", "/api/v1/approvals/*"),
			new AllowedEndpoint("POST", "/api/v1/chat/rejected-sender"));

	private final AntPathMatcher pathMatcher = new AntPathMatcher();
	private final AuditService auditService;
	private final ObjectMapper objectMapper;

	public IntegrationScopeFilter(AuditService auditService, ObjectMapper objectMapper) {
		this.auditService = auditService;
		this.objectMapper = objectMapper;
	}

	@Override
	protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
			@NonNull FilterChain filterChain) throws ServletException, IOException {
		var authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user
				&& user.integrationScoped() && !isAllowed(request)) {
			auditService.record(user.id(), "integration.access_denied", "http_request", null,
					Map.of("method", request.getMethod(), "path", request.getRequestURI()));
			response.setStatus(HttpServletResponse.SC_FORBIDDEN);
			response.setContentType(MediaType.APPLICATION_JSON_VALUE);
			objectMapper.writeValue(response.getWriter(),
					ErrorResponse.of("INTEGRATION_SCOPE_FORBIDDEN", "This service account cannot access this endpoint"));
			return;
		}
		filterChain.doFilter(request, response);
	}

	private boolean isAllowed(HttpServletRequest request) {
		String path = request.getRequestURI();
		return ALLOWED_ENDPOINTS.stream()
				.anyMatch(endpoint -> endpoint.method().equals(request.getMethod())
						&& pathMatcher.match(endpoint.pathPattern(), path));
	}
}
