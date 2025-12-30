package com.policyinsight.api;

import com.policyinsight.api.messaging.JobPublisher;
import com.policyinsight.api.storage.StorageService;
import com.policyinsight.shared.model.PolicyJob;
import com.policyinsight.shared.repository.PolicyJobRepository;
import com.policyinsight.observability.TracingServiceInterface;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/documents")
@Tag(name = "Documents", description = "Document upload and status endpoints")
public class DocumentController {

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
    public ResponseEntity<Map<String, Object>> uploadDocument(
            @Parameter(description = "PDF file to upload (max 50 MB)")
            @RequestParam("file") MultipartFile file) {

        // Create span for upload operation
        Span uploadSpan = null;
        if (tracingService != null) {
            uploadSpan = tracingService.spanBuilder("upload")
                    .setAttribute("stage", "upload")
                    .setAttribute("filename", file.getOriginalFilename() != null ? file.getOriginalFilename() : "unknown")
                    .setAttribute("file_size_bytes", file.getSize())
                    .setAttribute("content_type", file.getContentType() != null ? file.getContentType() : "unknown")
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

            // Generate job UUID
            UUID jobId = UUID.randomUUID();
            String filename = file.getOriginalFilename();
            if (filename == null || filename.isEmpty()) {
                filename = "document.pdf";
            }

            // Add job_id to MDC for logging
            String jobIdStr = jobId.toString();
            MDC.put("job_id", jobIdStr);

            if (uploadSpan != null) {
                uploadSpan.setAttribute("job_id", jobIdStr);
                uploadSpan.setAttribute("document_id", jobIdStr);
            }

            try {
                // Upload to storage
                String storagePath = storageService.uploadFile(jobId, filename, file.getInputStream(), contentType);
                logger.info("File uploaded to storage: {}", storagePath);
                if (uploadSpan != null) {
                    uploadSpan.setAttribute("storage_path", storagePath);
                }

                // Create job record in database
                PolicyJob job = new PolicyJob(jobId);
                job.setStatus("PENDING");
                job.setPdfGcsPath(storagePath);
                job.setPdfFilename(filename);
                job.setFileSizeBytes(file.getSize());
                job = policyJobRepository.save(job);
                logger.info("Job record created in database: jobId={}", jobId);

                // Publish Pub/Sub message
                jobPublisher.publishJobQueued(jobId, storagePath);
                logger.info("Job queued event published for job: {}", jobId);

                // Build response
                Map<String, Object> response = new HashMap<>();
                response.put("jobId", jobIdStr);
                response.put("status", "PENDING");
                response.put("statusUrl", "/api/documents/" + jobId + "/status");
                response.put("message", "Document uploaded successfully. Processing will begin shortly.");

                if (uploadSpan != null) {
                    uploadSpan.setStatus(StatusCode.OK);
                    uploadSpan.setAttribute("status", "PENDING");
                }

                return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);

            } catch (IOException e) {
                logger.error("Failed to upload file to storage for job: {}", jobId, e);
                if (uploadSpan != null) {
                    uploadSpan.setStatus(StatusCode.ERROR);
                    uploadSpan.setAttribute("error", true);
                    uploadSpan.setAttribute("error.message", e.getMessage());
                    uploadSpan.recordException(e);
                }
                throw new RuntimeException("Failed to upload file to storage", e);
            } catch (Exception e) {
                logger.error("Failed to queue job for processing: {}", jobId, e);
                if (uploadSpan != null) {
                    uploadSpan.setStatus(StatusCode.ERROR);
                    uploadSpan.setAttribute("error", true);
                    uploadSpan.setAttribute("error.message", e.getMessage());
                    uploadSpan.recordException(e);
                }
                throw new RuntimeException("Failed to queue job for processing", e);
            } finally {
                if (uploadSpan != null) {
                    uploadSpan.end();
                }
                MDC.remove("job_id");
            }
        }
    }

    @GetMapping("/{id}/status")
    @Operation(summary = "Get document processing status",
               description = "Returns the current status of a document analysis job")
    public ResponseEntity<Map<String, Object>> getDocumentStatus(
            @Parameter(description = "Job ID returned from upload endpoint")
            @PathVariable("id") String id) {

        UUID jobUuid;
        try {
            jobUuid = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid job ID format: " + id);
        }

        PolicyJob job = policyJobRepository.findByJobUuid(jobUuid)
                .orElseThrow(() -> new IllegalArgumentException("Job not found: " + id));

        Map<String, Object> response = new HashMap<>();
        response.put("jobId", job.getJobUuid().toString());
        response.put("status", job.getStatus());

        // Add status-specific fields
        if ("SUCCESS".equals(job.getStatus())) {
            response.put("reportUrl", "/api/documents/" + id + "/report");
            response.put("message", "Analysis completed successfully");
        } else if ("FAILED".equals(job.getStatus())) {
            response.put("errorMessage", job.getErrorMessage());
            response.put("message", "Analysis failed: " + (job.getErrorMessage() != null ? job.getErrorMessage() : "Unknown error"));
        } else if ("PROCESSING".equals(job.getStatus())) {
            response.put("message", "Document is being processed");
        } else {
            response.put("message", "Job is queued for processing");
        }

        return ResponseEntity.ok(response);
    }
}

