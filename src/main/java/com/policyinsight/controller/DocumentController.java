package com.policyinsight.controller;

import com.policyinsight.service.DocumentService;
import com.policyinsight.service.UploadResult;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

@Controller
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @PostMapping("/upload")
    public String upload(@RequestParam("file") MultipartFile file, HttpServletResponse response, Model model) {
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
}
