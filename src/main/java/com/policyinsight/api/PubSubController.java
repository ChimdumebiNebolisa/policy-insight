package com.policyinsight.api;


import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.policyinsight.processing.DocumentJobProcessor;
import com.policyinsight.shared.repository.PolicyJobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Base64;
import java.util.UUID;

/**
 * Controller for handling Pub/Sub push messages.
 *
 * Implements Pub/Sub OIDC/JWT token verification per official Pub/Sub push authentication docs:
 * https://cloud.google.com/pubsub/docs/push#authenticating_push_requests
=======
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
feat/scaffold
 */
@RestController
@RequestMapping("/internal")
public class PubSubController {

    private static final Logger logger = LoggerFactory.getLogger(PubSubController.class);

 backup/main-before-merge-20251231-2142
    private final PubSubTokenVerifier tokenVerifier;
    private final DocumentJobProcessor documentJobProcessor;
    private final PolicyJobRepository policyJobRepository;
    private final ObjectMapper objectMapper;

    @Autowired
    public PubSubController(PubSubTokenVerifier tokenVerifier,
                           ObjectMapper objectMapper,
                           DocumentJobProcessor documentJobProcessor,
                           PolicyJobRepository policyJobRepository) {
        if (documentJobProcessor == null) {
            throw new IllegalStateException("DocumentJobProcessor bean is required but not available. " +
                    "Ensure LocalDocumentProcessingWorker or DocumentProcessingWorker is configured.");
        }
        this.tokenVerifier = tokenVerifier;
        this.documentJobProcessor = documentJobProcessor;
        this.policyJobRepository = policyJobRepository;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
    }

    /**
     * Handles Pub/Sub push messages.
     *
     * Pub/Sub push message format:
     * {
     *   "message": {
     *     "data": "base64-encoded-string",
     *     "messageId": "...",
     *     "attributes": {...},
     *     "publishTime": "..."
     *   },
     *   "subscription": "projects/.../subscriptions/..."
     * }
     *
     * @param authorizationHeader Authorization header with Bearer token
     * @param requestBody Raw request body as string
     * @param request HTTP request
     * @return 204 No Content on success, 401/403 on auth failure
     */
    @PostMapping("/pubsub")
    @Transactional
    public ResponseEntity<Void> handlePubSubMessage(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @RequestBody String requestBody,
            HttpServletRequest request) {

        long startTime = System.currentTimeMillis();
        String pubsubMessageId = null;
        String requestId = null;
        UUID jobId = null;

        try {
            // Verify OIDC/JWT token
            // In cloudrun profile, tokenVerifier should always be available
            // If it's null, this indicates a configuration error - fail fast
            if (tokenVerifier == null) {
                logger.error("Pub/Sub token verifier is not available - this indicates a configuration error");
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }

            if (!tokenVerifier.verifyToken(authorizationHeader)) {
                logger.warn("Pub/Sub push message rejected: token verification failed");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }

            // Parse Pub/Sub push message JSON
            JsonNode rootNode;
            try {
                rootNode = objectMapper.readTree(requestBody);
            } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                logger.error("Invalid Pub/Sub push message: invalid JSON: {}", e.getMessage());
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }

            JsonNode messageNode = rootNode.get("message");
            if (messageNode == null) {
                logger.error("Invalid Pub/Sub push message: missing 'message' field");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }

            // Extract Pub/Sub messageId for correlation
            if (messageNode.has("messageId") && messageNode.get("messageId").isTextual()) {
                pubsubMessageId = messageNode.get("messageId").asText();
            }

            // Extract request_id from attributes (set by upload endpoint)
            JsonNode attributesNode = messageNode.get("attributes");
            if (attributesNode != null && attributesNode.has("request_id") && attributesNode.get("request_id").isTextual()) {
                requestId = attributesNode.get("request_id").asText();
            }

            // Extract and base64 decode message.data
            JsonNode dataNode = messageNode.get("data");
            if (dataNode == null || !dataNode.isTextual()) {
                logger.error("Invalid Pub/Sub push message: missing or invalid 'message.data' field");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }

            String base64Data = dataNode.asText();
            String decodedData;
            try {
                decodedData = new String(Base64.getDecoder().decode(base64Data));
            } catch (IllegalArgumentException e) {
                logger.error("Invalid Pub/Sub push message: invalid base64 data in 'message.data' field");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }
            logger.debug("Decoded Pub/Sub message data: {}", decodedData);

            // Parse decoded data as JSON to extract job_id
            JsonNode payloadNode;
            try {
                payloadNode = objectMapper.readTree(decodedData);
            } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                logger.error("Invalid Pub/Sub push message: invalid JSON in decoded payload: {}", e.getMessage());
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }
            String jobIdStr = null;

