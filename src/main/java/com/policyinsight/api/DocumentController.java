package com.policyinsight.api;

import com.policyinsight.api.messaging.JobPublisher;
import com.policyinsight.api.storage.StorageService;
import com.policyinsight.api.validation.PdfValidator;
import com.policyinsight.security.RateLimitService;
import com.policyinsight.security.TokenService;
import com.policyinsight.shared.model.PolicyJob;
import com.policyinsight.shared.repository.PolicyJobRepository;
import com.policyinsight.observability.TracingServiceInterface;
import com.policyinsight.util.Strings;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.ModelAndView;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Controller
@RequestMapping("/api/documents")
@Tag(name = "Documents", description = "Document upload and status endpoints")
public class DocumentController {

    private static final String HX_REQUEST_HEADER = "HX-Request";

    private static final Logger logger = LoggerFactory.getLogger(DocumentController.class);
    private static final long MAX_FILE_SIZE_BYTES = 50 * 1024 * 1024; // 50 MB
    private static final String PDF_CONTENT_TYPE = "application/pdf";

    private final StorageService storageService;
    private final JobPublisher jobPublisher;
    private final PolicyJobRepository policyJobRepository;
    private final TracingServiceInterface tracingService;
    private final TokenService tokenService;
    private final RateLimitService rateLimitService;
    private final PdfValidator pdfValidator;

    public DocumentController(
            StorageService storageService,
            JobPublisher jobPublisher,
            PolicyJobRepository policyJobRepository,
            TokenService tokenService,
            RateLimitService rateLimitService,
            PdfValidator pdfValidator,
            @Autowired(required = false) TracingServiceInterface tracingService) {
        this.storageService = storageService;
        this.jobPublisher = jobPublisher;
        this.policyJobRepository = policyJobRepository;
        this.tokenService = tokenService;
        this.rateLimitService = rateLimitService;
        this.pdfValidator = pdfValidator;
        this.tracingService = tracingService;
    }

