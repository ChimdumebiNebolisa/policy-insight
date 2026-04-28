package com.policyinsight.service;

import com.policyinsight.ai.dto.RiskReport;
import com.policyinsight.model.DocumentChunk;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

public record ReportView(
        UUID reportId,
        UUID jobId,
        Instant createdAt,
        boolean demo,
        RiskReport report,
        List<DocumentChunk> chunks
) {

    public String createdAtDisplay() {
        if (createdAt == null) {
            return "Created recently";
        }
        return DateTimeFormatter.ofPattern("MMM d, yyyy 'at' h:mm a 'UTC'")
                .withZone(ZoneOffset.UTC)
                .format(createdAt);
    }
}
