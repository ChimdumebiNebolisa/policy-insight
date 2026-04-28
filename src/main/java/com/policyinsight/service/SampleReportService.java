package com.policyinsight.service;

import com.policyinsight.ai.AiAnalyzerException;
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
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SampleReportService {

    public static final String SAMPLE_DEMO_KEY = "fictional-business-agreement";

    private final PdfTextExtractor pdfTextExtractor;
    private final ChunkingService chunkingService;
    private final PolicyJobRepository policyJobRepository;
    private final DocumentChunkRepository documentChunkRepository;
    private final ReportRepository reportRepository;
    private final TokenService tokenService;
    private final OwnerTokenProperties ownerTokenProperties;
    private final ReportService reportService;

    public SampleReportService(
            PdfTextExtractor pdfTextExtractor,
            ChunkingService chunkingService,
            PolicyJobRepository policyJobRepository,
            DocumentChunkRepository documentChunkRepository,
            ReportRepository reportRepository,
            TokenService tokenService,
            OwnerTokenProperties ownerTokenProperties,
            ReportService reportService
    ) {
        this.pdfTextExtractor = pdfTextExtractor;
        this.chunkingService = chunkingService;
        this.policyJobRepository = policyJobRepository;
        this.documentChunkRepository = documentChunkRepository;
        this.reportRepository = reportRepository;
        this.tokenService = tokenService;
        this.ownerTokenProperties = ownerTokenProperties;
        this.reportService = reportService;
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
        try {
            Report report = reportService.generateAndSaveReport(saved);
            return report.getJob();
        } catch (AiAnalyzerException ex) {
            saved.setStatus(JobStatus.FAILED);
            saved.setSafeErrorMessage(ex.getMessage());
            return policyJobRepository.save(saved);
        } catch (RuntimeException ex) {
            saved.setStatus(JobStatus.FAILED);
            saved.setSafeErrorMessage("Unable to generate the sample report.");
            return policyJobRepository.save(saved);
        }
    }

    private byte[] readSamplePdf() {
        try {
            return new ClassPathResource("samples/fictional_business_agreement.pdf").getContentAsByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("Sample PDF resource is missing.", ex);
        }
    }
}
