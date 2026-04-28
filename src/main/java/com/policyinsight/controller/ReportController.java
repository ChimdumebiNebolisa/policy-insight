package com.policyinsight.controller;

import com.policyinsight.model.JobStatus;
import com.policyinsight.service.AccessDeniedException;
import com.policyinsight.service.ReportService;
import com.policyinsight.service.ReportView;
import com.policyinsight.service.StatusView;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/status/{jobId}")
    public String status(@PathVariable UUID jobId, HttpServletRequest request, Model model) {
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
}
