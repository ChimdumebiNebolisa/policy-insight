package com.policyinsight.processing;

import com.policyinsight.api.storage.StorageService;
import com.policyinsight.processing.model.ExtractedText;
import com.policyinsight.processing.model.TextChunk;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.policyinsight.shared.model.DocumentChunk;
import com.policyinsight.shared.model.PolicyJob;
import com.policyinsight.shared.model.Report;
import com.policyinsight.shared.repository.DocumentChunkRepository;
import com.policyinsight.shared.repository.PolicyJobRepository;
import com.policyinsight.shared.repository.ReportRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Local worker service that polls the database for PENDING jobs and processes them.
 * Only loads when app.processing.mode=local (or when property is missing, as it's the default).
 * Uses @Scheduled to periodically poll for jobs in batches.
 */
@Service
@ConditionalOnProperty(name = "app.processing.mode", havingValue = "local", matchIfMissing = true)
public class LocalDocumentProcessingWorker {

    private static final Logger logger = LoggerFactory.getLogger(LocalDocumentProcessingWorker.class);

    @Autowired(required = false)
    private DocumentAiService documentAiService;

    @Autowired
    private FallbackOcrService fallbackOcrService;

    @Autowired
    private TextChunkerService textChunkerService;

    @Autowired
    private DocumentClassifierService documentClassifierService;

    @Autowired
    private RiskAnalysisService riskAnalysisService;

    @Autowired
    private ReportGenerationService reportGenerationService;

    @Autowired
    private ReportGroundingValidator reportGroundingValidator;

    @Autowired
    private PolicyJobRepository policyJobRepository;

    @Autowired
    private DocumentChunkRepository documentChunkRepository;

    @Autowired
    private ReportRepository reportRepository;

    @Autowired
    private StorageService storageService;

    @Value("${app.local-worker.poll-ms:2000}")
    private long pollIntervalMs;

    @Value("${app.local-worker.batch-size:5}")
    private int batchSize;

    /**
     * Periodically polls for PENDING jobs and processes them in batches.
     * Uses fixedDelayString to wait for the specified interval after each execution completes.
     * Processes up to batchSize jobs per poll cycle.
     */
    @Scheduled(fixedDelayString = "${app.local-worker.poll-ms:2000}")
    public void pollAndProcessJobs() {
        try {
            // Find oldest PENDING jobs using FOR UPDATE SKIP LOCKED for atomic locking
            List<PolicyJob> pendingJobs = policyJobRepository.findOldestPendingJobsForUpdate(batchSize);
            if (pendingJobs.isEmpty()) {
                // No pending jobs, skip this poll
                return;
            }

            logger.debug("Found {} pending job(s) to process", pendingJobs.size());

            // Process each job in the batch
            for (PolicyJob job : pendingJobs) {
                // Double-check status (idempotency: skip if already processing/completed)
                if (!"PENDING".equals(job.getStatus())) {
                    logger.debug("Skipping job {} - status is already {}", job.getJobUuid(), job.getStatus());
                    continue;
                }

                // Claim the job by updating status to PROCESSING
                if (claimJob(job)) {
                    logger.info("Claimed job for processing: {}", job.getJobUuid());
                    // Process asynchronously to allow batch processing
                    try {
                        processDocument(job.getJobUuid());
                    } catch (Exception e) {
                        logger.error("Error processing job: {}", job.getJobUuid(), e);
                        // Error handling is done in processDocument, but log here too
                    }
                } else {
                    logger.debug("Could not claim job {} (may have been claimed by another worker)", job.getJobUuid());
                }
            }
        } catch (Exception e) {
            logger.error("Error during job polling", e);
        }
    }

    /**
     * Atomically claims a job by updating its status from PENDING to PROCESSING.
     * Returns true if the job was successfully claimed, false if it was already claimed by another worker.
     * This method uses a transaction to ensure atomicity.
     * Note: The job should already be locked via FOR UPDATE SKIP LOCKED from the query.
     */
    @Transactional
    public boolean claimJob(PolicyJob job) {
        // Re-fetch the job to ensure we have the latest version (though it should be locked)
        Optional<PolicyJob> jobOpt = policyJobRepository.findByJobUuid(job.getJobUuid());
        if (jobOpt.isEmpty()) {
            logger.warn("Job not found during claim: {}", job.getJobUuid());
            return false;
        }

        PolicyJob currentJob = jobOpt.get();
        // Only claim if still PENDING (idempotency check)
        if (!"PENDING".equals(currentJob.getStatus())) {
            logger.debug("Job {} is not PENDING (status: {}), skipping claim",
                    currentJob.getJobUuid(), currentJob.getStatus());
            return false;
        }

        // Update to PROCESSING
        currentJob.setStatus("PROCESSING");
        currentJob.setStartedAt(Instant.now());
        policyJobRepository.save(currentJob);
        logger.debug("Successfully claimed job: {}", currentJob.getJobUuid());
        return true;
    }

    @Transactional
    public void processDocument(UUID jobId) {
        Optional<PolicyJob> jobOpt = policyJobRepository.findByJobUuid(jobId);
        if (jobOpt.isEmpty()) {
            logger.error("Job not found: {}", jobId);
            return;
        }

        PolicyJob job = jobOpt.get();

        // Idempotency check: if job is already SUCCESS or FAILED, skip processing
        if ("SUCCESS".equals(job.getStatus()) || "FAILED".equals(job.getStatus())) {
            logger.info("Job {} is already in final state: {}, skipping processing", jobId, job.getStatus());
            return;
        }

        // Ensure job is in PROCESSING state (should be set by claimJob)
        if (!"PROCESSING".equals(job.getStatus())) {
            logger.warn("Job {} is not in PROCESSING state (status: {}), updating to PROCESSING",
                    jobId, job.getStatus());
            job.setStatus("PROCESSING");
            if (job.getStartedAt() == null) {
                job.setStartedAt(Instant.now());
            }
            policyJobRepository.save(job);
        }

        try {
            // Download PDF from storage
            String storagePath = job.getPdfGcsPath();
            if (storagePath == null || storagePath.isEmpty()) {
                throw new IllegalArgumentException("Storage path is null or empty");
            }

            logger.info("Downloading PDF from storage: {}", storagePath);
            byte[] pdfBytes = storageService.downloadFile(storagePath);
            InputStream pdfStream = new ByteArrayInputStream(pdfBytes);

            // Extract text (try Document AI first, fallback to PDFBox)
            ExtractedText extractedText;

            if (documentAiService != null) {
                try {
                    extractedText = documentAiService.extractText(pdfStream, "application/pdf");
                    logger.info("Document AI extraction successful");
                } catch (Exception e) {
                    logger.warn("Document AI extraction failed, using fallback: {}", e.getMessage());
                    pdfStream = new ByteArrayInputStream(pdfBytes); // Reset stream
                    extractedText = fallbackOcrService.extractText(pdfStream);
                }
            } else {
                logger.info("Document AI not available, using fallback");
                extractedText = fallbackOcrService.extractText(pdfStream);
            }

            // Chunk text
            List<TextChunk> chunks = textChunkerService.chunkText(extractedText);

            // Store chunks in database
            for (TextChunk chunk : chunks) {
                DocumentChunk docChunk = new DocumentChunk(jobId);
                docChunk.setChunkIndex(chunk.getChunkIndex());
                docChunk.setText(chunk.getText());
                docChunk.setPageNumber(chunk.getPageNumber());
                docChunk.setStartOffset(chunk.getStartOffset());
                docChunk.setEndOffset(chunk.getEndOffset());
                docChunk.setSpanConfidence(chunk.getSpanConfidence());
                documentChunkRepository.save(docChunk);
            }

            logger.info("Stored {} chunks for job: {}", chunks.size(), jobId);

            // Retrieve stored chunks from DB to get IDs
            List<DocumentChunk> storedChunks = documentChunkRepository.findByJobUuidOrderByChunkIndex(jobId);

            // Classify document
            String fullText = extractedText.getFullText();
            DocumentClassifierService.ClassificationResult classification =
                    documentClassifierService.classify(fullText);

            job.setClassification(classification.getClassification());
            job.setClassificationConfidence(classification.getConfidence());

            // Risk analysis (5 categories)
            logger.info("Starting risk analysis for job: {}", jobId);
            Map<String, Object> riskTaxonomy = riskAnalysisService.analyzeRisks(storedChunks);
            logger.info("Risk analysis completed for job: {}", jobId);

            // Generate report sections
            logger.info("Generating report sections for job: {}", jobId);
            Map<String, Object> documentOverview = reportGenerationService.generateDocumentOverview(job, storedChunks);
            Map<String, Object> summary = reportGenerationService.generateSummary(storedChunks);
            Map<String, Object> obligationsAndRestrictions = reportGenerationService.generateObligationsAndRestrictions(storedChunks);

            // Prepare report data for validation (cite-or-abstain enforcement)
            logger.info("Validating report grounding for job: {}", jobId);
            Map<String, Object> reportDataMap = new HashMap<>();
            reportDataMap.put("summary_bullets", summary);
            reportDataMap.put("obligations", obligationsAndRestrictions.get("obligations"));
            reportDataMap.put("restrictions", obligationsAndRestrictions.get("restrictions"));
            reportDataMap.put("termination_triggers", obligationsAndRestrictions.get("termination_triggers"));
            reportDataMap.put("risk_taxonomy", riskTaxonomy);

            ReportGroundingValidator.ValidationResult validationResult =
                    reportGroundingValidator.validateReport(reportDataMap, storedChunks);

            if (!validationResult.isValid()) {
                logger.warn("Report validation found {} violations for job: {}",
                        validationResult.getViolations().size(), jobId);
                for (String violation : validationResult.getViolations()) {
                    logger.warn("Validation violation: {}", violation);
                }
                // Violations are handled by abstain statements in validated data, per PRD
                // Continue processing with validated (abstained) data
            } else {
                logger.info("Report validation passed for job: {}", jobId);
            }

            // Create and save Report with validated data
            Report report = new Report(jobId);
            report.setDocumentOverview(documentOverview);
            @SuppressWarnings("unchecked")
            Map<String, Object> validatedSummary = (Map<String, Object>) reportDataMap.get("summary_bullets");
            report.setSummaryBullets(validatedSummary);
            // obligations/restrictions/termination_triggers from reportDataMap are already validated
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> validatedObligations = (List<Map<String, Object>>) reportDataMap.get("obligations");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> validatedRestrictions = (List<Map<String, Object>>) reportDataMap.get("restrictions");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> validatedTerminationTriggers = (List<Map<String, Object>>) reportDataMap.get("termination_triggers");
            Map<String, Object> obligationsMap = Map.of("items", validatedObligations != null ? validatedObligations : Collections.emptyList());
            Map<String, Object> restrictionsMap = Map.of("items", validatedRestrictions != null ? validatedRestrictions : Collections.emptyList());
            Map<String, Object> terminationTriggersMap = Map.of("items", validatedTerminationTriggers != null ? validatedTerminationTriggers : Collections.emptyList());
            report.setObligations(obligationsMap);
            report.setRestrictions(restrictionsMap);
            report.setTerminationTriggers(terminationTriggersMap);
            @SuppressWarnings("unchecked")
            Map<String, Object> validatedRiskTaxonomy = (Map<String, Object>) reportDataMap.get("risk_taxonomy");
            report.setRiskTaxonomy(validatedRiskTaxonomy);
            report.setGeneratedAt(Instant.now());

            // Optionally upload report JSON to storage
            try {
                ObjectMapper objectMapper = new ObjectMapper();
                // Use validated report data for storage upload
                String reportJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(Map.of(
                        "document_overview", documentOverview,
                        "summary_bullets", reportDataMap.get("summary_bullets"),
                        "obligations", reportDataMap.get("obligations"),
                        "restrictions", reportDataMap.get("restrictions"),
                        "termination_triggers", reportDataMap.get("termination_triggers"),
                        "risk_taxonomy", reportDataMap.get("risk_taxonomy")
                ));
                String reportStoragePath = storageService.uploadFile(
                        jobId, "report.json",
                        new ByteArrayInputStream(reportJson.getBytes()),
                        "application/json");
                report.setGcsPath(reportStoragePath);
                job.setReportGcsPath(reportStoragePath);
                logger.info("Report JSON uploaded to storage: {}", reportStoragePath);
            } catch (Exception e) {
                logger.warn("Failed to upload report JSON to storage, continuing without storage path: {}", e.getMessage());
            }

            reportRepository.save(report);
            logger.info("Report saved to database for job: {}", jobId);

            // Update job status
            job.setStatus("SUCCESS");
            job.setCompletedAt(Instant.now());
            policyJobRepository.save(job);

            logger.info("Document processing completed for job: {}, classification: {}",
                    jobId, classification.getClassification());

        } catch (Exception e) {
            logger.error("Failed to process document for job: {}", jobId, e);
            job.setStatus("FAILED");
            job.setErrorMessage(e.getMessage());
            job.setCompletedAt(Instant.now());
            policyJobRepository.save(job);
        }
    }
}

