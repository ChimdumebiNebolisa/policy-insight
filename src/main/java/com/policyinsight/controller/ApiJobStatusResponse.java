package com.policyinsight.controller;

import com.policyinsight.model.JobStatus;
import com.policyinsight.service.StatusView;
import java.time.Instant;
import java.util.UUID;

public record ApiJobStatusResponse(
        UUID jobId,
        JobStatus status,
        UUID reportId,
        String message,
        Instant createdAt,
        Instant updatedAt
) {

    public static ApiJobStatusResponse from(StatusView status) {
        return new ApiJobStatusResponse(
                status.jobId(),
                status.status(),
                status.reportId(),
                status.message(),
                status.createdAt(),
                status.updatedAt()
        );
    }
}
