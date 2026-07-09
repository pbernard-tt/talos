package dev.talos.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(ApiException.class)
	public ResponseEntity<ErrorResponse> handleApiException(ApiException ex) {
		return ResponseEntity.status(ex.getStatus())
				.body(ErrorResponse.of(ex.getCode(), ex.getMessage(), ex.getDetails()));
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
		Map<String, Object> fieldErrors = new LinkedHashMap<>();
		ex.getBindingResult().getFieldErrors()
				.forEach(fe -> fieldErrors.put(fe.getField(), fe.getDefaultMessage()));
		return ResponseEntity.status(HttpStatus.BAD_REQUEST)
				.body(ErrorResponse.of("VALIDATION_ERROR", "Request validation failed", fieldErrors));
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ErrorResponse> handleMalformedRequest(HttpMessageNotReadableException ex) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST)
				.body(ErrorResponse.of("MALFORMED_REQUEST", "Request body could not be read"));
	}

	@ExceptionHandler(NoResourceFoundException.class)
	public ResponseEntity<ErrorResponse> handleNotFound(NoResourceFoundException ex) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND)
				.body(ErrorResponse.of("NOT_FOUND", "Resource not found"));
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
		log.error("Unhandled exception", ex);
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body(ErrorResponse.of("INTERNAL_ERROR", "An unexpected error occurred"));
	}
}
