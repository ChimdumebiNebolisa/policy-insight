package com.policyinsight.shared.dto;

import java.util.UUID;

/**
 * DTO for document upload response.
 */
public class UploadDocumentResponse {

    private UUID jobId;
    private String status;
    private String statusUrl;
    private String message;

    public UploadDocumentResponse() {
    }

    public UploadDocumentResponse(UUID jobId, String status, String statusUrl, String message) {
        this.jobId = jobId;
        this.status = status;
        this.statusUrl = statusUrl;
        this.message = message;
    }

    public UUID getJobId() {
        return jobId;
    }

    public void setJobId(UUID jobId) {
        this.jobId = jobId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getStatusUrl() {
        return statusUrl;
    }

    public void setStatusUrl(String statusUrl) {
        this.statusUrl = statusUrl;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}

