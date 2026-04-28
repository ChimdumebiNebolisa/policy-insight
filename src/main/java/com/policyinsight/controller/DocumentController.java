package com.policyinsight.controller;

import com.policyinsight.service.DocumentService;
import com.policyinsight.security.RateLimitService;
import com.policyinsight.service.RateLimitExceededException;
import com.policyinsight.service.UploadResult;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

@Controller
public class DocumentController {

    private final DocumentService documentService;
    private final RateLimitService rateLimitService;

    public DocumentController(DocumentService documentService, RateLimitService rateLimitService) {
        this.documentService = documentService;
        this.rateLimitService = rateLimitService;
    }

    @PostMapping("/upload")
    public String upload(
            @RequestParam("file") MultipartFile file,
            HttpServletRequest request,
            HttpServletResponse response,
            Model model
    ) {
        if (!rateLimitService.allow("upload:" + clientIp(request))) {
            throw new RateLimitExceededException("Too many upload requests. Try again shortly.");
        }
        UploadResult result = documentService.upload(file);
        Cookie cookie = new Cookie(ownerCookieName(result.jobId().toString()), result.ownerToken());
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(60 * 60 * 2);
        response.addCookie(cookie);
        model.addAttribute("jobId", result.jobId());
        model.addAttribute("chunkCount", result.chunkCount());
        return "fragments/upload-started :: uploadStarted";
    }

    public static String ownerCookieName(String jobId) {
        return "PI_OWNER_" + jobId.replace("-", "");
    }

    private String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
