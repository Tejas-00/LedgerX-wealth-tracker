package com.ledgerx.security;

import java.io.IOException;
import java.util.UUID;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestCorrelationFilter extends OncePerRequestFilter {

	public static final String TRACE_ID_ATTRIBUTE = RequestCorrelationFilter.class.getName() + ".traceId";
	public static final String TRACE_ID_HEADER = "X-Trace-Id";

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		String traceId = UUID.randomUUID().toString();
		request.setAttribute(TRACE_ID_ATTRIBUTE, traceId);
		response.setHeader(TRACE_ID_HEADER, traceId);
		MDC.put("traceId", traceId);
		try {
			filterChain.doFilter(request, response);
		} finally {
			MDC.remove("traceId");
		}
	}
}
