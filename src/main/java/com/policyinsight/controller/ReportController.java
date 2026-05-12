package com.policyinsight.controller;

import com.policyinsight.ai.dto.CitedClaim;
import com.policyinsight.model.JobStatus;
import com.policyinsight.service.AccessDeniedException;
import com.policyinsight.service.AsyncReportGenerationService;
import com.policyinsight.service.ReportService;
import com.policyinsight.service.ReportView;
import com.policyinsight.service.StatusView;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class ReportController {

    private final ReportService reportService;
    private final AsyncReportGenerationService asyncReportGenerationService;

    public ReportController(ReportService reportService, AsyncReportGenerationService asyncReportGenerationService) {
        this.reportService = reportService;
        this.asyncReportGenerationService = asyncReportGenerationService;
    }

    @GetMapping("/status/{jobId}")
    public String status(@PathVariable UUID jobId, HttpServletRequest request, Model model) {
        StatusView status = reportService.statusForOwner(jobId, request.getCookies());
        model.addAttribute("status", status);
        model.addAttribute("completed", status.status() == JobStatus.COMPLETED);
        return "fragments/job-status :: status";
    }

    @PostMapping("/fallback/{jobId}")
    public String fallbackReport(@PathVariable UUID jobId, HttpServletRequest request, Model model) {
        StatusView status = reportService.generateFallbackReportForOwner(jobId, request.getCookies());
        model.addAttribute("status", status);
        model.addAttribute("completed", status.status() == JobStatus.COMPLETED);
        return "fragments/job-status :: status";
    }

    @PostMapping("/retry/{jobId}")
    public String retry(@PathVariable UUID jobId, HttpServletRequest request, Model model) {
        reportService.validateAndResetForRetry(jobId, request.getCookies());
        asyncReportGenerationService.generate(jobId);
        StatusView status = reportService.statusForOwner(jobId, request.getCookies());
        model.addAttribute("status", status);
        model.addAttribute("completed", status.status() == JobStatus.COMPLETED);
        return "fragments/job-status :: status";
    }

    @GetMapping("/report/{reportId}")
    public String report(@PathVariable UUID reportId, HttpServletRequest request, Model model) {
        ReportView report = reportService.reportForOwner(reportId, request.getCookies())
                .orElseThrow(() -> new AccessDeniedException("Report is not available for this session."));
        model.addAttribute("reportView", report);
        return "report";
    }

    @GetMapping(value = "/report/{reportId}/export.md")
    @ResponseBody
    public ResponseEntity<String> exportMarkdown(@PathVariable UUID reportId, HttpServletRequest request) {
        ReportView report = reportService.reportForOwner(reportId, request.getCookies())
                .orElseThrow(() -> new AccessDeniedException("Report is not available for this session."));
        String markdown = buildMarkdown(report);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/markdown;charset=UTF-8"))
                .header("Content-Disposition", "attachment; filename=\"report.md\"")
                .body(markdown);
    }

    private static String buildMarkdown(ReportView view) {
        StringBuilder sb = new StringBuilder();
        String docType = view.demo() ? "Fictional sample" : (view.fallback() ? "Demo fallback" : "PolicyInsight report");
        sb.append("# PolicyInsight Report\n\n");
        sb.append("*").append(escapeMd(docType)).append(" — ").append(escapeMd(view.createdAtDisplay())).append("*\n\n");

        if (view.report().documentOverview() != null) {
            sb.append("## Document Overview\n\n");
            sb.append(escapeMd(view.report().documentOverview())).append("\n\n");
        }

        appendSection(sb, "Summary", view.report().summaryBullets(), view);
        appendSection(sb, "Key Obligations", view.report().obligations(), view);
        appendSection(sb, "Restrictions", view.report().restrictions(), view);
        appendSection(sb, "Termination Terms", view.report().terminationTriggers(), view);
        appendSection(sb, "Risks", view.report().riskTaxonomy(), view);

        sb.append("---\n\n*Exported from PolicyInsight. Not legal advice.*\n");
        return sb.toString();
    }

    private static void appendSection(StringBuilder sb, String title, List<CitedClaim> claims, ReportView view) {
        if (claims == null || claims.isEmpty()) {
            return;
        }
        sb.append("## ").append(title).append("\n\n");
        for (CitedClaim claim : claims) {
            sb.append("- ").append(escapeMd(claim.text()));
            if (claim.unsupported()) {
                sb.append(" *(unsupported)*");
            } else if (claim.chunkIds() != null && !claim.chunkIds().isEmpty()) {
                sb.append(" *(");
                for (int i = 0; i < claim.chunkIds().size(); i++) {
                    if (i > 0) {
                        sb.append(", ");
                    }
                    sb.append(escapeMd(view.sourceLabel(claim.chunkIds().get(i))));
                }
                sb.append(")*");
            }
            sb.append("\n");
        }
        sb.append("\n");
    }

    private static String escapeMd(String text) {
        if (text == null) {
            return "";
        }
        return text
                .replace("\\", "\\\\")
                .replace("`", "\\`")
                .replace("*", "\\*")
                .replace("_", "\\_")
                .replace("[", "\\[")
                .replace("]", "\\]")
                .replace("#", "\\#");
    }
}
