package com.policyinsight.service;

public record CleanupResult(
        int expiredShareLinksDeleted,
        int staleProcessingJobsMarkedFailed,
        int failedJobsDeleted,
        int completedJobsDeleted
) {
}
