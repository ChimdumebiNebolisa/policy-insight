package com.policyinsight.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.policyinsight.ai.AiAnalyzer;
import com.policyinsight.ai.dto.RiskReport;
import com.policyinsight.controller.DocumentController;
import com.policyinsight.model.DocumentChunk;
import com.policyinsight.model.JobStatus;
import com.policyinsight.model.PolicyJob;
import com.policyinsight.model.Report;
import com.policyinsight.repository.DocumentChunkRepository;
import com.policyinsight.repository.PolicyJobRepository;
import com.policyinsight.repository.ReportRepository;
import com.policyinsight.security.TokenService;
import com.policyinsight.util.CitationValidator;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReportService {

    private final AiAnalyzer aiAnalyzer;
    private final ObjectMapper objectMapper;
    private final DocumentChunkRepository documentChunkRepository;
    private final PolicyJobRepository policyJobRepository;
    private final ReportRepository reportRepository;
    private final CitationValidator citationValidator;
    private final TokenService tokenService;
    private final FallbackReportBuilder fallbackReportBuilder;

    public ReportService(
            AiAnalyzer aiAnalyzer,
            ObjectMapper objectMapper,
            DocumentChunkRepository documentChunkRepository,
            PolicyJobRepository policyJobRepository,
            ReportRepository reportRepository,
            CitationValidator citationValidator,
            TokenService tokenService,
            FallbackReportBuilder fallbackReportBuilder
    ) {
        this.aiAnalyzer = aiAnalyzer;
        this.objectMapper = objectMapper;
        this.documentChunkRepository = documentChunkRepository;
        this.policyJobRepository = policyJobRepository;
        this.reportRepository = reportRepository;
        this.citationValidator = citationValidator;
        this.tokenService = tokenService;
        this.fallbackReportBuilder = fallbackReportBuilder;
    }

    public Report generateAndSaveReport(PolicyJob job) {
        List<DocumentChunk> chunks = documentChunkRepository.findByJobIdOrderByChunkIndex(job.getId());
        RiskReport riskReport = citationValidator.validateReport(aiAnalyzer.generateReport(chunks), chunks);
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
        return new StatusView(jobId, job.getStatus(), report.getId(), null, false);
    }

    @Transactional(readOnly = true)
    public StatusView statusForOwner(UUID jobId, Cookie[] cookies) {
        PolicyJob job = policyJobRepository.findById(jobId)
                .orElseThrow(() -> new NotFoundException("Job was not found."));
        if (!hasOwnerAccess(job, cookies)) {
            throw new AccessDeniedException("Status is not available for this session.");
        }
        UUID reportId = reportRepository.findByJobId(jobId).map(Report::getId).orElse(null);
        boolean fallbackAvailable = job.getStatus() == JobStatus.FAILED
                && job.getDemoKey() == null
                && documentChunkRepository.findByJobIdOrderByChunkIndex(jobId).size() > 0;
        return new StatusView(jobId, job.getStatus(), reportId, job.getSafeErrorMessage(), fallbackAvailable);
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
        return Optional.of(new ReportView(
                reportId,
                job.getId(),
                report.get().getCreatedAt(),
                job.getDemoKey() != null,
                riskReport.documentOverview() != null
                        && riskReport.documentOverview().startsWith(FallbackReportBuilder.FALLBACK_LABEL),
                riskReport,
                chunks
        ));
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
