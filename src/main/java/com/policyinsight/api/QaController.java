package com.policyinsight.api;

import com.policyinsight.processing.QaService;
import com.policyinsight.security.RateLimitService;
import com.policyinsight.security.TokenService;
import com.policyinsight.shared.dto.QuestionRequest;
import com.policyinsight.shared.dto.QuestionResponse;
import com.policyinsight.shared.model.PolicyJob;
import com.policyinsight.shared.model.QaInteraction;
import com.policyinsight.shared.repository.PolicyJobRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Controller for Q&A endpoints.
 * Handles grounded question-answering with cite-or-abstain enforcement.
 */
@RestController
@RequestMapping("/api/questions")
@Tag(name = "Q&A", description = "Grounded question-answering endpoints")
public class QaController {

    private static final Logger logger = LoggerFactory.getLogger(QaController.class);
    private static final String TOKEN_HEADER = "X-Job-Token";
    private static final String COOKIE_PREFIX = "pi_job_token_";

    private final QaService qaService;
    private final PolicyJobRepository policyJobRepository;
    private final TokenService tokenService;
    private final RateLimitService rateLimitService;

    public QaController(QaService qaService, PolicyJobRepository policyJobRepository, TokenService tokenService, RateLimitService rateLimitService) {
        this.qaService = qaService;
        this.policyJobRepository = policyJobRepository;
        this.tokenService = tokenService;
        this.rateLimitService = rateLimitService;
    }

