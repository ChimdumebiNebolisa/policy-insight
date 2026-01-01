package com.policyinsight.api;

import com.policyinsight.api.messaging.JobPublisher;
import com.policyinsight.api.storage.StorageService;
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

    public DocumentController(
            StorageService storageService,
            JobPublisher jobPublisher,
            PolicyJobRepository policyJobRepository,
            @Autowired(required = false) TracingServiceInterface tracingService) {
        this.storageService = storageService;
        this.jobPublisher = jobPublisher;
        this.policyJobRepository = policyJobRepository;
        this.tracingService = tracingService;
    }

    @PostMapping("/upload")
    @Operation(summary = "Upload a PDF document for analysis",
               description = "Accepts a PDF file and returns a job ID for tracking the analysis process")
    @Transactional
    public Object uploadDocument(
            @Parameter(description = "PDF file to upload (max 50 MB)")
            @RequestParam("file") MultipartFile file,
            HttpServletRequest request) {

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

            // Generate job UUID and request ID for correlation
            UUID jobId = UUID.randomUUID();
            String requestId = UUID.randomUUID().toString();
            String filename = file.getOriginalFilename();
            if (filename == null || filename.isEmpty()) {
                filename = "document.pdf";
            }


            // Add correlation IDs to MDC for logging
=======
            // Add job_id to MDC for logging
feat/scaffold
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

                // Create job record in database
                PolicyJob job = new PolicyJob(jobId);
                job.setStatus("PENDING");
                job.setPdfGcsPath(storagePath);
                job.setPdfFilename(filename);
                job.setFileSizeBytes(file.getSize());
                job = policyJobRepository.save(job);
                logger.info("Job record created in database: jobId={}", jobId);

                // Publish Pub/Sub message with request_id for correlation
                jobPublisher.publishJobQueued(jobId, storagePath, requestId);
                logger.info("Job queued event published for job: {}, requestId: {}", jobId, requestId);

                if (uploadSpan != null) {
                    uploadSpan.setStatus(StatusCode.OK);
                    uploadSpan.setAttribute("status", "PENDING");
                }

                // Return HTML fragment for htmx, JSON for API clients
                if (isHtmxRequest) {
                    ModelAndView mav = new ModelAndView("fragments/upload-started");
                    mav.addObject("jobId", jobIdStr);
                    return mav;
                } else {
                    // Build JSON response
                    Map<String, Object> response = new HashMap<>();
                    response.put("jobId", jobIdStr);
                    response.put("status", "PENDING");
                    response.put("statusUrl", "/api/documents/" + jobId + "/status");
                    response.put("message", "Document uploaded successfully. Processing will begin shortly.");
                    return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
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
            } else if ("PROCESSING".equals(status)) {
                jsonResponse.put("message", "Document is being processed");
            } else {
                jsonResponse.put("message", "Job is queued for processing");
            }

            return ResponseEntity.ok(jsonResponse);
        }
    }
}

