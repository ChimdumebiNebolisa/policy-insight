package com.policyinsight.api;

import com.policyinsight.shared.dto.ShareLinkResponse;
import com.policyinsight.shared.model.PolicyJob;
import com.policyinsight.shared.repository.PolicyJobRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Controller for share link generation endpoints.
 */
@RestController
@RequestMapping("/api/documents")
@Tag(name = "Share", description = "Shareable link generation endpoints")
public class ShareController {

    private static final Logger logger = LoggerFactory.getLogger(ShareController.class);

    private final PolicyJobRepository policyJobRepository;
    private final ShareLinkService shareLinkService;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    public ShareController(
            PolicyJobRepository policyJobRepository,
            ShareLinkService shareLinkService) {
        this.policyJobRepository = policyJobRepository;
        this.shareLinkService = shareLinkService;
    }

    @PostMapping("/{id}/share")
    @Operation(summary = "Generate shareable link",
               description = "Creates a read-only shareable link with 7-day TTL")
    public ResponseEntity<?> generateShareLink(
            @Parameter(description = "Document/job ID")
            @PathVariable("id") String id) {

        logger.info("Share link generation request for document: {}", id);

        UUID jobUuid;
        try {
            jobUuid = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid document ID format: {}", id);
            throw new IllegalArgumentException("Invalid document ID format: " + id);
        }

        // Verify job exists and is completed
        PolicyJob job = policyJobRepository.findByJobUuid(jobUuid)
                .orElseThrow(() -> {
                    logger.warn("Job not found: {}", jobUuid);
                    return new NoSuchElementException("Document not found: " + jobUuid);
                });

        if (!"SUCCESS".equals(job.getStatus())) {
            logger.warn("Document not ready for sharing: status={}", job.getStatus());
            throw new IllegalStateException("Document still processing. Status: " + job.getStatus());
        }

        try {
            ShareLinkResponse response = shareLinkService.generateShareLink(jobUuid, baseUrl);
            logger.info("Share link generated: jobUuid={}, token={}",
                    jobUuid, response.getShareUrl());

            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(response);

        } catch (Exception e) {
            logger.error("Failed to generate share link for job: {}", jobUuid, e);
            throw new RuntimeException("Failed to generate share link: " + e.getMessage(), e);
        }
    }
}

