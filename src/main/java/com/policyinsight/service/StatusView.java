package com.policyinsight.service;

import com.policyinsight.model.JobStatus;
import java.util.UUID;

public record StatusView(UUID jobId, JobStatus status, UUID reportId, String safeErrorMessage) {
}
