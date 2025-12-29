package com.policyinsight.shared.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO for share link response.
 */
public class ShareLinkResponse {

    private UUID jobId;
    private String shareUrl;
    private Instant expiresAt;
    private String message;

    public ShareLinkResponse() {
    }

    public ShareLinkResponse(UUID jobId, String shareUrl, Instant expiresAt, String message) {
        this.jobId = jobId;
        this.shareUrl = shareUrl;
        this.expiresAt = expiresAt;
        this.message = message;
    }

    public UUID getJobId() {
        return jobId;
    }

    public void setJobId(UUID jobId) {
        this.jobId = jobId;
    }

    public String getShareUrl() {
        return shareUrl;
    }

    public void setShareUrl(String shareUrl) {
        this.shareUrl = shareUrl;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}

