package com.policyinsight.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Map;

/**
 * Serves the sample PDF demo and sample report without using DB, Pub/Sub, or external services.
 * Works in both normal and sleep mode.
 */
@Controller
public class SampleDemoController {

    private static final String SAMPLE_PDF_PATH = "static/sample/sample.pdf";

    @Value("${app.demo-sleep:false}")
    private boolean demoSleep;

    /**
     * Serves the bundled sample PDF with correct content-type.
     */
    @GetMapping("/sample-pdf")
    public ResponseEntity<Resource> samplePdf() {
        Resource resource = new ClassPathResource(SAMPLE_PDF_PATH);
        if (!resource.exists()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"sample.pdf\"")
                .body(resource);
    }

    /**
     * Renders the sample report page (static content, no DB).
     */
    @GetMapping("/sample-report")
    public String sampleReport() {
        return "sample-report";
    }

    /**
     * Returns whether demo-sleep is active (for frontend if needed).
     */
    @GetMapping("/demo-status")
    @ResponseBody
    public Map<String, Boolean> demoStatus() {
        return Map.of("demoSleep", demoSleep);
    }
}
