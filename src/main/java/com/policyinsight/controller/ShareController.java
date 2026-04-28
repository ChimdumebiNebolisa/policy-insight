package com.policyinsight.controller;

import com.policyinsight.service.AccessDeniedException;
import com.policyinsight.service.NotFoundException;
import com.policyinsight.service.ReportService;
import com.policyinsight.service.ShareLinkService;
import com.policyinsight.service.ShareResult;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
public class ShareController {

    private final ShareLinkService shareLinkService;
    private final ReportService reportService;

    public ShareController(ShareLinkService shareLinkService, ReportService reportService) {
        this.shareLinkService = shareLinkService;
        this.reportService = reportService;
    }

    @PostMapping("/share/{reportId}")
    public String create(@PathVariable UUID reportId, HttpServletRequest request, Model model) {
        reportService.reportForOwner(reportId, request.getCookies())
                .orElseThrow(() -> new AccessDeniedException("Share link is not available for this session."));
        ShareResult share = shareLinkService.createShareLink(reportId, baseUrl(request));
        model.addAttribute("share", share);
        return "fragments/share-link :: shareLink";
    }

    @GetMapping("/shared/{token}")
    public String shared(@PathVariable String token, Model model) {
        model.addAttribute("reportView", shareLinkService.sharedReport(token)
                .orElseThrow(() -> new NotFoundException("Shared report was not found or has expired.")));
        return "shared-report";
    }

    private String baseUrl(HttpServletRequest request) {
        String scheme = request.getHeader("X-Forwarded-Proto");
        if (scheme == null || scheme.isBlank()) {
            scheme = request.getScheme();
        }
        String host = request.getHeader("X-Forwarded-Host");
        if (host == null || host.isBlank()) {
            host = request.getServerName() + ":" + request.getServerPort();
        }
        return scheme + "://" + host;
    }
}
