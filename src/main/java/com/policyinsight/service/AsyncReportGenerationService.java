package com.policyinsight.service;

import com.policyinsight.ai.AiAnalyzerException;
import com.policyinsight.ai.GeminiAnalyzer;
import com.policyinsight.model.JobStatus;
import com.policyinsight.model.PolicyJob;
import com.policyinsight.repository.PolicyJobRepository;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class AsyncReportGenerationService {

    private static final Logger log = LoggerFactory.getLogger(AsyncReportGenerationService.class);

    private final ReportService reportService;
    private final PolicyJobRepository policyJobRepository;

    public AsyncReportGenerationService(ReportService reportService, PolicyJobRepository policyJobRepository) {
        this.reportService = reportService;
        this.policyJobRepository = policyJobRepository;
    }

    @Async
    public void generate(UUID jobId) {
        PolicyJob job = policyJobRepository.findById(jobId)
                .orElseThrow(() -> new NotFoundException("Job was not found."));
        try {
            reportService.generateAndSaveReport(job);
        } catch (AiAnalyzerException ex) {
            markFailed(job, GeminiAnalyzer.SAFE_ANALYSIS_FAILURE_MESSAGE);
        } catch (RuntimeException ex) {
            log.warn("Async report generation failed for job {}: {}", jobId, ex.toString());
            markFailed(job, "Unable to generate the report.");
        }
    }

    private void markFailed(PolicyJob job, String safeMessage) {
        job.setStatus(JobStatus.FAILED);
        job.setSafeErrorMessage(safeMessage);
        policyJobRepository.save(job);
    }
}
