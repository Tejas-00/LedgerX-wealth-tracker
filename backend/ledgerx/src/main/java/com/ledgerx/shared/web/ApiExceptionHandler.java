package com.ledgerx.shared.web;

import java.net.URI;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.WebRequest;

import com.ledgerx.portfolio.application.service.IdempotencyConflictException;
import com.ledgerx.portfolio.application.service.InvalidPortfolioException;
import com.ledgerx.security.RequestCorrelationFilter;

import jakarta.validation.ConstraintViolationException;

@RestControllerAdvice
public class ApiExceptionHandler {

	@ExceptionHandler(IdempotencyConflictException.class)
	public ResponseEntity<Map<String, Object>> handleIdempotencyConflict(
			IdempotencyConflictException exception, WebRequest request) {
		return problem(HttpStatus.CONFLICT, "IDEMPOTENCY_CONFLICT", exception.getMessage(), request);
	}

	@ExceptionHandler(InvalidPortfolioException.class)
	public ResponseEntity<Map<String, Object>> handleInvalidPortfolio(
			InvalidPortfolioException exception, WebRequest request) {
		return problem(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_PORTFOLIO", exception.getMessage(), request);
	}

	@ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class,
			MissingRequestHeaderException.class, HttpMessageNotReadableException.class,
			MethodArgumentTypeMismatchException.class})
	public ResponseEntity<Map<String, Object>> handleValidation(Exception exception, WebRequest request) {
		return problem(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Request validation failed", request);
	}

	private ResponseEntity<Map<String, Object>> problem(HttpStatus status, String code, String detail,
			WebRequest request) {
		Object traceAttribute = request.getAttribute(
				RequestCorrelationFilter.TRACE_ID_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
		String traceId = traceAttribute instanceof String value ? value : null;
		if (traceId == null || traceId.isBlank()) {
			traceId = UUID.randomUUID().toString();
		}
		Map<String, Object> body = Map.of(
				"type", URI.create("about:blank").toString(),
				"title", status.getReasonPhrase(),
				"status", status.value(),
				"detail", detail,
				"code", code,
				"traceId", traceId);
		return ResponseEntity.status(status).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(body);
	}
}
