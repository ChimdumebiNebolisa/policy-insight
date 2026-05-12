package com.policyinsight.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.policyinsight.ai.dto.RiskReport;
import com.policyinsight.config.OwnerTokenProperties;
import com.policyinsight.model.DocumentChunk;
import com.policyinsight.model.JobStatus;
import com.policyinsight.model.PolicyJob;
import com.policyinsight.model.Report;
import com.policyinsight.repository.DocumentChunkRepository;
import com.policyinsight.repository.PolicyJobRepository;
import com.policyinsight.repository.ReportRepository;
import com.policyinsight.security.TokenService;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SampleReportService {

    public static final String DEFAULT_SAMPLE_KEY = "vendor-agreement";
    public static final String SAMPLE_DEMO_KEY = "fictional-business-agreement-deterministic-v1";
    private static final Logger LOGGER = LoggerFactory.getLogger(SampleReportService.class);
    private static final Map<String, SampleDefinition> SAMPLE_DEFINITIONS = Map.of(
            DEFAULT_SAMPLE_KEY, new SampleDefinition(
                    SAMPLE_DEMO_KEY,
                    "samples/fictional_business_agreement.pdf"
            ),
            "privacy-policy", new SampleDefinition(
                    "privacy-policy-deterministic-v1",
                    "samples/privacy_policy_sample.txt"
            ),
            "employment-policy", new SampleDefinition(
                    "employment-policy-deterministic-v1",
                    "samples/employment_policy_sample.txt"
            ),
            "campus-student-policy", new SampleDefinition(
                    "campus-student-policy-deterministic-v1",
                    "samples/campus_student_policy_sample.txt"
            )
    );

    private final PdfTextExtractor pdfTextExtractor;
    private final ChunkingService chunkingService;
    private final PolicyJobRepository policyJobRepository;
    private final DocumentChunkRepository documentChunkRepository;
    private final ReportRepository reportRepository;
    private final TokenService tokenService;
    private final OwnerTokenProperties ownerTokenProperties;
    private final ObjectMapper objectMapper;
    private final SampleAgreementReportBuilder sampleAgreementReportBuilder;

    public SampleReportService(
            PdfTextExtractor pdfTextExtractor,
            ChunkingService chunkingService,
            PolicyJobRepository policyJobRepository,
            DocumentChunkRepository documentChunkRepository,
            ReportRepository reportRepository,
            TokenService tokenService,
            OwnerTokenProperties ownerTokenProperties,
            ObjectMapper objectMapper,
            SampleAgreementReportBuilder sampleAgreementReportBuilder
    ) {
        this.pdfTextExtractor = pdfTextExtractor;
        this.chunkingService = chunkingService;
        this.policyJobRepository = policyJobRepository;
        this.documentChunkRepository = documentChunkRepository;
        this.reportRepository = reportRepository;
        this.tokenService = tokenService;
        this.ownerTokenProperties = ownerTokenProperties;
        this.objectMapper = objectMapper;
        this.sampleAgreementReportBuilder = sampleAgreementReportBuilder;
    }

    @Transactional
    public SampleReportResult openSampleReport() {
        return openSampleReport(DEFAULT_SAMPLE_KEY);
    }

    @Transactional
    public SampleReportResult openSampleReport(String sampleKey) {
        SampleDefinition definition = SAMPLE_DEFINITIONS.get(sampleKey);
        if (definition == null) {
            throw new SampleReportException("The requested sample was not found.");
        }
        String ownerToken = tokenService.generateToken();
        PolicyJob job = policyJobRepository.findByDemoKey(definition.demoKey())
                .map(existingJob -> reuseOrRebuildSampleJob(existingJob, definition, sampleKey))
                .orElseGet(() -> createSampleJob(definition, sampleKey));
        job.setOwnerTokenHash(tokenService.hashToken(ownerToken));
        job.setOwnerTokenExpiresAt(Instant.now().plus(ownerTokenProperties.ttlMinutes(), ChronoUnit.MINUTES));
        policyJobRepository.save(job);
        int chunkCount = documentChunkRepository.findByJobIdOrderByChunkIndex(job.getId()).size();
        Report report = reportRepository.findByJobId(job.getId())
                .orElseThrow(() -> new SampleReportException("The sample report could not be generated. Check configuration or try again."));
        return new SampleReportResult(job.getId(), report.getId(), ownerToken, chunkCount);
    }

    public boolean isKnownSampleKey(String sampleKey) {
        return SAMPLE_DEFINITIONS.containsKey(sampleKey);
    }

    private PolicyJob reuseOrRebuildSampleJob(PolicyJob existingJob, SampleDefinition definition, String sampleKey) {
        if (existingJob.getStatus() == JobStatus.COMPLETED && reportRepository.findByJobId(existingJob.getId()).isPresent()) {
            return existingJob;
        }
        policyJobRepository.delete(existingJob);
        policyJobRepository.flush();
        return createSampleJob(definition, sampleKey);
    }

    private PolicyJob createSampleJob(SampleDefinition definition, String sampleKey) {
        String text = readSampleText(definition.resourceClasspath());
        List<String> chunks = chunkingService.chunk(text);
        if (chunks.isEmpty()) {
            throw new BadUploadException("No extractable text was found in the sample document.");
        }

        PolicyJob job = new PolicyJob("pending-sample-owner-token", Instant.now().plus(15, ChronoUnit.MINUTES));
        job.setDemoKey(definition.demoKey());
        PolicyJob saved = policyJobRepository.save(job);
        for (int i = 0; i < chunks.size(); i++) {
            documentChunkRepository.save(new DocumentChunk(saved, i, chunks.get(i)));
        }
        List<DocumentChunk> savedChunks = documentChunkRepository.findByJobIdOrderByChunkIndex(saved.getId());
        RiskReport riskReport = sampleAgreementReportBuilder.build(sampleKey, savedChunks);
        reportRepository.save(new Report(saved, toJson(riskReport)));
        saved.setStatus(JobStatus.COMPLETED);
        saved.setSafeErrorMessage(null);
        return policyJobRepository.save(saved);
    }

    private String readSampleText(String resourcePath) {
        try {
            ClassPathResource resource = new ClassPathResource(resourcePath);
            if (!resource.exists()) {
                throw new IllegalStateException("Sample resource is missing from the classpath.");
            }
            if (resourcePath.endsWith(".pdf")) {
                byte[] bytes = resource.getContentAsByteArray();
                LOGGER.info("Loaded sample PDF from classpath resource {} ({} bytes).", resourcePath, bytes.length);
                return pdfTextExtractor.extractText(bytes);
            }
            String text = new String(resource.getContentAsByteArray(), StandardCharsets.UTF_8);
            LOGGER.info("Loaded sample text from classpath resource {} ({} chars).", resourcePath, text.length());
            return text;
        } catch (IOException ex) {
            throw new IllegalStateException("Sample resource is missing.", ex);
        }
    }

    private String toJson(RiskReport report) {
        try {
            return objectMapper.writeValueAsString(report);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to serialize sample report", ex);
        }
    }

    private record SampleDefinition(String demoKey, String resourceClasspath) {
    }
}
