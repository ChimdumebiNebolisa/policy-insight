package com.policyinsight.service;

import com.policyinsight.ai.dto.RiskReport;
import com.policyinsight.model.DocumentChunk;
import java.util.List;
import java.util.UUID;

public record ReportView(UUID reportId, UUID jobId, RiskReport report, List<DocumentChunk> chunks) {
}
