package com.policyinsight.web;

import com.policyinsight.processing.QaService;
import com.policyinsight.shared.model.DocumentChunk;
import com.policyinsight.shared.model.PolicyJob;
import com.policyinsight.shared.model.QaInteraction;
import com.policyinsight.shared.model.Report;
import com.policyinsight.shared.repository.DocumentChunkRepository;
import com.policyinsight.shared.repository.PolicyJobRepository;
import com.policyinsight.shared.repository.ReportRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Controller for report display pages.
 * Renders Thymeleaf templates for viewing analysis reports.
 */
@Controller
public class ReportController {

    private static final Logger logger = LoggerFactory.getLogger(ReportController.class);

    private final PolicyJobRepository policyJobRepository;
    private final ReportRepository reportRepository;
    private final DocumentChunkRepository chunkRepository;
    private final QaService qaService;

    public ReportController(
            PolicyJobRepository policyJobRepository,
            ReportRepository reportRepository,
            DocumentChunkRepository chunkRepository,
            QaService qaService) {
        this.policyJobRepository = policyJobRepository;
        this.reportRepository = reportRepository;
        this.chunkRepository = chunkRepository;
        this.qaService = qaService;
    }

    /**
     * Display the full risk report for a document.
     * Renders all 5 sections: Overview, Summary, Obligations, Risk Taxonomy, Q&A.
     */
    @GetMapping("/documents/{id}/report")
    public String viewReport(@PathVariable("id") String id, Model model) {
        logger.info("Rendering report page: documentId={}", id);

        UUID jobUuid;
        try {
            jobUuid = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid document ID format: {}", id);
            return "error";
        }

        // Fetch job
        PolicyJob job = policyJobRepository.findByJobUuid(jobUuid)
                .orElse(null);
        if (job == null) {
            logger.warn("Job not found: {}", jobUuid);
            model.addAttribute("error", "Document not found");
            return "error";
        }

        // Fetch report
        Report report = reportRepository.findByJobUuid(jobUuid)
                .orElse(null);
        if (report == null) {
            logger.warn("Report not found for job: {}", jobUuid);
            model.addAttribute("error", "Report not available. Document may still be processing.");
            return "error";
        }

        // Fetch chunks for citation display
        List<DocumentChunk> chunks = chunkRepository.findByJobUuidOrderByChunkIndex(jobUuid);

        // Fetch Q&A interactions
        List<QaInteraction> qaInteractions = qaService.getQaInteractions(jobUuid);
        long questionCount = qaService.getQuestionCount(jobUuid);

        // Build model attributes
        model.addAttribute("job", job);
        model.addAttribute("report", report);
        model.addAttribute("chunks", chunks);
        model.addAttribute("qaInteractions", qaInteractions);
        model.addAttribute("questionCount", questionCount);
        model.addAttribute("maxQuestions", 3);
        model.addAttribute("canAskMore", questionCount < 3);

        // Extract report sections for easier template access
        Map<String, Object> reportData = new HashMap<>();
        reportData.put("documentOverview", report.getDocumentOverview());
        reportData.put("summaryBullets", report.getSummaryBullets());
        reportData.put("obligations", report.getObligations());
        reportData.put("restrictions", report.getRestrictions());
        reportData.put("terminationTriggers", report.getTerminationTriggers());
        reportData.put("riskTaxonomy", report.getRiskTaxonomy());
        model.addAttribute("reportData", reportData);

        logger.info("Report page rendered successfully: jobUuid={}, chunks={}, qaCount={}",
                jobUuid, chunks.size(), qaInteractions.size());

        return "report";
    }
}

