package com.policyinsight.processing;

import com.google.cloud.pubsub.v1.AckReplyConsumer;
import com.google.cloud.pubsub.v1.MessageReceiver;
import com.google.cloud.pubsub.v1.Subscriber;
import com.google.pubsub.v1.PubsubMessage;
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
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.transaction.Transactional;
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
 * Worker service that consumes Pub/Sub messages and processes documents.
 *
 * In pull mode: Starts a Pub/Sub subscriber to pull messages (when app.messaging.mode=gcp and PUBSUB_PUSH_MODE is not true).
 * In push mode: Available as a DocumentJobProcessor bean for PubSubController to use (when app.messaging.mode=gcp).
 *
 * This bean is required in cloudrun profile for PubSubController to process push messages.
 */
@Service
@ConditionalOnProperty(name = "app.messaging.mode", havingValue = "gcp")
public class DocumentProcessingWorker implements DocumentJobProcessor {

    private static final Logger logger = LoggerFactory.getLogger(DocumentProcessingWorker.class);

    private final String projectId;
    private final String subscriptionName;
    private Subscriber subscriber;

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

    public DocumentProcessingWorker(
            @Value("${pubsub.project-id:#{T(java.lang.System).getenv('GOOGLE_CLOUD_PROJECT')}}") String projectId,
            @Value("${pubsub.subscription-name:document-analysis-sub}") String subscriptionName) {
        this.projectId = projectId != null && !projectId.isEmpty() ? projectId : "local-project";
        this.subscriptionName = subscriptionName;
    }

    @PostConstruct
    public void initialize() {
        if (documentAiService == null) {
            logger.info("Document AI service not available, will use fallback OCR");
        }

        // Check if we should start the subscriber (only for pull mode, not push mode)
        // In push mode, Pub/Sub sends messages to /internal/pubsub endpoint, so we don't need a subscriber
        String pushMode = System.getenv("PUBSUB_PUSH_MODE");
        if ("true".equalsIgnoreCase(pushMode)) {
            logger.info("Pub/Sub push mode enabled - skipping subscriber initialization. Messages will be received via /internal/pubsub endpoint.");
            return;
        }

        try {
            String subscriptionPath = String.format("projects/%s/subscriptions/%s", projectId, subscriptionName);
            logger.info("Initializing Pub/Sub subscriber for: {}", subscriptionPath);

            MessageReceiver receiver = (PubsubMessage message, AckReplyConsumer consumer) -> {
                try {
                    processMessage(message);
                    consumer.ack();
                } catch (Exception e) {
                    logger.error("Failed to process message, nacking", e);
                    consumer.nack();
                }
            };

            this.subscriber = Subscriber.newBuilder(
                    com.google.pubsub.v1.ProjectSubscriptionName.of(projectId, subscriptionName), receiver)
                    .build();

            subscriber.startAsync().awaitRunning();
            logger.info("Document processing worker started and listening for messages");

        } catch (Exception e) {
            logger.error("Failed to initialize Pub/Sub subscriber", e);
            throw new RuntimeException("Failed to initialize worker", e);
        }
    }

    @PreDestroy
    public void shutdown() {
        if (subscriber != null) {
            subscriber.stopAsync();
            logger.info("Document processing worker stopped");
        }
    }

    private void processMessage(PubsubMessage message) {
        String jobIdStr = message.getAttributesMap().get("job_id");
        if (jobIdStr == null) {
            // Try parsing from payload
            String payload = message.getData().toStringUtf8();
            // Simple JSON parsing (in production, use proper JSON library)
            if (payload.contains("\"job_id\"")) {
                int start = payload.indexOf("\"job_id\"") + 9;
                int end = payload.indexOf("\"", start);
                jobIdStr = payload.substring(start, end);
            }
        }

        if (jobIdStr == null) {
            logger.error("Message missing job_id attribute");
            return;
        }

        UUID jobId;
        try {
            jobId = UUID.fromString(jobIdStr);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid job_id format: {}", jobIdStr);
            return;
        }

        logger.info("Processing document for job: {}", jobId);
        processDocument(jobId);
    }

    @Transactional
    public void processDocument(UUID jobId) {
        Optional<PolicyJob> jobOpt = policyJobRepository.findByJobUuid(jobId);
        if (jobOpt.isEmpty()) {
            logger.error("Job not found: {}", jobId);
            return;
        }

        PolicyJob job = jobOpt.get();

        try {
            // Update status to PROCESSING
            job.setStatus("PROCESSING");
            job.setStartedAt(Instant.now());
            policyJobRepository.save(job);

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

            // Optionally upload report JSON to GCS
            try {
                ObjectMapper objectMapper = new ObjectMapper();
                // Use validated report data for GCS upload
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

