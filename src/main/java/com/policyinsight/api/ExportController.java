package com.policyinsight.api;

import com.policyinsight.processing.PdfExportService;
import com.policyinsight.shared.model.DocumentChunk;
import com.policyinsight.shared.model.PolicyJob;
import com.policyinsight.shared.model.Report;
import com.policyinsight.shared.repository.DocumentChunkRepository;
import com.policyinsight.shared.repository.PolicyJobRepository;
import com.policyinsight.shared.repository.ReportRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Controller for PDF export endpoints.
 */
@RestController
@RequestMapping("/api/documents")
@Tag(name = "Export", description = "PDF export endpoints")
public class ExportController {

    private static final Logger logger = LoggerFactory.getLogger(ExportController.class);

    private final PolicyJobRepository policyJobRepository;
    private final ReportRepository reportRepository;
    private final DocumentChunkRepository chunkRepository;
    private final PdfExportService pdfExportService;

    public ExportController(
            PolicyJobRepository policyJobRepository,
            ReportRepository reportRepository,
            DocumentChunkRepository chunkRepository,
            PdfExportService pdfExportService) {
        this.policyJobRepository = policyJobRepository;
        this.reportRepository = reportRepository;
        this.chunkRepository = chunkRepository;
        this.pdfExportService = pdfExportService;
    }

    @GetMapping("/{id}/export/pdf")
    @Operation(summary = "Export report as PDF",
               description = "Generates and downloads a PDF report with inline citations, watermark, and disclaimer")
    public ResponseEntity<?> exportPdf(
            @Parameter(description = "Document/job ID")
            @PathVariable("id") String id) {

        logger.info("PDF export request for document: {}", id);

        UUID jobUuid;
        try {
            jobUuid = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid document ID format: {}", id);
            throw new IllegalArgumentException("Invalid document ID format: " + id);
        }

        // Fetch job
        PolicyJob job = policyJobRepository.findByJobUuid(jobUuid)
                .orElseThrow(() -> {
                    logger.warn("Job not found: {}", jobUuid);
                    return new NoSuchElementException("Document not found: " + jobUuid);
                });

        // Check if document is completed
        if (!"SUCCESS".equals(job.getStatus())) {
            logger.warn("Document not ready for export: status={}", job.getStatus());
            throw new IllegalStateException("Document still processing. Status: " + job.getStatus());
        }

        // Fetch report
        Report report = reportRepository.findByJobUuid(jobUuid)
                .orElseThrow(() -> {
                    logger.warn("Report not found for job: {}", jobUuid);
                    return new NoSuchElementException("Report not available for job: " + jobUuid);
                });

        // Fetch chunks for citations
        List<DocumentChunk> chunks = chunkRepository.findByJobUuidOrderByChunkIndex(jobUuid);

        try {
            // Generate PDF
            byte[] pdfBytes = pdfExportService.generatePdf(job, report, chunks);

            // Build filename
            String timestamp = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").format(Instant.now());
            String filename = String.format("PolicyInsight_%s_%s.pdf", jobUuid.toString().substring(0, 8), timestamp);

            // Set headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDispositionFormData("attachment", filename);
            headers.setContentLength(pdfBytes.length);

            logger.info("PDF export successful: jobUuid={}, size={} bytes", jobUuid, pdfBytes.length);

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(pdfBytes);

        } catch (Exception e) {
            logger.error("Failed to generate PDF for job: {}", jobUuid, e);
            throw new RuntimeException("Failed to generate PDF: " + e.getMessage(), e);
        }
    }
}

