package com.policyinsight.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.policyinsight.ai.AiAnalyzer;
import com.policyinsight.ai.dto.QaAnswer;
import com.policyinsight.ai.dto.RiskReport;
import com.policyinsight.controller.DocumentController;
import com.policyinsight.model.DocumentChunk;
import com.policyinsight.model.JobStatus;
import com.policyinsight.model.PolicyJob;
import com.policyinsight.model.QaInteraction;
import com.policyinsight.model.Report;
import com.policyinsight.repository.DocumentChunkRepository;
import com.policyinsight.repository.PolicyJobRepository;
import com.policyinsight.repository.QaInteractionRepository;
import com.policyinsight.repository.ReportRepository;
import com.policyinsight.security.TokenService;
import com.policyinsight.util.CitationValidator;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReportService {

    private static final Logger log = LoggerFactory.getLogger(ReportService.class);

    private final AiAnalyzer aiAnalyzer;
    private final ObjectMapper objectMapper;
    private final DocumentChunkRepository documentChunkRepository;
    private final PolicyJobRepository policyJobRepository;
    private final ReportRepository reportRepository;
    private final QaInteractionRepository qaInteractionRepository;
    private final CitationValidator citationValidator;
    private final TokenService tokenService;
    private final FallbackReportBuilder fallbackReportBuilder;

    @Value("${app.ai.provider:mock}")
    private String aiProvider;

    public ReportService(
            AiAnalyzer aiAnalyzer,
            ObjectMapper objectMapper,
            DocumentChunkRepository documentChunkRepository,
            PolicyJobRepository policyJobRepository,
            ReportRepository reportRepository,
            QaInteractionRepository qaInteractionRepository,
            CitationValidator citationValidator,
            TokenService tokenService,
            FallbackReportBuilder fallbackReportBuilder
    ) {
        this.aiAnalyzer = aiAnalyzer;
        this.objectMapper = objectMapper;
        this.documentChunkRepository = documentChunkRepository;
        this.policyJobRepository = policyJobRepository;
        this.reportRepository = reportRepository;
        this.qaInteractionRepository = qaInteractionRepository;
        this.citationValidator = citationValidator;
        this.tokenService = tokenService;
        this.fallbackReportBuilder = fallbackReportBuilder;
    }

    public Report generateAndSaveReport(PolicyJob job) {
        List<DocumentChunk> chunks = documentChunkRepository.findByJobIdOrderByChunkIndex(job.getId());
        job.setStatus(JobStatus.BUILDING_AI_REPORT);
        policyJobRepository.save(job);
        RiskReport generatedReport = aiAnalyzer.generateReport(chunks);
        job.setStatus(JobStatus.VALIDATING_CITATIONS);
        policyJobRepository.save(job);
        RiskReport riskReport = citationValidator.validateReport(generatedReport, chunks);
        Report report = reportRepository.save(new Report(job, toJson(riskReport)));
        job.setStatus(JobStatus.COMPLETED);
        policyJobRepository.save(job);
        return report;
    }

    @Transactional
    public StatusView generateFallbackReportForOwner(UUID jobId, Cookie[] cookies) {
        PolicyJob job = policyJobRepository.findById(jobId)
                .orElseThrow(() -> new NotFoundException("Job was not found."));
        if (!hasOwnerAccess(job, cookies)) {
            throw new AccessDeniedException("Fallback report is not available for this session.");
        }
        if (job.getDemoKey() != null) {
            throw new AccessDeniedException("Fallback reports are only available for uploaded documents.");
        }
        if (job.getStatus() != JobStatus.FAILED) {
            return statusForOwner(jobId, cookies);
        }
        List<DocumentChunk> chunks = documentChunkRepository.findByJobIdOrderByChunkIndex(job.getId());
        if (chunks.isEmpty()) {
            throw new NotFoundException("Extracted source text was not found.");
        }
        RiskReport fallbackReport = citationValidator.validateReport(fallbackReportBuilder.build(chunks), chunks);
        Report report = reportRepository.save(new Report(job, toJson(fallbackReport)));
        job.setStatus(JobStatus.COMPLETED);
        job.setSafeErrorMessage(null);
        policyJobRepository.save(job);
        return new StatusView(
                jobId,
                job.getStatus(),
                report.getId(),
                null,
                false,
                false,
                job.getCreatedAt(),
                job.getUpdatedAt()
        );
    }

    @Transactional(readOnly = true)
    public StatusView statusForOwner(UUID jobId, Cookie[] cookies) {
        PolicyJob job = policyJobRepository.findById(jobId)
                .orElseThrow(() -> new NotFoundException("Job was not found."));
        if (!hasOwnerAccess(job, cookies)) {
            throw new AccessDeniedException("Status is not available for this session.");
        }
        UUID reportId = reportRepository.findByJobId(jobId).map(Report::getId).orElse(null);
        boolean hasChunks = !documentChunkRepository.findByJobIdOrderByChunkIndex(jobId).isEmpty();
        boolean fallbackAvailable = job.getStatus() == JobStatus.FAILED
                && job.getDemoKey() == null
                && hasChunks
                && reportId == null;
        boolean retryAvailable = job.getStatus() == JobStatus.FAILED
                && job.getDemoKey() == null
                && hasChunks
                && reportId == null;
        return new StatusView(
                jobId,
                job.getStatus(),
                reportId,
                job.getSafeErrorMessage(),
                fallbackAvailable,
                retryAvailable,
                job.getCreatedAt(),
                job.getUpdatedAt()
        );
    }

    @Transactional
    public void validateAndResetForRetry(UUID jobId, Cookie[] cookies) {
        PolicyJob job = policyJobRepository.findById(jobId)
                .orElseThrow(() -> new NotFoundException("Job was not found."));
        if (!hasOwnerAccess(job, cookies)) {
            throw new AccessDeniedException("Retry is not available for this session.");
        }
        if (job.getDemoKey() != null) {
            throw new AccessDeniedException("Retry is only available for uploaded documents.");
        }
        if (job.getStatus() != JobStatus.FAILED) {
            throw new BadUploadException("Only failed jobs can be retried.");
        }
        if (reportRepository.findByJobId(jobId).isPresent()) {
            throw new BadUploadException("A report already exists for this job.");
        }
        List<DocumentChunk> chunks = documentChunkRepository.findByJobIdOrderByChunkIndex(jobId);
        if (chunks.isEmpty()) {
            throw new NotFoundException("No extracted text found for this job.");
        }
        job.setStatus(JobStatus.TEXT_EXTRACTED);
        job.setSafeErrorMessage(null);
        policyJobRepository.save(job);
    }

    @Transactional(readOnly = true)
    public Optional<ReportView> reportForOwner(UUID reportId, Cookie[] cookies) {
        Optional<Report> report = reportRepository.findById(reportId);
        if (report.isEmpty()) {
            return Optional.empty();
        }
        PolicyJob job = report.get().getJob();
        if (!hasOwnerAccess(job, cookies)) {
            return Optional.empty();
        }
        RiskReport riskReport = fromJson(report.get().getContent());
        List<DocumentChunk> chunks = documentChunkRepository.findByJobIdOrderByChunkIndex(job.getId());
        List<ReportView.QaInteractionView> qaHistory = loadQaHistory(reportId);
        boolean isFallback = riskReport.documentOverview() != null
                && riskReport.documentOverview().startsWith(FallbackReportBuilder.FALLBACK_LABEL);
        return Optional.of(new ReportView(
                reportId,
                job.getId(),
                report.get().getCreatedAt(),
                job.getDemoKey() != null,
                isFallback,
                riskReport,
                chunks,
                qaHistory,
                aiProviderLabel()
        ));
    }

    private List<ReportView.QaInteractionView> loadQaHistory(UUID reportId) {
        return qaInteractionRepository.findByReportIdOrderByCreatedAtAsc(reportId).stream()
                .map(interaction -> {
                    try {
                        QaAnswer answer = objectMapper.readValue(interaction.getAnswer(), QaAnswer.class);
                        return new ReportView.QaInteractionView(interaction.getQuestion(), answer);
                    } catch (JsonProcessingException ex) {
                        log.warn("Could not deserialize Q&A answer for interaction {}: {}", interaction.getId(), ex.getMessage());
                        return null;
                    }
                })
                .filter(view -> view != null)
                .toList();
    }

    private String aiProviderLabel() {
        return switch (aiProvider.toLowerCase()) {
            case "gemini" -> "Gemini";
            case "mock" -> "Mock (demo mode)";
            default -> aiProvider;
        };
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to serialize report", ex);
        }
    }

    private RiskReport fromJson(String value) {
        try {
            return objectMapper.readValue(value, RiskReport.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to read report", ex);
        }
    }

    private boolean hasOwnerAccess(PolicyJob job, Cookie[] cookies) {
        if (Instant.now().isAfter(job.getOwnerTokenExpiresAt()) || cookies == null) {
            return false;
        }
        String cookieName = DocumentController.ownerCookieName(job.getId().toString());
        return Arrays.stream(cookies)
                .filter(cookie -> cookieName.equals(cookie.getName()))
                .map(Cookie::getValue)
                .anyMatch(token -> tokenService.matches(token, job.getOwnerTokenHash()));
    }
}
