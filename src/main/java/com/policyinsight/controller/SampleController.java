package com.policyinsight.controller;

import com.policyinsight.service.SampleReportResult;
import com.policyinsight.service.SampleReportService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class SampleController {

    private final SampleReportService sampleReportService;

    public SampleController(SampleReportService sampleReportService) {
        this.sampleReportService = sampleReportService;
    }

    @GetMapping({"/sample", "/sample-report"})
    public String sample(HttpServletResponse response, RedirectAttributes redirectAttributes) {
        SampleReportResult result = sampleReportService.openSampleReport();
        Cookie cookie = new Cookie(DocumentController.ownerCookieName(result.jobId().toString()), result.ownerToken());
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(60 * 60 * 2);
        response.addCookie(cookie);
        redirectAttributes.addFlashAttribute("sampleNotice", "Fictional sample agreement. Demonstration only.");
        return "redirect:/report/" + result.reportId();
    }
}
