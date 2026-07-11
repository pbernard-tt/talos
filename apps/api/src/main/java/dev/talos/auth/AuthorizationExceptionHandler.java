package dev.talos.auth;

import dev.talos.audit.AuditService;
import dev.talos.common.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * Section 16 Phase 15: the {@code @PreAuthorize} denial path (see SecurityConfig, TaskController,
 * ProjectController, RunController, ApprovalController, IntegrationController, MemoryController,
 * UserController). {@code AuthorizationDeniedException} is thrown from inside the controller
 * method invocation (Spring AOP around the handler), so it never reaches a filter-chain-level
 * {@code AccessDeniedHandler} -- Spring MVC's own exception resolution sees it first (found live:
 * a handler wired via {@code exceptionHandling().accessDeniedHandler(...)} was simply never
 * invoked). A separate {@code @RestControllerAdvice} here, not dev.talos.common.GlobalExceptionHandler,
 * keeps the low-level common package from depending back on dev.talos.auth/dev.talos.audit types.
 * Mirrors IntegrationScopeFilter's shape: 403 plus an audit row (Phase 15 acceptance: "a VIEWER
 * attempting an approval receives 403 plus an audit row").
 */
@RestControllerAdvice
public class AuthorizationExceptionHandler {

	private final AuditService auditService;

	public AuthorizationExceptionHandler(AuditService auditService) {
		this.auditService = auditService;
	}

	@ExceptionHandler(AuthorizationDeniedException.class)
	public ResponseEntity<ErrorResponse> handleAuthorizationDenied(AuthorizationDeniedException ex,
			HttpServletRequest request) {
		var authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user) {
			auditService.record(user.id(), "role.access_denied", "http_request", null,
					Map.of("method", request.getMethod(), "path", request.getRequestURI(), "role", user.role().name()));
		}
		return ResponseEntity.status(HttpStatus.FORBIDDEN)
				.body(ErrorResponse.of("FORBIDDEN", "Your role does not permit this action"));
	}
}
