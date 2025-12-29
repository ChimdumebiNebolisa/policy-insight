package com.policyinsight.web;

import com.policyinsight.api.ShareLinkService;
import com.policyinsight.shared.model.DocumentChunk;
import com.policyinsight.shared.model.PolicyJob;
import com.policyinsight.shared.model.Report;
import com.policyinsight.shared.model.ShareLink;
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
 * Controller for read-only shared report views.
 */
@Controller
public class ShareReportController {

    private static final Logger logger = LoggerFactory.getLogger(ShareReportController.class);

    private final PolicyJobRepository policyJobRepository;
    private final ReportRepository reportRepository;
    private final DocumentChunkRepository chunkRepository;
    private final ShareLinkService shareLinkService;

    public ShareReportController(
            PolicyJobRepository policyJobRepository,
            ReportRepository reportRepository,
            DocumentChunkRepository chunkRepository,
            ShareLinkService shareLinkService) {
        this.policyJobRepository = policyJobRepository;
        this.reportRepository = reportRepository;
        this.chunkRepository = chunkRepository;
        this.shareLinkService = shareLinkService;
    }

    /**
     * Display a read-only shared report view.
     * Validates the share token and checks expiration.
     */
    @GetMapping("/documents/{id}/share/{token}")
    public String viewSharedReport(
            @PathVariable("id") String id,
            @PathVariable("token") String token,
            Model model) {

        logger.info("Shared report view request: documentId={}, token={}", id, token);

        UUID jobUuid;
        UUID shareToken;
        try {
            jobUuid = UUID.fromString(id);
            shareToken = UUID.fromString(token);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid UUID format: id={}, token={}", id, token);
            model.addAttribute("error", "Invalid share link format");
            return "error";
        }

        // Validate share link
        ShareLink shareLink = shareLinkService.validateAndAccessShareLink(shareToken);
        if (shareLink == null) {
            logger.warn("Invalid or expired share link: token={}", shareToken);
            model.addAttribute("error", "Share link not found or expired");
            return "error";
        }

        // Verify job UUID matches
        if (!shareLink.getJobUuid().equals(jobUuid)) {
            logger.warn("Job UUID mismatch: expected={}, got={}",
                    shareLink.getJobUuid(), jobUuid);
            model.addAttribute("error", "Invalid share link");
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
            model.addAttribute("error", "Report not available");
            return "error";
        }

        // Fetch chunks for citation display
        List<DocumentChunk> chunks = chunkRepository.findByJobUuidOrderByChunkIndex(jobUuid);

        // Build model attributes (same as regular report, but read-only)
        model.addAttribute("job", job);
        model.addAttribute("report", report);
        model.addAttribute("chunks", chunks);
        model.addAttribute("shareLink", shareLink);
        model.addAttribute("isReadOnly", true);
        model.addAttribute("expiresAt", shareLink.getExpiresAt());

        // Extract report sections for easier template access
        Map<String, Object> reportData = new HashMap<>();
        reportData.put("documentOverview", report.getDocumentOverview());
        reportData.put("summaryBullets", report.getSummaryBullets());
        reportData.put("obligations", report.getObligations());
        reportData.put("restrictions", report.getRestrictions());
        reportData.put("terminationTriggers", report.getTerminationTriggers());
        reportData.put("riskTaxonomy", report.getRiskTaxonomy());
        model.addAttribute("reportData", reportData);

        logger.info("Shared report page rendered: jobUuid={}, accessCount={}",
                jobUuid, shareLink.getAccessCount());

        return "share-report";
    }
}

