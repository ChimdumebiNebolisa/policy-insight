package com.policyinsight.service;

import com.policyinsight.ai.dto.RiskReport;
import com.policyinsight.model.DocumentChunk;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

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

    public List<DocumentChunk> citedChunks() {
        if (chunks == null || report == null) {
            return List.of();
        }
        Set<UUID> citedIds = citedIds();
        return chunks.stream()
                .filter(chunk -> citedIds.contains(chunk.getId()))
                .toList();
    }

    private Set<UUID> citedIds() {
        Set<UUID> ids = new HashSet<>();
        Stream.of(
                        report.summaryBullets(),
                        report.obligations(),
                        report.restrictions(),
                        report.terminationTriggers(),
                        report.riskTaxonomy()
                )
                .filter(claims -> claims != null)
                .flatMap(List::stream)
                .filter(claim -> claim.chunkIds() != null)
                .flatMap(claim -> claim.chunkIds().stream())
                .forEach(ids::add);
        return ids;
    }
}
