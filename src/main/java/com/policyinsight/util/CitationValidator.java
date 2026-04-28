package com.policyinsight.util;

import com.policyinsight.ai.dto.CitedClaim;
import com.policyinsight.ai.dto.QaAnswer;
import com.policyinsight.ai.dto.RiskReport;
import com.policyinsight.model.DocumentChunk;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class CitationValidator {

    public RiskReport validateReport(RiskReport report, List<DocumentChunk> chunks) {
        Set<UUID> validIds = validIds(chunks);
        return new RiskReport(
                report.documentOverview(),
                validateClaims(report.summaryBullets(), validIds),
                validateClaims(report.obligations(), validIds),
                validateClaims(report.restrictions(), validIds),
                validateClaims(report.terminationTriggers(), validIds),
                validateClaims(report.riskTaxonomy(), validIds)
        );
    }

    public QaAnswer validateAnswer(QaAnswer answer, List<DocumentChunk> chunks) {
        Set<UUID> validIds = validIds(chunks);
        List<UUID> kept = keepValid(answer.chunkIds(), validIds);
        if (kept.isEmpty()) {
            return new QaAnswer(answer.answer(), List.of(), true);
        }
        return new QaAnswer(answer.answer(), kept, false);
    }

    private List<CitedClaim> validateClaims(List<CitedClaim> claims, Set<UUID> validIds) {
        return claims.stream()
                .map(claim -> {
                    List<UUID> kept = keepValid(claim.chunkIds(), validIds);
                    if (kept.isEmpty()) {
                        return new CitedClaim(claim.text(), List.of(), true);
                    }
                    return new CitedClaim(claim.text(), kept, false);
                })
                .toList();
    }

    private List<UUID> keepValid(List<UUID> ids, Set<UUID> validIds) {
        if (ids == null) {
            return List.of();
        }
        return ids.stream()
                .filter(validIds::contains)
                .distinct()
                .toList();
    }

    private Set<UUID> validIds(List<DocumentChunk> chunks) {
        Set<UUID> ids = new HashSet<>();
        for (DocumentChunk chunk : chunks) {
            ids.add(chunk.getId());
        }
        return ids;
    }
}
