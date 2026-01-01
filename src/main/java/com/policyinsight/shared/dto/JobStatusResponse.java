package com.policyinsight.shared.dto;

import java.util.UUID;

/**
 * DTO for job status response.
 */
public class JobStatusResponse {

    private UUID jobId;
    private String status;
    private String message;
    private String reportUrl;
    private ProgressInfo progress;

    public JobStatusResponse() {
    }

    public JobStatusResponse(UUID jobId, String status, String message) {
        this.jobId = jobId;
        this.status = status;
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

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getReportUrl() {
        return reportUrl;
    }

    public void setReportUrl(String reportUrl) {
        this.reportUrl = reportUrl;
    }

    public ProgressInfo getProgress() {
        return progress;
    }

    public void setProgress(ProgressInfo progress) {
        this.progress = progress;
    }

    /**
     * Nested DTO for progress information.
     */
    public static class ProgressInfo {
        private String stage;
        private Integer percentComplete;
        private Integer estimatedSecondsRemaining;

        public ProgressInfo() {
        }

        public ProgressInfo(String stage, Integer percentComplete, Integer estimatedSecondsRemaining) {
            this.stage = stage;
            this.percentComplete = percentComplete;
            this.estimatedSecondsRemaining = estimatedSecondsRemaining;
        }

        public String getStage() {
            return stage;
        }

        public void setStage(String stage) {
            this.stage = stage;
        }

        public Integer getPercentComplete() {
            return percentComplete;
        }

        public void setPercentComplete(Integer percentComplete) {
            this.percentComplete = percentComplete;
        }

        public Integer getEstimatedSecondsRemaining() {
            return estimatedSecondsRemaining;
        }

        public void setEstimatedSecondsRemaining(Integer estimatedSecondsRemaining) {
            this.estimatedSecondsRemaining = estimatedSecondsRemaining;
        }
    }
}

