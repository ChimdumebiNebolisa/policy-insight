package com.policyinsight.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Controller for handling Pub/Sub push messages.
 * 
 * TODO: Implement Pub/Sub OIDC/JWT token verification per official Pub/Sub push authentication docs:
 * https://cloud.google.com/pubsub/docs/push#authenticating_push_requests
 * 
 * The endpoint should verify the Authorization header contains a valid JWT token issued by Google.
 */
@RestController
@RequestMapping("/internal")
public class PubSubController {

    private static final Logger logger = LoggerFactory.getLogger(PubSubController.class);

    /**
     * Handles Pub/Sub push messages.
     * 
     * @param request HTTP request containing Pub/Sub message
     * @return 204 No Content on success
     */
    @PostMapping("/pubsub")
    public ResponseEntity<Void> handlePubSubMessage(HttpServletRequest request) {
        // Extract and log X-Request-ID header if present
        String requestId = request.getHeader("X-Request-ID");
        if (requestId != null && !requestId.isEmpty()) {
            logger.info("Received Pub/Sub push message with X-Request-ID: {}", requestId);
            MDC.put("requestId", requestId);
        } else {
            logger.info("Received Pub/Sub push message (no X-Request-ID header)");
        }

        // TODO: Verify Pub/Sub OIDC/JWT token
        // TODO: Extract and process Pub/Sub message payload
        // TODO: Acknowledge message processing

        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}

