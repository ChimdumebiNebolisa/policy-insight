package com.policyinsight.service;

import java.util.UUID;

public record SampleReportResult(UUID jobId, UUID reportId, String ownerToken, int chunkCount) {
}