    @PostMapping("/upload")
    @Operation(summary = "Upload a PDF document for analysis",
               description = "Accepts a PDF file and returns a job ID for tracking the analysis process")
    @Transactional
    public Object uploadDocument(
            @Parameter(description = "PDF file to upload (max 50 MB)")
            @RequestParam("file") MultipartFile file,
            HttpServletRequest request,
            HttpServletResponse response) {

        // Check if this is an htmx request
        boolean isHtmxRequest = "true".equals(request.getHeader(HX_REQUEST_HEADER));

        // Create span for upload operation
        Span uploadSpan = null;
        if (tracingService != null) {
            uploadSpan = tracingService.spanBuilder("upload")
                    .setAttribute("stage", "upload")
                    .setAttribute("filename", Strings.safe(file.getOriginalFilename()))
                    .setAttribute("file_size_bytes", file.getSize())
                    .setAttribute("content_type", Strings.safe(file.getContentType()))
                    .startSpan();
        }

        logger.info("Received upload request: filename={}, size={}, contentType={}",
                file.getOriginalFilename(), file.getSize(), file.getContentType());

        try (io.opentelemetry.context.Scope scope = uploadSpan != null ? uploadSpan.makeCurrent() : null) {

        // Check rate limit
        if (rateLimitService.checkUploadRateLimit(request)) {
            logger.warn("Upload rate limit exceeded for IP: {}", rateLimitService.extractClientIp(request));
            try {
                response.setStatus(429); // HTTP 429 Too Many Requests
                response.setContentType("application/json");
                response.setCharacterEncoding("UTF-8");
                response.getWriter().write(String.format(
                    "{\"error\":\"RATE_LIMIT_EXCEEDED\",\"message\":\"Upload rate limit exceeded. Please try again later.\",\"timestamp\":\"%s\"}",
                    java.time.Instant.now()
                ));
            } catch (IOException e) {
                logger.error("Failed to write rate limit error response", e);
            }
            return null; // Response already written
        }

        // Validate file is present
        if (file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }

        // Validate file size (50 MB max)
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new IllegalArgumentException(
                    String.format("File size (%d bytes) exceeds maximum allowed size (%d bytes / 50 MB)",
                            file.getSize(), MAX_FILE_SIZE_BYTES));
        }

        // Validate content type is PDF
        String contentType = file.getContentType();
        if (contentType == null || !contentType.equals(PDF_CONTENT_TYPE)) {
            throw new IllegalArgumentException(
                    String.format("Invalid file type: %s. Only PDF files (application/pdf) are allowed.", contentType));
        }

        // Validate PDF magic bytes (%PDF-) - security: prevent malicious files
        try {
            if (!pdfValidator.validateMagicBytes(file.getInputStream())) {
                throw new IllegalArgumentException("File does not appear to be a valid PDF (magic bytes validation failed)");
            }
        } catch (IOException e) {
            logger.error("Failed to validate PDF magic bytes", e);
            throw new IllegalArgumentException("Failed to validate PDF file: " + e.getMessage());
        }

            // Generate job UUID and request ID for correlation
            UUID jobId = UUID.randomUUID();
            String requestId = UUID.randomUUID().toString();
            // Force filename to document.pdf (ignore user-provided filename for security)
            String filename = "document.pdf";

            // Add correlation IDs to MDC for logging
            String jobIdStr = Strings.safe(jobId.toString());
            MDC.put("job_id", jobIdStr);
            MDC.put("request_id", requestId);

            if (uploadSpan != null) {
                uploadSpan.setAttribute("job_id", jobIdStr);
                uploadSpan.setAttribute("document_id", jobIdStr);
            }

            try {
                // Upload to storage
                String storagePath = storageService.uploadFile(jobId, filename, file.getInputStream(), contentType);
                logger.info("File uploaded to storage: {}", storagePath);
                if (uploadSpan != null) {
                    uploadSpan.setAttribute("storage_path", Strings.safe(storagePath));
                }

                // Generate capability token
                String token = tokenService.generateToken();
                String tokenHmac = tokenService.computeHmac(token);

                // Create job record in database
                PolicyJob job = new PolicyJob(jobId);
                job.setStatus("PENDING");
                job.setPdfGcsPath(storagePath);
                job.setPdfFilename(filename);
                job.setFileSizeBytes(file.getSize());
                job.setAccessTokenHmac(tokenHmac);
                job = policyJobRepository.save(job);
                logger.info("Job record created in database: jobId={}", jobId);

                // Publish Pub/Sub message with request_id for correlation
                jobPublisher.publishJobQueued(jobId, storagePath, requestId);
                logger.info("Job queued event published for job: {}, requestId: {}", jobId, requestId);

                if (uploadSpan != null) {
                    uploadSpan.setStatus(StatusCode.OK);
                    uploadSpan.setAttribute("status", "PENDING");
                }

                // Set cookie for browser-based clients (HTMX)
                if (isHtmxRequest) {
                    setTokenCookie(response, jobId, token, request);
                }

                // Return HTML fragment for htmx, JSON for API clients
                if (isHtmxRequest) {
                    ModelAndView mav = new ModelAndView("fragments/upload-started");
                    mav.addObject("jobId", jobIdStr);
                    // DO NOT render token in HTML fragment (security requirement)
                    return mav;
                } else {
                    // Build JSON response with token for API clients
                    Map<String, Object> jsonResponse = new HashMap<>();
                    jsonResponse.put("jobId", jobIdStr);
                    jsonResponse.put("token", token); // Return token once in JSON
                    jsonResponse.put("status", "PENDING");
                    jsonResponse.put("statusUrl", "/api/documents/" + jobId + "/status");
                    jsonResponse.put("message", "Document uploaded successfully. Processing will begin shortly.");
                    return ResponseEntity.status(HttpStatus.ACCEPTED).body(jsonResponse);
                }

            } catch (IOException e) {
                logger.error("Failed to upload file to storage for job: {}", jobId, e);
                if (uploadSpan != null) {
                    uploadSpan.setStatus(StatusCode.ERROR);
                    uploadSpan.setAttribute("error", true);
                    uploadSpan.setAttribute("error.message", Strings.safe(e.getMessage()));
                    uploadSpan.recordException(e);
                }
                throw new RuntimeException("Failed to upload file to storage", e);
            } catch (Exception e) {
                logger.error("Failed to queue job for processing: {}", jobId, e);
                if (uploadSpan != null) {
                    uploadSpan.setStatus(StatusCode.ERROR);
                    uploadSpan.setAttribute("error", true);
                    uploadSpan.setAttribute("error.message", Strings.safe(e.getMessage()));
                    uploadSpan.recordException(e);
                }
                throw new RuntimeException("Failed to queue job for processing", e);
            } finally {
                if (uploadSpan != null) {
                    uploadSpan.end();
                }
                MDC.remove("job_id");
                MDC.remove("request_id");
            }
        }
    }

    @GetMapping("/{id}/status")
    @Operation(summary = "Get document processing status",
               description = "Returns the current status of a document analysis job")
    public Object getDocumentStatus(
            @Parameter(description = "Job ID returned from upload endpoint")
            @PathVariable("id") String id,
            HttpServletRequest request,
            HttpServletResponse response) {

        // Check if this is an htmx request
        boolean isHtmxRequest = "true".equals(request.getHeader(HX_REQUEST_HEADER));

        UUID jobUuid;
        try {
            jobUuid = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid job ID format: " + id);
        }

        PolicyJob job = policyJobRepository.findByJobUuid(jobUuid)
                .orElseThrow(() -> new IllegalArgumentException("Job not found: " + id));

        String status = job.getStatus();
        String jobIdStr = job.getJobUuid().toString();

        // Return HTML fragment for htmx, JSON for API clients
        if (isHtmxRequest) {
            ModelAndView mav = new ModelAndView("fragments/job-status");
            mav.addObject("jobId", jobIdStr);
            mav.addObject("status", status);

            // Add status-specific fields
            if ("SUCCESS".equals(status)) {
                mav.addObject("message", "Analysis completed successfully");
                // Set HX-Redirect header for htmx to redirect to report page
                // Uses the same 'id' (jobUuid) that was returned from upload endpoint as "jobId"
                response.setHeader("HX-Redirect", "/documents/" + id + "/report");
            } else if ("FAILED".equals(status)) {
                mav.addObject("errorMessage", job.getErrorMessage());
                mav.addObject("message", "Analysis failed: " + (job.getErrorMessage() != null ? job.getErrorMessage() : "Unknown error"));
            } else if ("PROCESSING".equals(status)) {
                mav.addObject("message", "Document is being processed");
            } else {
                mav.addObject("message", "Job is queued for processing");
            }

            return mav;
        } else {
            // Build JSON response
            Map<String, Object> jsonResponse = new HashMap<>();
            jsonResponse.put("jobId", jobIdStr);
            jsonResponse.put("status", status);

            // Add status-specific fields
            if ("SUCCESS".equals(status)) {
                jsonResponse.put("reportUrl", "/documents/" + id + "/report");
                jsonResponse.put("message", "Analysis completed successfully");
            } else if ("FAILED".equals(status)) {
                jsonResponse.put("errorMessage", job.getErrorMessage());
                jsonResponse.put("message", "Analysis failed: " + (job.getErrorMessage() != null ? job.getErrorMessage() : "Unknown error"));
                // Include error code if present
                if (job.getLastErrorCode() != null) {
                    jsonResponse.put("errorCode", job.getLastErrorCode());
                }
            } else if ("PROCESSING".equals(status)) {
                jsonResponse.put("message", "Document is being processed");
                // Include attempt count and lease info for visibility
                if (job.getAttemptCount() != null) {
                    jsonResponse.put("attemptCount", job.getAttemptCount());
                }
                if (job.getLeaseExpiresAt() != null) {
                    jsonResponse.put("leaseExpiresAt", job.getLeaseExpiresAt().toString());
                }
            } else {
                jsonResponse.put("message", "Job is queued for processing");
            }

            return ResponseEntity.ok(jsonResponse);
        }
    }

    /**
     * Sets the job token cookie with proper security attributes.
     * Secure flag is set only when request is HTTPS or X-Forwarded-Proto=https (Cloud Run).
     */
    private void setTokenCookie(HttpServletResponse response, UUID jobId, String token, HttpServletRequest request) {
        String cookieName = "pi_job_token_" + jobId.toString();

        // Set Secure flag only for HTTPS requests
        boolean isSecure = request.isSecure() ||
                          "https".equalsIgnoreCase(request.getHeader("X-Forwarded-Proto"));

        // Build Set-Cookie header manually to include SameSite attribute
        // Format: name=value; HttpOnly; Secure; SameSite=Strict; Path=/; Max-Age=2592000
        StringBuilder cookieValue = new StringBuilder();
        cookieValue.append(cookieName).append("=").append(token);
        cookieValue.append("; HttpOnly");
        if (isSecure) {
            cookieValue.append("; Secure");
        }
        cookieValue.append("; SameSite=Strict");
        cookieValue.append("; Path=/");
        cookieValue.append("; Max-Age=").append(30 * 24 * 60 * 60); // 30 days

        response.setHeader("Set-Cookie", cookieValue.toString());
        logger.debug("Set token cookie for job: {}", jobId);
    }
}

