package com.policyinsight.util;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Filter that handles request correlation IDs and Datadog trace correlation.
 * Reads X-Request-ID header (or generates UUID if missing) and adds to MDC for logging.
 * Also propagates Datadog trace/span IDs from MDC (injected by dd-java-agent).
 */
@Component
public class CorrelationIdFilter extends OncePerRequestFilter {

    private static final String REQUEST_ID_HEADER = "X-Request-ID";
    private static final String REQUEST_ID_MDC_KEY = "request_id";
    private static final String CORRELATION_ID_MDC_KEY = "correlationId";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        // Read or generate request ID
        String requestId = request.getHeader(REQUEST_ID_HEADER);
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString();
        }

        // Add to response header
        response.setHeader(REQUEST_ID_HEADER, requestId);

        // Add to MDC for logging (both keys for compatibility)
        MDC.put(REQUEST_ID_MDC_KEY, requestId);
        MDC.put(CORRELATION_ID_MDC_KEY, requestId);

        // Note: Datadog trace_id and span_id are automatically injected by dd-java-agent
        // into MDC when DD_LOGS_INJECTION=true. They will be picked up by logback-spring.xml.

        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(REQUEST_ID_MDC_KEY);
            MDC.remove(CORRELATION_ID_MDC_KEY);
        }
    }
}

