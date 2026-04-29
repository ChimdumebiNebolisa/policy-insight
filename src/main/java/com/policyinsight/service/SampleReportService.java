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
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SampleReportService {

    public static final String SAMPLE_DEMO_KEY = "fictional-business-agreement-deterministic-v1";
    private static final Logger LOGGER = LoggerFactory.getLogger(SampleReportService.class);
    private static final String SAMPLE_PDF_CLASSPATH = "samples/fictional_business_agreement.pdf";

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
        String ownerToken = tokenService.generateToken();
        PolicyJob job = policyJobRepository.findByDemoKey(SAMPLE_DEMO_KEY)
                .map(this::reuseOrRebuildSampleJob)
                .orElseGet(this::createSampleJob);
        job.setOwnerTokenHash(tokenService.hashToken(ownerToken));
        job.setOwnerTokenExpiresAt(Instant.now().plus(ownerTokenProperties.ttlMinutes(), ChronoUnit.MINUTES));
        policyJobRepository.save(job);
        int chunkCount = documentChunkRepository.findByJobIdOrderByChunkIndex(job.getId()).size();
        Report report = reportRepository.findByJobId(job.getId())
                .orElseThrow(() -> new SampleReportException("The fictional sample report could not be generated. Check AI configuration or try again."));
        return new SampleReportResult(job.getId(), report.getId(), ownerToken, chunkCount);
    }

    private PolicyJob reuseOrRebuildSampleJob(PolicyJob existingJob) {
        if (existingJob.getStatus() == JobStatus.COMPLETED && reportRepository.findByJobId(existingJob.getId()).isPresent()) {
            return existingJob;
        }
        policyJobRepository.delete(existingJob);
        policyJobRepository.flush();
        return createSampleJob();
    }

    private PolicyJob createSampleJob() {
        byte[] pdfBytes = readSamplePdf();
        String text = pdfTextExtractor.extractText(pdfBytes);
        List<String> chunks = chunkingService.chunk(text);
        if (chunks.isEmpty()) {
            throw new BadUploadException("No extractable text was found in the sample PDF.");
        }

        PolicyJob job = new PolicyJob("pending-sample-owner-token", Instant.now().plus(15, ChronoUnit.MINUTES));
        job.setDemoKey(SAMPLE_DEMO_KEY);
        PolicyJob saved = policyJobRepository.save(job);
        for (int i = 0; i < chunks.size(); i++) {
            documentChunkRepository.save(new DocumentChunk(saved, i, chunks.get(i)));
        }
        List<DocumentChunk> savedChunks = documentChunkRepository.findByJobIdOrderByChunkIndex(saved.getId());
        RiskReport riskReport = sampleAgreementReportBuilder.build(savedChunks);
        reportRepository.save(new Report(saved, toJson(riskReport)));
        saved.setStatus(JobStatus.COMPLETED);
        saved.setSafeErrorMessage(null);
        return policyJobRepository.save(saved);
    }

    private byte[] readSamplePdf() {
        try {
            ClassPathResource resource = new ClassPathResource(SAMPLE_PDF_CLASSPATH);
            if (!resource.exists()) {
                throw new IllegalStateException("Sample PDF resource is missing from the classpath.");
            }
            byte[] bytes = resource.getContentAsByteArray();
            LOGGER.info("Loaded sample PDF from classpath resource {} ({} bytes).", SAMPLE_PDF_CLASSPATH, bytes.length);
            return bytes;
        } catch (IOException ex) {
            throw new IllegalStateException("Sample PDF resource is missing.", ex);
        }
    }

    private String toJson(RiskReport report) {
        try {
            return objectMapper.writeValueAsString(report);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to serialize sample report", ex);
        }
    }
}
