package com.policyinsight.service;

import com.policyinsight.ai.dto.RiskReport;
import com.policyinsight.model.DocumentChunk;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
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
        boolean fallback,
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

    public List<EvidenceItem> citedEvidence() {
        if (chunks == null || report == null) {
            return List.of();
        }
        return citedChunks().stream()
                .map(chunk -> new EvidenceItem(
                        chunk.getId(),
                        sourceLabel(chunk.getId()),
                        evidenceCategory(chunk.getTextContent()),
                        evidenceExcerpt(chunk.getTextContent())
                ))
                .toList();
    }

    public List<ReviewCard> sampleReviewCards() {
        if (!demo || report == null) {
            return List.of();
        }
        List<ReviewCard> cards = new ArrayList<>();
        cards.add(new ReviewCard(
                "Payment terms",
                "The client pays USD $8,000 per month, with invoices due within fifteen days.",
                "Late or disputed payment handling affects cash timing and renewal conversations.",
                idsForClaims("USD $8,000", "Payment is due within fifteen")
        ));
        cards.add(new ReviewCard(
                "Acceptance window",
                "Deliverables can be accepted automatically if the client does not reject them within ten business days.",
                "The review deadline needs an owner so issues are raised before acceptance is deemed final.",
                idsForClaims("ten (10) business days")
        ));
        cards.add(new ReviewCard(
                "Data handling",
                "Provider may process Client Data only as needed for the services and must protect confidential information.",
                "Operational access and safeguards should match the data shared under the agreement.",
                idsForClaims("Client Data", "confidential information", "reasonable care")
        ));
        cards.add(new ReviewCard(
                "Liability cap",
                "Liability may be capped to fees paid or payable during the prior six months, with listed exceptions.",
                "The cap may limit recovery for ordinary contract claims.",
                idsForClaims("six months", "Liability may be capped")
        ));
        cards.add(new ReviewCard(
                "Termination rights",
                "Material breach has a thirty-day cure period, while nonpayment may be cured within ten days.",
                "Different cure windows affect escalation timing and service continuity.",
                idsForClaims("thirty (30) days", "Nonpayment")
        ));
        cards.add(new ReviewCard(
                "Scope exclusions",
                "Schedule A excludes ERP development, payment processing, regulated reporting, legal advice, and production hosting unless added by change order.",
                "Excluded services can create delivery gaps unless they are added explicitly.",
                idsForClaims("excludes ERP development", "Excluded services")
        ));
        return cards;
    }

    private List<UUID> idsForClaims(String... needles) {
        Set<UUID> ids = new java.util.LinkedHashSet<>();
        Stream.of(
                        report.summaryBullets(),
                        report.obligations(),
                        report.restrictions(),
                        report.terminationTriggers(),
                        report.riskTaxonomy()
                )
                .filter(claims -> claims != null)
                .flatMap(List::stream)
                .filter(claim -> claim.text() != null)
                .filter(claim -> {
                    String text = claim.text().toLowerCase();
                    for (String needle : needles) {
                        if (text.contains(needle.toLowerCase())) {
                            return true;
                        }
                    }
                    return false;
                })
                .filter(claim -> claim.chunkIds() != null)
                .flatMap(claim -> claim.chunkIds().stream())
                .forEach(ids::add);
        return List.copyOf(ids);
    }

    private String evidenceCategory(String text) {
        String normalized = normalize(text);
        if (normalized.contains("payment is due") || normalized.contains("usd $8,000") || normalized.contains("one percent (1%) per month")) {
            return "Fees";
        }
        if (normalized.contains("ten (10) business days") || normalized.contains("deemed accepted")) {
            return "Acceptance";
        }
        if (normalized.contains("confidential information") || normalized.contains("client data") || normalized.contains("reasonable administrative")) {
            return "Confidentiality";
        }
        if (normalized.contains("provider retains all right, title, and interest")) {
            return "IP";
        }
        if (normalized.contains("six (6) months before the event giving rise to liability") || normalized.contains("sole remedy will be reperformance")) {
            return "Liability";
        }
        if (normalized.contains("materially breaches") || normalized.contains("nonpayment may be cured") || normalized.contains("transition assistance")) {
            return "Termination";
        }
        if (normalized.contains("the services do not include custom enterprise resource planning development") || normalized.contains("out-of-scope work")) {
            return "Scope";
        }
        return "Evidence";
    }

    private String evidenceExcerpt(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        for (String anchor : List.of(
                "Payment is due within fifteen (15) days after the invoice date",
                "USD $8,000",
                "ten (10) business days",
                "reasonable administrative, technical, and physical safeguards",
                "Provider retains all right, title, and interest in Provider tools",
                "sole remedy will be reperformance",
                "six (6) months before the event giving rise to liability",
                "materially breaches",
                "Nonpayment may be cured within ten (10) days",
                "transition assistance for up to thirty (30) days",
                "laws of the State of Columbia",
                "The Services do not include custom enterprise resource planning development",
                "Provider has no obligation to perform out-of-scope work"
        )) {
            int index = text.indexOf(anchor);
            if (index >= 0) {
                return excerptAround(text, index, anchor.length());
            }
        }
        return compact(text, 280);
    }

    private String excerptAround(String text, int anchorStart, int anchorLength) {
        int start = Math.max(0, anchorStart - 120);
        int end = Math.min(text.length(), anchorStart + anchorLength + 160);
        String excerpt = text.substring(start, end).replaceAll("\\s+", " ").trim();
        if (start > 0) {
            excerpt = "... " + excerpt;
        }
        if (end < text.length()) {
            excerpt = excerpt + " ...";
        }
        return excerpt;
    }

    private String compact(String text, int maxLength) {
        String compacted = text.replaceAll("\\s+", " ").trim();
        if (compacted.length() <= maxLength) {
            return compacted;
        }
        return compacted.substring(0, maxLength).trim() + " ...";
    }

    private String normalize(String text) {
        if (text == null) {
            return "";
        }
        return text.toLowerCase();
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

    public record ReviewCard(String title, String finding, String whyItMatters, List<UUID> sourceIds) {
    }

    public record EvidenceItem(UUID sourceId, String label, String category, String excerpt) {
    }
}
