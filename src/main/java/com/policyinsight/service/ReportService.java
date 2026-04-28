package com.policyinsight.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.policyinsight.ai.AiAnalyzer;
import com.policyinsight.ai.dto.RiskReport;
import com.policyinsight.model.DocumentChunk;
import com.policyinsight.model.JobStatus;
import com.policyinsight.model.PolicyJob;
import com.policyinsight.model.Report;
import com.policyinsight.repository.DocumentChunkRepository;
import com.policyinsight.repository.PolicyJobRepository;
import com.policyinsight.repository.ReportRepository;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ReportService {

    private final AiAnalyzer aiAnalyzer;
    private final ObjectMapper objectMapper;
    private final DocumentChunkRepository documentChunkRepository;
    private final PolicyJobRepository policyJobRepository;
    private final ReportRepository reportRepository;

    public ReportService(
            AiAnalyzer aiAnalyzer,
            ObjectMapper objectMapper,
            DocumentChunkRepository documentChunkRepository,
            PolicyJobRepository policyJobRepository,
            ReportRepository reportRepository
    ) {
        this.aiAnalyzer = aiAnalyzer;
        this.objectMapper = objectMapper;
        this.documentChunkRepository = documentChunkRepository;
        this.policyJobRepository = policyJobRepository;
        this.reportRepository = reportRepository;
    }

    public Report generateAndSaveReport(PolicyJob job) {
        List<DocumentChunk> chunks = documentChunkRepository.findByJobIdOrderByChunkIndex(job.getId());
        RiskReport riskReport = aiAnalyzer.generateReport(chunks);
        Report report = reportRepository.save(new Report(job, toJson(riskReport)));
        job.setStatus(JobStatus.COMPLETED);
        policyJobRepository.save(job);
        return report;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to serialize report", ex);
        }
    }
}