    /**
     * Submit a question for grounded Q&A.
     * Returns citation-backed answer or abstention.
     *
     * PRD Constraints:
     * - Up to 3 questions per document session
     * - 3-second timeout per answer
     * - Question >500 chars returns 400
     */
    @PostMapping(consumes = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_FORM_URLENCODED_VALUE})
    @Operation(summary = "Submit a question for grounded Q&A",
               description = "Returns citation-backed answer or abstention. Max 3 questions per document session.")
    public ResponseEntity<?> submitQuestion(
            @Parameter(description = "Question request with document_id and question")
            @RequestParam(value = "document_id", required = false) String documentIdStr,
            @RequestParam(value = "question", required = false) String questionText,
            @RequestHeader(value = "HX-Request", required = false) String hxRequest,
            @RequestHeader(value = TOKEN_HEADER, required = false) String tokenHeader,
            @Valid @RequestBody(required = false) QuestionRequest request,
            HttpServletRequest httpRequest) {

        // Handle both form data (htmx) and JSON
        UUID jobUuid;
        String question;

        if (request != null && request.getDocumentId() != null) {
            // JSON request
            jobUuid = request.getDocumentId();
            question = request.getQuestion();
        } else if (documentIdStr != null && questionText != null) {
            // Form data request (htmx)
            try {
                jobUuid = UUID.fromString(documentIdStr);
            } catch (IllegalArgumentException e) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(buildErrorResponse("Invalid document ID format: " + documentIdStr));
            }
            question = questionText;
        } else {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(buildErrorResponse("document_id and question are required"));
        }

        // Validate and sanitize question
        if (question == null || question.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(buildErrorResponse("Question cannot be blank"));
        }

        // Strip control characters (security: prevent injection)
        question = question.replaceAll("[\\p{Cntrl}&&[^\r\n\t]]", "").trim();

        // Enforce max length (500 characters)
        if (question.length() > 500) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(buildErrorResponse("Question must not exceed 500 characters"));
        }

        logger.info("Received Q&A request: documentId={}, questionLength={}",
                jobUuid, question.length());

        // Check per-IP rate limit
        if (rateLimitService.checkQaRateLimit(httpRequest)) {
            logger.warn("Q&A rate limit exceeded for IP: {}", rateLimitService.extractClientIp(httpRequest));
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(buildErrorResponse("Q&A rate limit exceeded. Please try again later."));
        }

        // Verify document exists and is completed
        PolicyJob job = policyJobRepository.findByJobUuid(jobUuid).orElse(null);
        if (job == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(buildErrorResponse("Document not found: " + jobUuid));
        }

        // Validate token for JSON requests (form data requests are validated by filter)
        if (request != null && request.getDocumentId() != null) {
            // JSON request - validate token from header
            String token = tokenHeader;
            if (token == null || token.isBlank()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(buildErrorResponse("Missing job token"));
            }
            if (job.getAccessTokenHmac() == null ||
                !tokenService.verifyToken(token, job.getAccessTokenHmac())) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(buildErrorResponse("Invalid job token"));
            }
        } else {
            // Form data request - validate token from cookie (filter already checked, but double-check)
            String token = extractTokenFromCookie(httpRequest, jobUuid);
            if (token == null || token.isBlank()) {
                token = tokenHeader; // Fallback to header
            }
            if (token == null || token.isBlank() ||
                job.getAccessTokenHmac() == null ||
                !tokenService.verifyToken(token, job.getAccessTokenHmac())) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(buildErrorResponse("Missing or invalid job token"));
            }
        }

        // Check per-job question quota (server-side enforcement)
        long questionCount = qaService.getQuestionCount(jobUuid);
        int maxPerJob = rateLimitService.getQaMaxPerJob();
        if (questionCount >= maxPerJob) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(buildErrorResponse(String.format("Maximum question limit (%d) reached for this document session", maxPerJob)));
        }

        try {
            // Process question
            QaService.QaResult result = qaService.answerQuestion(jobUuid, question);

            // Build response
            QuestionResponse response = new QuestionResponse();
            response.setJobId(jobUuid);
            response.setQuestion(result.getQuestion());
            response.setAnswer(result.getAnswer());
            response.setConfidence(result.isGrounded() ? "CONFIDENT" : "ABSTAINED");

            // Convert citations
            List<QuestionResponse.Citation> citations = result.getCitations().stream()
                    .map(c -> new QuestionResponse.Citation(
                            c.getChunkId(),
                            c.getPageNumber(),
                            c.getTextSpan()))
                    .collect(Collectors.toList());
            response.setCitations(citations);

            logger.info("Q&A response generated: jobUuid={}, grounded={}, latencyMs={}",
                    jobUuid, result.isGrounded(), result.getLatencyMs());

            // Return HTML fragment for htmx requests, JSON for API requests
            if (hxRequest != null && "true".equals(hxRequest)) {
                return ResponseEntity.ok()
                        .contentType(MediaType.TEXT_HTML)
                        .body(buildHtmlFragment(response));
            } else {
                return ResponseEntity.ok(response);
            }

        } catch (IllegalArgumentException e) {
            logger.warn("Invalid Q&A request: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(buildErrorResponse(e.getMessage()));
        } catch (Exception e) {
            logger.error("Failed to process Q&A request: jobUuid={}", jobUuid, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(buildErrorResponse("Failed to process question. Please try again later."));
        }
    }


    /**
     * Get all Q&A interactions for a document.
     */
    @GetMapping("/{document_id}")
    @Operation(summary = "Get all Q&A interactions for a document",
               description = "Returns list of all questions and answers for the document")
    public ResponseEntity<?> getQaInteractions(
            @Parameter(description = "Document ID")
            @PathVariable("document_id") String documentIdStr,
            @RequestHeader(value = TOKEN_HEADER, required = false) String tokenHeader,
            HttpServletRequest request) {

        UUID jobUuid;
        try {
            jobUuid = UUID.fromString(documentIdStr);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(buildErrorResponse("Invalid document ID format: " + documentIdStr));
        }

        // Validate token (filter handles most cases, but double-check here)
        PolicyJob job = policyJobRepository.findByJobUuid(jobUuid).orElse(null);
        if (job == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(buildErrorResponse("Document not found: " + jobUuid));
        }

        String token = extractTokenFromCookie(request, jobUuid);
        if (token == null) {
            token = tokenHeader;
        }
        if (token == null || token.isBlank() ||
            job.getAccessTokenHmac() == null ||
            !tokenService.verifyToken(token, job.getAccessTokenHmac())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(buildErrorResponse("Missing or invalid job token"));
        }

        List<QaInteraction> interactions = qaService.getQaInteractions(jobUuid);

        List<QuestionResponse> responses = interactions.stream()
                .map(interaction -> {
                    QuestionResponse response = new QuestionResponse();
                    response.setJobId(interaction.getJobUuid());
                    response.setQuestion(interaction.getQuestion());
                    response.setAnswer(interaction.getAnswer());
                    response.setConfidence(interaction.getConfidence());
                    return response;
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(responses);
    }

    private Map<String, Object> buildErrorResponse(String message) {
        Map<String, Object> error = new HashMap<>();
        error.put("error", message);
        error.put("timestamp", java.time.Instant.now().toString());
        return error;
    }

    /**
     * Build HTML fragment for htmx response.
     */
    private String buildHtmlFragment(QuestionResponse response) {
        StringBuilder html = new StringBuilder();
        html.append("<div style=\"margin-bottom: 1.5rem; padding: 1rem; background-color: #f8f9fa; border-radius: 4px; border-left: 3px solid ");
        html.append("CONFIDENT".equals(response.getConfidence()) ? "#27ae60" : "#e74c3c");
        html.append(";\">\n");
        html.append("  <p><strong>Q:</strong> ").append(escapeHtml(response.getQuestion())).append("</p>\n");
        html.append("  <p><strong>A:</strong> ").append(escapeHtml(response.getAnswer())).append("</p>\n");
        if (response.getCitations() != null && !response.getCitations().isEmpty()) {
            html.append("  <p style=\"font-size: 0.9rem; color: #666;\"><strong>Citations:</strong> ");
            for (int i = 0; i < response.getCitations().size(); i++) {
                QuestionResponse.Citation citation = response.getCitations().get(i);
                html.append("<a href=\"#chunk-").append(citation.getChunkId())
                     .append("\" style=\"color: #3498db;\">Page ").append(citation.getPageNumber()).append("</a>");
                if (i < response.getCitations().size() - 1) {
                    html.append(", ");
                }
            }
            html.append("</p>\n");
        }
        html.append("  <p style=\"color: ");
        html.append("CONFIDENT".equals(response.getConfidence()) ? "#27ae60" : "#e74c3c");
        html.append("; font-size: 0.9rem;\"><em>");
        html.append("CONFIDENT".equals(response.getConfidence()) ? "Grounded answer with citations" : "Insufficient evidence");
        html.append("</em></p>\n");
        html.append("</div>\n");
        return html.toString();
    }

    private String escapeHtml(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&#39;");
    }

    private String extractTokenFromCookie(HttpServletRequest request, UUID jobUuid) {
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
}

