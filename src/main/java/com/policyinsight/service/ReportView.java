package com.policyinsight.service;

import com.policyinsight.ai.dto.RiskReport;
import com.policyinsight.model.DocumentChunk;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    public String sourceLabel(UUID chunkId) {
        if (chunkId == null || chunks == null) {
            return "Source";
        }
        for (int i = 0; i < chunks.size(); i++) {
            if (chunkId.equals(chunks.get(i).getId())) {
                return "Source " + (i + 1);
            }
        }
        return "Source";
    }

    public Map<UUID, String> sourceLabels() {
        Map<UUID, String> labels = new LinkedHashMap<>();
        if (chunks == null) {
            return labels;
        }
        for (int i = 0; i < chunks.size(); i++) {
            labels.put(chunks.get(i).getId(), "Source " + (i + 1));
        }
        return labels;
    }
}
