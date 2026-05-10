package com.policyinsight.controller;

import com.policyinsight.service.ReportService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class JobStatusApiController {

    private final ReportService reportService;

    public JobStatusApiController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/api/jobs/{jobId}/status")
    public ApiJobStatusResponse status(@PathVariable UUID jobId, HttpServletRequest request) {
        return ApiJobStatusResponse.from(reportService.statusForOwner(jobId, request.getCookies()));
    }
}
