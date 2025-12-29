package com.policyinsight.processing;

import com.google.cloud.pubsub.v1.AckReplyConsumer;
import com.google.cloud.pubsub.v1.MessageReceiver;
import com.google.cloud.pubsub.v1.Subscriber;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import com.google.pubsub.v1.PubsubMessage;
import com.policyinsight.api.storage.GcsStorageService;
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
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Worker service that consumes Pub/Sub messages and processes documents.
 * Only loads when pubsub.enabled=true and worker.enabled=true.
 */
@Service
@ConditionalOnProperty(prefix = "worker", name = "enabled", havingValue = "true", matchIfMissing = false)
public class DocumentProcessingWorker {

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
    private PolicyJobRepository policyJobRepository;

    @Autowired
    private DocumentChunkRepository documentChunkRepository;

    @Autowired
    private ReportRepository reportRepository;

    @Autowired
    private GcsStorageService gcsStorageService;

    private final Storage storage;

    public DocumentProcessingWorker(
            @Value("${pubsub.project-id:#{T(java.lang.System).getenv('GOOGLE_CLOUD_PROJECT')}}") String projectId,
            @Value("${pubsub.subscription-name:document-analysis-sub}") String subscriptionName) {
        this.projectId = projectId != null && !projectId.isEmpty() ? projectId : "local-project";
        this.subscriptionName = subscriptionName;
        this.storage = StorageOptions.getDefaultInstance().getService();
    }

    @PostConstruct
    public void initialize() {
        if (documentAiService == null) {
            logger.info("Document AI service not available, will use fallback OCR");
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

            // Download PDF from GCS
            String gcsPath = job.getPdfGcsPath();
            if (gcsPath == null || !gcsPath.startsWith("gs://")) {
                throw new IllegalArgumentException("Invalid GCS path: " + gcsPath);
            }

            // Parse GCS path: gs://bucket-name/jobId/filename
            String pathWithoutPrefix = gcsPath.replace("gs://", "");
            int firstSlash = pathWithoutPrefix.indexOf('/');
            if (firstSlash < 0) {
                throw new IllegalArgumentException("Invalid GCS path format: " + gcsPath);
            }
            String bucketName = pathWithoutPrefix.substring(0, firstSlash);
            String objectName = pathWithoutPrefix.substring(firstSlash + 1);

            logger.info("Downloading PDF from GCS: gs://{}/{}", bucketName, objectName);
            byte[] pdfBytes = storage.readAllBytes(BlobId.of(bucketName, objectName));
            InputStream pdfStream = new ByteArrayInputStream(pdfBytes);

            // Extract text (try Document AI first, fallback to PDFBox)
            ExtractedText extractedText;
            boolean usedFallback = false;

            if (documentAiService != null) {
                try {
                    extractedText = documentAiService.extractText(pdfStream, "application/pdf");
                    logger.info("Document AI extraction successful");
                } catch (Exception e) {
                    logger.warn("Document AI extraction failed, using fallback: {}", e.getMessage());
                    pdfStream = new ByteArrayInputStream(pdfBytes); // Reset stream
                    extractedText = fallbackOcrService.extractText(pdfStream);
                    usedFallback = true;
                }
            } else {
                logger.info("Document AI not available, using fallback");
                extractedText = fallbackOcrService.extractText(pdfStream);
                usedFallback = true;
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

            // Create and save Report
            Report report = new Report(jobId);
            report.setDocumentOverview(documentOverview);
            report.setSummaryBullets(summary);
            // obligationsAndRestrictions contains arrays, wrap them in maps for JSONB storage
            @SuppressWarnings("unchecked")
            Map<String, Object> obligationsMap = Map.of("items", obligationsAndRestrictions.get("obligations"));
            @SuppressWarnings("unchecked")
            Map<String, Object> restrictionsMap = Map.of("items", obligationsAndRestrictions.get("restrictions"));
            @SuppressWarnings("unchecked")
            Map<String, Object> terminationTriggersMap = Map.of("items", obligationsAndRestrictions.get("termination_triggers"));
            report.setObligations(obligationsMap);
            report.setRestrictions(restrictionsMap);
            report.setTerminationTriggers(terminationTriggersMap);
            report.setRiskTaxonomy(riskTaxonomy);
            report.setGeneratedAt(Instant.now());

            // Optionally upload report JSON to GCS
            try {
                ObjectMapper objectMapper = new ObjectMapper();
                String reportJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(Map.of(
                        "document_overview", documentOverview,
                        "summary_bullets", summary,
                        "obligations", obligationsAndRestrictions.get("obligations"),
                        "restrictions", obligationsAndRestrictions.get("restrictions"),
                        "termination_triggers", obligationsAndRestrictions.get("termination_triggers"),
                        "risk_taxonomy", riskTaxonomy
                ));
                String reportGcsPath = gcsStorageService.uploadFile(
                        jobId, "report.json", 
                        new ByteArrayInputStream(reportJson.getBytes()), 
                        "application/json");
                report.setGcsPath(reportGcsPath);
                job.setReportGcsPath(reportGcsPath);
                logger.info("Report JSON uploaded to GCS: {}", reportGcsPath);
            } catch (Exception e) {
                logger.warn("Failed to upload report JSON to GCS, continuing without GCS path: {}", e.getMessage());
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

