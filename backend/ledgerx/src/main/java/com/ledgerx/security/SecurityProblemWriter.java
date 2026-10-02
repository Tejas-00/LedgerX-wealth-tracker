package com.ledgerx.security;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class SecurityProblemWriter {

	private final ObjectMapper objectMapper;

	public SecurityProblemWriter(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	public void write(HttpServletRequest request, HttpServletResponse response, HttpStatus status,
			String code, String title) throws IOException {
		Object traceAttribute = request.getAttribute(RequestCorrelationFilter.TRACE_ID_ATTRIBUTE);
		String traceId = traceAttribute instanceof String value ? value : UUID.randomUUID().toString();

		response.setStatus(status.value());
		response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
		objectMapper.writeValue(response.getOutputStream(), Map.of(
				"type", "about:blank",
				"title", title,
				"status", status.value(),
				"detail", title,
				"code", code,
				"traceId", traceId));
	}
}
