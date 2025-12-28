package com.policyinsight.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/documents")
@Tag(name = "Documents", description = "Document upload and status endpoints")
public class DocumentController {

    @PostMapping("/upload")
    @Operation(summary = "Upload a PDF document for analysis",
               description = "Accepts a PDF file and returns a job ID for tracking the analysis process")
    public ResponseEntity<Map<String, Object>> uploadDocument(
            @Parameter(description = "PDF file to upload (max 20 MB)")
            @RequestParam("file") MultipartFile file) {

        // Stubbed implementation for Milestone 1
        UUID jobId = UUID.randomUUID();

        Map<String, Object> response = new HashMap<>();
        response.put("jobId", jobId.toString());
        response.put("status", "PENDING");
        response.put("statusUrl", "/api/documents/" + jobId + "/status");
        response.put("message", "Document uploaded successfully. Processing will begin shortly.");

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @GetMapping("/{id}/status")
    @Operation(summary = "Get document processing status",
               description = "Returns the current status of a document analysis job")
    public ResponseEntity<Map<String, Object>> getDocumentStatus(
            @Parameter(description = "Job ID returned from upload endpoint")
            @PathVariable("id") String id) {

        // Stubbed implementation for Milestone 1
        Map<String, Object> response = new HashMap<>();
        response.put("jobId", id);
        response.put("status", "PENDING");
        response.put("message", "Processing has not started yet. This is a stubbed response.");

        return ResponseEntity.ok(response);
    }
}

