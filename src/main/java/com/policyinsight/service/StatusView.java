package com.policyinsight.service;

import com.policyinsight.model.JobStatus;
import java.time.Instant;
import java.util.UUID;

public record StatusView(
        UUID jobId,
        JobStatus status,
        UUID reportId,
        String safeErrorMessage,
        boolean fallbackAvailable,
        Instant createdAt,
        Instant updatedAt
) {

    public String message() {
        return switch (status) {
            case FAILED -> safeErrorMessage != null && !safeErrorMessage.isBlank()
                    ? safeErrorMessage
                    : "Report generation failed.";
            case COMPLETED -> "Report is ready.";
            case UPLOADED, TEXT_EXTRACTED, BUILDING_AI_REPORT, VALIDATING_CITATIONS, PROCESSING ->
                    "Report is still processing.";
        };
    }

    public String kicker() {
        return switch (status) {
            case FAILED -> "Review stopped";
            case COMPLETED -> "Report ready";
            case UPLOADED -> "Upload received";
            case TEXT_EXTRACTED -> "Text extracted";
            case BUILDING_AI_REPORT -> "Building AI report";
            case VALIDATING_CITATIONS -> "Validating citations";
            case PROCESSING -> "Processing document";
        };
    }

    public String title() {
        return switch (status) {
            case FAILED -> "We could not finish this report";
            case COMPLETED -> "Your cited report is ready";
            case UPLOADED -> "Upload received";
            case TEXT_EXTRACTED -> "Text extracted";
            case BUILDING_AI_REPORT -> "Building AI report";
            case VALIDATING_CITATIONS -> "Validating citations";
            case PROCESSING -> "Building your report";
        };
    }

    public String stepClass(String step) {
        int current = rank(status);
        int target = switch (step) {
            case "UPLOAD_RECEIVED" -> 1;
            case "TEXT_EXTRACTED" -> 2;
            case "BUILDING_AI_REPORT" -> 3;
            case "VALIDATING_CITATIONS" -> 4;
            case "COMPLETED" -> 5;
            default -> 0;
        };
        if (status == JobStatus.FAILED) {
            return target < current ? "step-done" : "";
        }
        if (current == target) {
            return "step-active";
        }
        return current > target ? "step-done" : "";
    }

    private int rank(JobStatus status) {
        return switch (status) {
            case UPLOADED -> 1;
            case TEXT_EXTRACTED -> 2;
            case BUILDING_AI_REPORT, PROCESSING -> 3;
            case VALIDATING_CITATIONS -> 4;
            case COMPLETED -> 5;
            case FAILED -> 5;
        };
    }
}