            // Try to get job_id from attributes first (preferred)
            if (attributesNode != null && attributesNode.has("job_id") && attributesNode.get("job_id").isTextual()) {
                jobIdStr = attributesNode.get("job_id").asText();
            }

            // Fall back to payload if not in attributes
            if (jobIdStr == null && payloadNode.has("job_id")) {
                jobIdStr = payloadNode.get("job_id").asText();
            }

            if (jobIdStr == null || jobIdStr.isEmpty()) {
                logger.error("Invalid Pub/Sub push message: missing 'job_id' in attributes or payload");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }

            // Parse job UUID
            try {
                jobId = UUID.fromString(jobIdStr);
            } catch (IllegalArgumentException e) {
                logger.error("Invalid job_id format in Pub/Sub message: {}", jobIdStr);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }

            // Set correlation IDs in MDC
            MDC.put("job_id", jobId.toString());
            if (requestId != null && !requestId.isEmpty()) {
                MDC.put("request_id", requestId);
            }
            if (pubsubMessageId != null && !pubsubMessageId.isEmpty()) {
                MDC.put("pubsub_message_id", pubsubMessageId);
            }

            // Atomic idempotency check: try to transition PENDING -> PROCESSING
            int updatedRows = policyJobRepository.updateStatusIfPending(jobId);
            if (updatedRows == 0) {
                // Job is not in PENDING status (already processing, completed, or failed)
                logger.info("SKIP_DUPLICATE: Skipping duplicate processing for job: {} (status not PENDING). request_id: {}, pubsub_message_id: {}",
                        jobId, requestId, pubsubMessageId);
                // Return 204 to acknowledge receipt (idempotent - already processed)
                return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
            }

            // Log START with all correlation IDs
            logger.info("START processing job_id={} request_id={} pubsub_message_id={}", jobId, requestId, pubsubMessageId);

            // Process the job using the document job processor
            // Process synchronously - Pub/Sub will retry if we return 500
            try {
                documentJobProcessor.processDocument(jobId);

                // Calculate duration
                long durationMs = System.currentTimeMillis() - startTime;

                // Get final status from database
                String finalStatus = policyJobRepository.findByJobUuid(jobId)
                        .map(job -> job.getStatus())
                        .orElse("UNKNOWN");

                // Log COMPLETE with all correlation IDs and duration
                logger.info("COMPLETE processing job_id={} request_id={} pubsub_message_id={} duration_ms={} final_status={}",
                        jobId, requestId, pubsubMessageId, durationMs, finalStatus);

                // Return 204 to acknowledge successful processing
                return ResponseEntity.status(HttpStatus.NO_CONTENT).build();

            } catch (Exception e) {
                long durationMs = System.currentTimeMillis() - startTime;
                logger.error("Failed to process job_id={} from Pub/Sub push message. request_id={} pubsub_message_id={} duration_ms={}",
                        jobId, requestId, pubsubMessageId, durationMs, e);
                // Return 500 so Pub/Sub will retry the message
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }

        } catch (IllegalArgumentException e) {
            // Invalid input (UUID format, missing fields) - return 4xx
            logger.error("Invalid Pub/Sub push message: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (Exception e) {
            // Unexpected error - return 5xx
            logger.error("Error processing Pub/Sub push message. job_id: {}, request_id: {}, pubsub_message_id: {}",
                    jobId, requestId, pubsubMessageId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } finally {
            MDC.remove("job_id");
            MDC.remove("request_id");
            MDC.remove("pubsub_message_id");
        }

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
 feat/scaffold
    }
}

