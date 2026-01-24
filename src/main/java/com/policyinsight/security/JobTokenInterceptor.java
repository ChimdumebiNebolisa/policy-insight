package com.policyinsight.security;

import com.policyinsight.shared.model.PolicyJob;
import com.policyinsight.shared.repository.PolicyJobRepository;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Interceptor that enforces capability-token security for protected endpoints.
 * Validates tokens from cookies or headers and applies CSRF protection for state-changing operations.
 */
@Component
public class JobTokenInterceptor implements HandlerInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(JobTokenInterceptor.class);

    private static final String TOKEN_HEADER = "X-Job-Token";
    private static final String COOKIE_PREFIX = "pi_job_token_";

    // Protected endpoints that need special handling (UUID in body/params)
    private static final List<String> PROTECTED_SPECIAL_PATHS = List.of(
        "/api/questions"
    );

    private final TokenService tokenService;
    private final PolicyJobRepository policyJobRepository;
    private final List<String> allowedOrigins;

    public JobTokenInterceptor(
            TokenService tokenService,
            PolicyJobRepository policyJobRepository,
            @Value("${app.security.allowed-origins:http://localhost:8080}") String allowedOrigins) {
        this.tokenService = tokenService;
        this.policyJobRepository = policyJobRepository;
        // Parse comma-separated origins
        this.allowedOrigins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    @Override
    public boolean preHandle(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler) throws Exception {

        String path = request.getRequestURI();
        String method = request.getMethod();

        // Extract job UUID from path variables when available
        Optional<UUID> jobUuidOpt = extractJobUuid(request);

        // For /api/questions POST, handle specially (UUID may be in body for JSON)
        if (PROTECTED_SPECIAL_PATHS.contains(path) && "POST".equals(method)) {
            if (jobUuidOpt.isEmpty()) {
                jobUuidOpt = extractJobUuidFromParams(request);
            }
            if (jobUuidOpt.isEmpty()) {
                // For JSON requests, UUID is in body - we can't read it here easily
                // Check if token is in header - if yes, allow through and controller validates
                String tokenHeader = request.getHeader(TOKEN_HEADER);
                if (tokenHeader == null || tokenHeader.isBlank()) {
                    logger.debug("Missing token header for /api/questions POST");
                    sendUnauthorized(response, "Missing job token");
                    return false;
                }
                // Check CSRF
                if (!validateCsrf(request)) {
                    logger.debug("CSRF validation failed: path={}, method={}", path, method);
                    sendForbidden(response, "CSRF validation failed");
                    return false;
                }
                // Allow through - controller will validate token matches document_id
                return true;
            }
            // Fall through to normal validation with UUID from params
        }

        if (jobUuidOpt.isEmpty()) {
            // No job UUID found in path - allow through
            return true;
        }

        UUID jobUuid = jobUuidOpt.get();

        // Extract token from header or cookie
        String token = extractToken(request, jobUuid);
        if (token == null) {
            logger.debug("Missing token for protected endpoint: path={}, jobUuid={}", path, jobUuid);
            sendUnauthorized(response, "Missing or invalid job token");
            return false;
        }

        // Validate token
        PolicyJob job = policyJobRepository.findByJobUuid(jobUuid).orElse(null);
        if (job == null) {
            logger.debug("Job not found: jobUuid={}", jobUuid);
            sendNotFound(response, "Job not found");
            return false;
        }

        if (job.getAccessTokenHmac() == null) {
            logger.debug("Job has no token HMAC: jobUuid={}", jobUuid);
            sendUnauthorized(response, "Job token not configured");
            return false;
        }

        if (!tokenService.verifyToken(token, job.getAccessTokenHmac())) {
            logger.debug("Token validation failed: path={}, jobUuid={}", path, jobUuid);
            sendUnauthorized(response, "Invalid job token");
            return false;
        }

        // CSRF protection for state-changing methods (except /internal/pubsub)
        if (isStateChangingMethod(method) && !path.startsWith("/internal/pubsub")) {
            if (!validateCsrf(request)) {
                logger.debug("CSRF validation failed: path={}, method={}", path, method);
                sendForbidden(response, "CSRF validation failed");
                return false;
            }
        }

        // Token valid, proceed
        return true;
    }

    private Optional<UUID> extractJobUuid(HttpServletRequest request) {
        Object attribute = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        if (attribute instanceof Map<?, ?> uriVariables) {
            Object id = uriVariables.get("id");
            if (id == null) {
                id = uriVariables.get("document_id");
            }
            if (id != null) {
                String uuidStr = id.toString();
                if (!uuidStr.isBlank()) {
                    try {
                        return Optional.of(UUID.fromString(uuidStr));
                    } catch (IllegalArgumentException e) {
                        logger.debug("Invalid UUID in path variables: {}", uuidStr);
                    }
                }
            }
        }
        return Optional.empty();
    }

    private Optional<UUID> extractJobUuidFromParams(HttpServletRequest request) {
        String documentIdParam = request.getParameter("document_id");
        if (documentIdParam != null && !documentIdParam.isBlank()) {
            try {
                return Optional.of(UUID.fromString(documentIdParam));
            } catch (IllegalArgumentException e) {
                logger.debug("Invalid document_id parameter: {}", documentIdParam);
            }
        }
        return Optional.empty();
    }

    private String extractToken(HttpServletRequest request, UUID jobUuid) {
        // Try header first
        String headerToken = request.getHeader(TOKEN_HEADER);
        if (headerToken != null && !headerToken.isBlank()) {
            return headerToken;
        }

        // Try cookie
        String cookieName = COOKIE_PREFIX + jobUuid.toString();
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if (cookieName.equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }

        return null;
    }

    private boolean isStateChangingMethod(String method) {
        return "POST".equals(method) || "PUT".equals(method) ||
               "PATCH".equals(method) || "DELETE".equals(method);
    }

    private boolean validateCsrf(HttpServletRequest request) {
        String origin = request.getHeader("Origin");
        if (origin != null && !origin.isBlank()) {
            return isAllowedOrigin(origin);
        }

        String referer = request.getHeader("Referer");
        if (referer != null && !referer.isBlank()) {
            try {
                URI refererUri = new URI(referer);
                String refererOrigin = refererUri.getScheme() + "://" + refererUri.getAuthority();
                return isAllowedOrigin(refererOrigin);
            } catch (URISyntaxException e) {
                logger.debug("Invalid Referer header: {}", referer);
                return false;
            }
        }

        // No Origin or Referer - deny (intentional)
        return false;
    }

    private boolean isAllowedOrigin(String origin) {
        return allowedOrigins.stream().anyMatch(allowed -> {
            // Exact match or allow subdomains
            return origin.equals(allowed) || origin.startsWith(allowed.replace("localhost", ""));
        });
    }

    private void sendUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(String.format(
            "{\"error\":\"UNAUTHORIZED\",\"message\":\"%s\",\"timestamp\":\"%s\"}",
            message, java.time.Instant.now()
        ));
    }

    private void sendForbidden(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(String.format(
            "{\"error\":\"FORBIDDEN\",\"message\":\"%s\",\"timestamp\":\"%s\"}",
            message, java.time.Instant.now()
        ));
    }

    private void sendNotFound(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(String.format(
            "{\"error\":\"NOT_FOUND\",\"message\":\"%s\",\"timestamp\":\"%s\"}",
            message, java.time.Instant.now()
        ));
    }
}

