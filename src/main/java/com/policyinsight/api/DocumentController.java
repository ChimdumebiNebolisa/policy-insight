package com.policyinsight.api;

import com.policyinsight.api.messaging.PubSubService;
import com.policyinsight.api.storage.GcsStorageService;
import com.policyinsight.shared.model.PolicyJob;
import com.policyinsight.shared.repository.PolicyJobRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private static final long MAX_FILE_SIZE_BYTES = 20 * 1024 * 1024; // 20 MB
    private static final String PDF_CONTENT_TYPE = "application/pdf";

    private final GcsStorageService gcsStorageService;
    private final PubSubService pubSubService;
    private final PolicyJobRepository policyJobRepository;

    public DocumentController(
            GcsStorageService gcsStorageService,
            PubSubService pubSubService,
            PolicyJobRepository policyJobRepository) {
        this.gcsStorageService = gcsStorageService;
        this.pubSubService = pubSubService;
        this.policyJobRepository = policyJobRepository;
    }

    @PostMapping("/upload")
    @Operation(summary = "Upload a PDF document for analysis",
               description = "Accepts a PDF file and returns a job ID for tracking the analysis process")
    @Transactional
    public ResponseEntity<Map<String, Object>> uploadDocument(
            @Parameter(description = "PDF file to upload (max 20 MB)")
            @RequestParam("file") MultipartFile file) {

        logger.info("Received upload request: filename={}, size={}, contentType={}",
                file.getOriginalFilename(), file.getSize(), file.getContentType());

        // Validate file is present
        if (file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }

        // Validate file size (20 MB max)
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new IllegalArgumentException(
                    String.format("File size (%d bytes) exceeds maximum allowed size (%d bytes / 20 MB)",
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

        try {
            // Upload to GCS
            String gcsPath = gcsStorageService.uploadFile(jobId, filename, file.getInputStream(), contentType);
            logger.info("File uploaded to GCS: {}", gcsPath);

            // Create job record in database
            PolicyJob job = new PolicyJob(jobId);
            job.setStatus("PENDING");
            job.setPdfGcsPath(gcsPath);
            job.setPdfFilename(filename);
            job.setFileSizeBytes(file.getSize());
            job = policyJobRepository.save(job);
            logger.info("Job record created in database: jobId={}", jobId);

            // Publish Pub/Sub message
            pubSubService.publishJobMessage(jobId, gcsPath);
            logger.info("Pub/Sub message published for job: {}", jobId);

            // Build response
            Map<String, Object> response = new HashMap<>();
            response.put("jobId", jobId.toString());
            response.put("status", "PENDING");
            response.put("statusUrl", "/api/documents/" + jobId + "/status");
            response.put("message", "Document uploaded successfully. Processing will begin shortly.");

            return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);

        } catch (IOException e) {
            logger.error("Failed to upload file to GCS for job: {}", jobId, e);
            throw new RuntimeException("Failed to upload file to storage", e);
        } catch (Exception e) {
            logger.error("Failed to publish Pub/Sub message for job: {}", jobId, e);
            throw new RuntimeException("Failed to queue job for processing", e);
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

