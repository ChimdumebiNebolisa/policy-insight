package com.policyinsight.service;

import com.policyinsight.ai.dto.QaAnswer;
import com.policyinsight.ai.dto.RiskReport;
import com.policyinsight.model.DocumentChunk;
import com.policyinsight.model.JobStatus;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
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
        List<DocumentChunk> chunks,
        List<QaInteractionView> qaHistory,
        String aiProviderLabel,
        String demoKey,
        JobStatus jobStatus
) {

    public String createdAtDisplay() {
        if (createdAt == null) {
            return "Created recently";
        }
        return DateTimeFormatter.ofPattern("MMM d, yyyy 'at' h:mm a 'UTC'")
                .withZone(ZoneOffset.UTC)
                .format(createdAt);
    }

    public String reportHeadline() {
        if (demo) {
            return displayDocumentTitle();
        }
        return "Policy analysis report";
    }

    public String displayDocumentTitle() {
        if (!demo) {
            return "Your document";
        }
        if (demoKey == null) {
            return "Fictional sample document";
        }
        if (SampleReportService.SAMPLE_DEMO_KEY.equals(demoKey)) {
            return "Fictional vendor services agreement";
        }
        if (SampleReportService.PRIVACY_POLICY_DEMO_KEY.equals(demoKey)) {
            return "Fictional SaaS privacy policy";
        }
        if (SampleReportService.EMPLOYMENT_POLICY_DEMO_KEY.equals(demoKey)) {
            return "Fictional employment policy";
        }
        if (SampleReportService.CAMPUS_STUDENT_POLICY_DEMO_KEY.equals(demoKey)) {
            return "Fictional campus student policy";
        }
        return "Fictional sample document";
    }

    public String jobStatusDisplay() {
        if (jobStatus == null) {
            return "Unknown";
        }
        return switch (jobStatus) {
            case COMPLETED -> "Completed";
            case FAILED -> "Failed";
            case PROCESSING, UPLOADED, TEXT_EXTRACTED, BUILDING_AI_REPORT, VALIDATING_CITATIONS -> "In progress";
        };
    }

    public String intakeSummary() {
        if (demo) {
            return "Fictional sample from the built-in catalog (deterministic report)";
        }
        return "PDF upload or pasted text (original files are not stored)";
    }

    public String sourceLabel(UUID chunkId) {
        if (chunkId == null || chunks == null) {
            return "Section";
        }
        for (int i = 0; i < chunks.size(); i++) {
            if (chunkId.equals(chunks.get(i).getId())) {
                return sectionLabel(i);
            }
        }
        return "Section";
    }

    private String sectionLabel(int zeroBasedIndex) {
        int n = chunks == null ? 0 : chunks.size();
        int section = zeroBasedIndex + 1;
        if (n <= 0) {
            return "Section " + section;
        }
        return "Section " + section + " of " + n;
    }

    public Map<UUID, String> sourceLabels() {
        Map<UUID, String> labels = new LinkedHashMap<>();
        if (chunks == null) {
            return labels;
        }
        for (int i = 0; i < chunks.size(); i++) {
            labels.put(chunks.get(i).getId(), sectionLabel(i));
        }
        return labels;
    }

    public List<DocumentChunk> citedChunks() {
        if (chunks == null || report == null) {
            return List.of();
        }
        Set<UUID> citedIds = reportCitedIds();
        return chunks.stream()
                .filter(chunk -> citedIds.contains(chunk.getId()))
                .toList();
    }

    public List<EvidenceItem> citedEvidence() {
        return buildEvidenceItems(reportCitedIds());
    }

    public List<EvidenceItem> allCitedEvidence() {
        Set<UUID> citedIds = new HashSet<>(reportCitedIds());
        if (qaHistory != null) {
            for (QaInteractionView qa : qaHistory) {
                if (qa.answer() != null && qa.answer().chunkIds() != null) {
                    citedIds.addAll(qa.answer().chunkIds());
                }
            }
        }
        return buildEvidenceItems(citedIds);
    }

    private List<EvidenceItem> buildEvidenceItems(Set<UUID> citedIds) {
        if (chunks == null || report == null) {
            return List.of();
        }
        int total = chunks.size();
        return chunks.stream()
                .filter(chunk -> citedIds.contains(chunk.getId()))
                .sorted(Comparator.comparingInt(DocumentChunk::getChunkIndex))
                .map(chunk -> new EvidenceItem(
                        chunk.getId(),
                        "Section " + (chunk.getChunkIndex() + 1) + " of " + total,
                        evidenceCategory(chunk.getTextContent()),
                        evidenceExcerpt(chunk.getTextContent())
                ))
                .toList();
    }

    public int chunkCount() {
        return chunks == null ? 0 : chunks.size();
    }

    public List<ReviewCard> sampleReviewCards() {
        if (!demo || report == null || demoKey == null) {
            return List.of();
        }
        if (SampleReportService.SAMPLE_DEMO_KEY.equals(demoKey)) {
            return vendorAgreementReviewCards();
        }
        if (SampleReportService.PRIVACY_POLICY_DEMO_KEY.equals(demoKey)) {
            return privacyPolicyReviewCards();
        }
        if (SampleReportService.EMPLOYMENT_POLICY_DEMO_KEY.equals(demoKey)) {
            return employmentPolicyReviewCards();
        }
        if (SampleReportService.CAMPUS_STUDENT_POLICY_DEMO_KEY.equals(demoKey)) {
            return campusPolicyReviewCards();
        }
        return List.of();
    }

    private List<ReviewCard> vendorAgreementReviewCards() {
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

    private List<ReviewCard> privacyPolicyReviewCards() {
        List<ReviewCard> cards = new ArrayList<>();
        cards.add(new ReviewCard(
                "Collection scope",
                "BrightLeaf collects account profile details, device diagnostics, and interaction logs to run support and reliability programs.",
                "Collection breadth affects notices, DPIAs, and subprocessors you need to track.",
                idsForClaims("account profile details", "device diagnostics")
        ));
        cards.add(new ReviewCard(
                "Retention and deletion",
                "Personal data is kept for twenty-four months after last activity unless a legal hold pauses deletion.",
                "Retention drives storage costs and subject-right timelines.",
                idsForClaims("twenty-four (24) months", "Retention exceptions")
        ));
        cards.add(new ReviewCard(
                "No sale of personal data",
                "The policy states BrightLeaf does not sell personal data to third-party advertisers.",
                "Confirms a key monetization guardrail for customer messaging.",
                idsForClaims("do not sell personal data to third-party advertisers")
        ));
        cards.add(new ReviewCard(
                "Security monitoring",
                "Security teams monitor logs for unauthorized access and restrict raw support transcripts to trained leads.",
                "Operational access controls should match how your team uses BrightLeaf day to day.",
                idsForClaims("monitor security logs for unauthorized access", "raw support transcripts is restricted to trained support leads")
        ));
        cards.add(new ReviewCard(
                "Policy change notice",
                "Material changes require at least thirty days' notice before new terms take effect.",
                "Gives customers time to object or exit before changes bind them.",
                idsForClaims("materially change this policy", "thirty (30) days before new terms take effect")
        ));
        cards.add(new ReviewCard(
                "Privacy request SLA",
                "Verified deletion requests are handled within forty-five days.",
                "SLA gaps can create regulatory exposure if your jurisdiction is stricter.",
                idsForClaims("respond to verified requests within forty-five (45) days")
        ));
        return cards;
    }

    private List<ReviewCard> employmentPolicyReviewCards() {
        List<ReviewCard> cards = new ArrayList<>();
        cards.add(new ReviewCard(
                "Training compliance",
                "Annual conduct training must be completed by March 31 each year.",
                "Missed training can block promotions or access to certain systems.",
                idsForClaims("annual conduct training by March 31")
        ));
        cards.add(new ReviewCard(
                "Time reporting",
                "Timesheets are due Monday 10:00 AM and payroll adjustments depend on approved submissions.",
                "Late timesheets ripple into pay corrections and manager workload.",
                idsForClaims("Monday 10:00 AM local time", "payroll adjustments depend on approved timesheets")
        ));
        cards.add(new ReviewCard(
                "Leave approvals",
                "Managers must acknowledge leave requests within five business days.",
                "Slow approvals affect coverage planning and employee trust.",
                idsForClaims("acknowledge leave requests within five (5) business days")
        ));
        cards.add(new ReviewCard(
                "Device and remote-work rules",
                "Confidential customer data cannot live on personal devices, and remote work from unapproved countries is blocked.",
                "Security and immigration rules need practical workflows for traveling staff.",
                idsForClaims("confidential customer data on personal devices", "remote work from unapproved countries is not allowed")
        ));
        cards.add(new ReviewCard(
                "Discipline ladder",
                "Unauthorized overtime can trigger progressive discipline, while harassment may lead to immediate termination.",
                "Escalation paths should be understood by managers before tough conversations.",
                idsForClaims("unauthorized overtime may result in corrective action", "harassment may result in immediate termination")
        ));
        cards.add(new ReviewCard(
                "Conflict disclosure",
                "Employees must disclose conflicts of interest in writing within five business days.",
                "Late disclosures undermine procurement integrity reviews.",
                idsForClaims("disclose conflicts of interest in writing")
        ));
        return cards;
    }

    private List<ReviewCard> campusPolicyReviewCards() {
        List<ReviewCard> cards = new ArrayList<>();
        cards.add(new ReviewCard(
                "Incident reporting",
                "Safety incidents must be reported within twenty-four hours to campus safety.",
                "Short windows mean residence staff need clear after-hours procedures.",
                idsForClaims("report safety incidents within twenty-four (24) hours")
        ));
        cards.add(new ReviewCard(
                "Residential quiet hours",
                "Quiet hours run from 10:00 PM to 7:00 AM during instructional periods.",
                "Impacts programming in halls and guest policies.",
                idsForClaims("quiet hours run from 10:00 PM to 7:00 AM")
        ));
        cards.add(new ReviewCard(
                "Appeals timing",
                "Appeals must be filed within seven calendar days after written notice.",
                "Students need proactive reminders so deadlines are not missed.",
                idsForClaims("appeals must be filed within seven (7) calendar days")
        ));
        cards.add(new ReviewCard(
                "Accommodations",
                "The campus schedules accommodation review meetings within ten business days of complete documentation.",
                "Backlogs here affect academic participation.",
                idsForClaims("accommodation review meetings within ten (10) business days")
        ));
        cards.add(new ReviewCard(
                "Digital conduct",
                "Anti-harassment standards apply to campus and online spaces.",
                "Student groups and class chats need the same standards as in-person conduct.",
                idsForClaims("anti-harassment standards apply to campus and online spaces")
        ));
        cards.add(new ReviewCard(
                "Facility access and retaliation",
                "Unauthorized lab or residence access is prohibited, and retaliation against reporters is banned.",
                "Pairs physical security expectations with whistleblower protections.",
                idsForClaims("unauthorized access to labs or residence facilities is prohibited", "retaliation against reporting parties is prohibited")
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
        if (normalized.contains("ten (10) business days") && normalized.contains("deliverable")) {
            return "Acceptance";
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
        if (normalized.contains("custom enterprise resource planning development") || normalized.contains("out-of-scope work")) {
            return "Scope";
        }
        if (normalized.contains("privacy@brightleaf")
                || (normalized.contains("personal data") && normalized.contains("retain"))) {
            return "Privacy & retention";
        }
        if (normalized.contains("do not sell personal data")) {
            return "Data sharing";
        }
        if (normalized.contains("security logs") || normalized.contains("support transcripts")) {
            return "Security";
        }
        if (normalized.contains("materially change this policy") || normalized.contains("forty-five (45) days")) {
            return "Policy changes";
        }
        if (normalized.contains("timesheet") || normalized.contains("payroll")) {
            return "Payroll";
        }
        if (normalized.contains("leave requests") || normalized.contains("remote work")) {
            return "Work arrangements";
        }
        if (normalized.contains("harassment") || normalized.contains("discipline")) {
            return "Conduct";
        }
        if (normalized.contains("quiet hours") || normalized.contains("residence hall")) {
            return "Residential life";
        }
        if (normalized.contains("appeals must be filed") || normalized.contains("accommodation review")) {
            return "Process & appeals";
        }
        if (normalized.contains("safety incidents") || normalized.contains("retaliation against reporting")) {
            return "Safety & reporting";
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
                "Provider has no obligation to perform out-of-scope work",
                "twenty-four (24) months",
                "request deletion through privacy@brightleaf.example",
                "do not sell personal data to third-party advertisers",
                "monitor security logs for unauthorized access",
                "raw support transcripts is restricted to trained support leads",
                "materially change this policy",
                "thirty (30) days before new terms take effect",
                "legal hold requests may suspend deletion",
                "respond to verified requests within forty-five (45) days",
                "annual conduct training by March 31",
                "Monday 10:00 AM local time",
                "acknowledge leave requests within five (5) business days",
                "disclose conflicts of interest in writing",
                "prohibited from storing confidential customer data on personal devices",
                "remote work from unapproved countries is not allowed",
                "unauthorized overtime may result in corrective action",
                "harassment may result in immediate termination",
                "payroll adjustments depend on approved timesheets",
                "report safety incidents within twenty-four (24) hours",
                "quiet hours run from 10:00 PM to 7:00 AM",
                "appeals must be filed within seven (7) calendar days",
                "accommodation review meetings within ten (10) business days",
                "anti-harassment standards apply to campus and online spaces",
                "unauthorized access to labs or residence facilities is prohibited",
                "retaliation against reporting parties is prohibited",
                "major misconduct can lead to suspension or expulsion",
                "failure to comply with sanctions may escalate penalties"
        )) {
            int index = text.indexOf(anchor);
            if (index >= 0) {
                return excerptAround(text, index, anchor.length());
            }
        }
        return compact(text, 420);
    }

    private String excerptAround(String text, int anchorStart, int anchorLength) {
        int start = Math.max(0, anchorStart - 140);
        int end = Math.min(text.length(), anchorStart + anchorLength + 200);
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

    private Set<UUID> reportCitedIds() {
        Set<UUID> ids = new HashSet<>();
        if (report == null) {
            return ids;
        }
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

    public record QaInteractionView(String question, QaAnswer answer) {
    }
}
