package com.policyinsight.controller;

import com.policyinsight.ai.dto.QaAnswer;
import com.policyinsight.security.RateLimitService;
import com.policyinsight.service.AccessDeniedException;
import com.policyinsight.service.QaService;
import com.policyinsight.service.RateLimitExceededException;
import com.policyinsight.service.ReportService;
import com.policyinsight.service.ReportView;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class QaController {

    private final QaService qaService;
    private final ReportService reportService;
    private final RateLimitService rateLimitService;

    public QaController(QaService qaService, ReportService reportService, RateLimitService rateLimitService) {
        this.qaService = qaService;
        this.reportService = reportService;
        this.rateLimitService = rateLimitService;
    }

    @PostMapping("/qa/{reportId}")
    public String ask(
            @PathVariable UUID reportId,
            @RequestParam("question") String question,
            HttpServletRequest request,
            Model model
    ) {
        ReportView report = reportService.reportForOwner(reportId, request.getCookies())
                .orElseThrow(() -> new AccessDeniedException("Q&A is not available for this session."));
        if (report.demo()) {
            throw new AccessDeniedException("Q&A is available for uploaded documents.");
        }
        if (!rateLimitService.allow("qa:" + clientIp(request))) {
            throw new RateLimitExceededException("Too many Q&A requests. Try again shortly.");
        }
        QaAnswer answer = qaService.answer(reportId, question);
        model.addAttribute("answer", answer);
        model.addAttribute("sourceLabels", report.sourceLabels());
        return "fragments/qa-answer :: qaAnswer";
    }

    private String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
