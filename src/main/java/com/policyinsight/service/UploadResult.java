package com.policyinsight.service;

import java.util.UUID;

public record UploadResult(UUID jobId, String ownerToken, int chunkCount) {
}
