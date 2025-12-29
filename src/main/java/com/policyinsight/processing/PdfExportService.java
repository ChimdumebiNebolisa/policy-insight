package com.policyinsight.processing;

import com.policyinsight.shared.model.DocumentChunk;
import com.policyinsight.shared.model.PolicyJob;
import com.policyinsight.shared.model.Report;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Text;
import com.itextpdf.layout.properties.TextAlignment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Service for generating PDF exports of analysis reports.
 * Includes inline citations, watermark, and disclaimer.
 */
@Service
public class PdfExportService {

    private static final Logger logger = LoggerFactory.getLogger(PdfExportService.class);
    private static final String DISCLAIMER_TEXT = "⚠️ NOT LEGAL ADVICE\n\n" +
            "PolicyInsight provides clarity and risk surfacing. " +
            "It is not a substitute for legal counsel. " +
            "This report is for informational purposes only.";

    /**
     * Generate a PDF export of the report.
     *
     * @param job the policy job
     * @param report the analysis report
     * @param chunks document chunks for citation mapping
     * @return PDF as byte array
     * @throws IOException if PDF generation fails
     */
    public byte[] generatePdf(PolicyJob job, Report report, List<DocumentChunk> chunks) throws IOException {
        logger.info("Generating PDF export for job: {}", job.getJobUuid());

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PdfWriter writer = new PdfWriter(baos);
        PdfDocument pdfDoc = new PdfDocument(writer);
        Document document = new Document(pdfDoc, PageSize.A4);

        try {
            // Add watermark
            addWatermark(document);

            // Add header
            addHeader(document, job);

            // Add disclaimer at the top
            addDisclaimer(document);

            // Document Overview
            addSection(document, "Document Overview", buildOverviewSection(job, report));

            // Plain-English Summary
            addSection(document, "Plain-English Summary", buildSummarySection(report, chunks));

            // Obligations & Restrictions
            addSection(document, "Obligations & Restrictions", buildObligationsSection(report, chunks));

            // Risk Taxonomy
            addSection(document, "Risk Taxonomy", buildRiskTaxonomySection(report, chunks));

            // Footer with disclaimer
            addFooter(document);

            document.close();
            logger.info("PDF generated successfully: {} bytes", baos.size());
            return baos.toByteArray();

        } catch (Exception e) {
            logger.error("Failed to generate PDF for job: {}", job.getJobUuid(), e);
            throw new IOException("PDF generation failed", e);
        }
    }

    private void addWatermark(Document document) {
        // Add watermark text (simplified - appears on first page)
        // For production, use PdfPageEventHelper to add to all pages
        Paragraph watermark = new Paragraph("PolicyInsight Report")
                .setFontColor(ColorConstants.LIGHT_GRAY)
                .setFontSize(48)
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginBottom(20)
                .setOpacity(0.3f);
        document.add(watermark);
    }

    private void addHeader(Document document, PolicyJob job) {
        Paragraph header = new Paragraph()
                .add(new Text("PolicyInsight Analysis Report\n").setBold().setFontSize(18))
                .add(new Text("Generated: " + formatTimestamp(Instant.now()) + "\n").setFontSize(10))
                .add(new Text("Document: " + (job.getPdfFilename() != null ? job.getPdfFilename() : "Unknown") + "\n").setFontSize(10))
                .setTextAlignment(TextAlignment.LEFT)
                .setMarginBottom(20);
        document.add(header);
    }

    private void addDisclaimer(Document document) {
        Paragraph disclaimer = new Paragraph(DISCLAIMER_TEXT)
                .setBackgroundColor(ColorConstants.YELLOW)
                .setPadding(10)
                .setMarginBottom(20)
                .setFontSize(10)
                .setBold();
        document.add(disclaimer);
    }

    private void addSection(Document document, String title, String content) {
        Paragraph titlePara = new Paragraph(title)
                .setBold()
                .setFontSize(14)
                .setMarginTop(15)
                .setMarginBottom(10);
        document.add(titlePara);

        if (content != null && !content.isEmpty()) {
            Paragraph contentPara = new Paragraph(content)
                    .setFontSize(10)
                    .setMarginBottom(15);
            document.add(contentPara);
        } else {
            Paragraph emptyPara = new Paragraph("No data available for this section.")
                    .setFontSize(10)
                    .setFontColor(ColorConstants.GRAY)
                    .setMarginBottom(15);
            document.add(emptyPara);
        }
    }

    private void addFooter(Document document) {
        Paragraph footer = new Paragraph("\n\n" + DISCLAIMER_TEXT)
                .setFontSize(8)
                .setFontColor(ColorConstants.DARK_GRAY)
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginTop(20);
        document.add(footer);
    }

    private String buildOverviewSection(PolicyJob job, Report report) {
        StringBuilder sb = new StringBuilder();
        sb.append("File: ").append(job.getPdfFilename() != null ? job.getPdfFilename() : "Unknown").append("\n");
        sb.append("Type: ").append(job.getClassification() != null ? job.getClassification() : "Unknown").append("\n");
        if (job.getClassificationConfidence() != null) {
            sb.append("Confidence: ").append(String.format("%.2f", job.getClassificationConfidence())).append("\n");
        }
        if (job.getCompletedAt() != null) {
            sb.append("Analyzed: ").append(formatTimestamp(job.getCompletedAt())).append("\n");
        }
        if (job.getFileSizeBytes() != null) {
            sb.append("File Size: ").append(formatFileSize(job.getFileSizeBytes())).append("\n");
        }

        Map<String, Object> overview = report.getDocumentOverview();
        if (overview != null) {
            if (overview.containsKey("parties")) {
                sb.append("Parties: ").append(overview.get("parties")).append("\n");
            }
            if (overview.containsKey("effective_date")) {
                sb.append("Effective Date: ").append(overview.get("effective_date")).append("\n");
            }
        }

        return sb.toString();
    }

    private String buildSummarySection(Report report, List<DocumentChunk> chunks) {
        Map<String, Object> summary = report.getSummaryBullets();
        if (summary == null) {
            return null;
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> bullets = (List<Map<String, Object>>) summary.get("bullets");
        if (bullets == null || bullets.isEmpty()) {
            return null;
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < bullets.size() && i < 10; i++) {
            Map<String, Object> bullet = bullets.get(i);
            String text = (String) bullet.get("text");
            if (text != null) {
                sb.append("• ").append(text);
                
                // Add citation if available
                @SuppressWarnings("unchecked")
                List<Integer> chunkIds = (List<Integer>) bullet.get("chunk_ids");
                @SuppressWarnings("unchecked")
                List<Integer> pageRefs = (List<Integer>) bullet.get("page_refs");
                
                if (chunkIds != null && !chunkIds.isEmpty() && pageRefs != null && !pageRefs.isEmpty()) {
                    sb.append(" [Page ").append(pageRefs.get(0)).append("]");
                }
                sb.append("\n");
            }
        }
        return sb.toString();
    }

    private String buildObligationsSection(Report report, List<DocumentChunk> chunks) {
        StringBuilder sb = new StringBuilder();

        // Obligations
        Map<String, Object> obligations = report.getObligations();
        if (obligations != null) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> items = (List<Map<String, Object>>) obligations.get("items");
            if (items != null && !items.isEmpty()) {
                sb.append("Obligations:\n");
                for (Map<String, Object> item : items) {
                    String text = (String) item.get("text");
                    if (text != null) {
                        sb.append("• ").append(text);
                        addCitation(sb, item, chunks);
                        sb.append("\n");
                    }
                }
            }
        }

        // Restrictions
        Map<String, Object> restrictions = report.getRestrictions();
        if (restrictions != null) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> items = (List<Map<String, Object>>) restrictions.get("items");
            if (items != null && !items.isEmpty()) {
                sb.append("\nRestrictions:\n");
                for (Map<String, Object> item : items) {
                    String text = (String) item.get("text");
                    if (text != null) {
                        sb.append("• ").append(text);
                        addCitation(sb, item, chunks);
                        sb.append("\n");
                    }
                }
            }
        }

        // Termination Triggers
        Map<String, Object> terminationTriggers = report.getTerminationTriggers();
        if (terminationTriggers != null) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> items = (List<Map<String, Object>>) terminationTriggers.get("items");
            if (items != null && !items.isEmpty()) {
                sb.append("\nTermination Triggers:\n");
                for (Map<String, Object> item : items) {
                    String text = (String) item.get("text");
                    if (text != null) {
                        sb.append("• ").append(text);
                        addCitation(sb, item, chunks);
                        sb.append("\n");
                    }
                }
            }
        }

        return sb.length() > 0 ? sb.toString() : null;
    }

    private String buildRiskTaxonomySection(Report report, List<DocumentChunk> chunks) {
        Map<String, Object> riskTaxonomyMap = report.getRiskTaxonomy();
        if (riskTaxonomyMap == null) {
            return null;
        }

        StringBuilder sb = new StringBuilder();

        // Risk taxonomy is stored as a Map, but the template iterates it as a list
        // Check if it's a list directly or has a "categories" key
        Object riskTaxonomyObj = riskTaxonomyMap;
        if (riskTaxonomyMap.containsKey("categories")) {
            riskTaxonomyObj = riskTaxonomyMap.get("categories");
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> categories = riskTaxonomyObj instanceof List 
            ? (List<Map<String, Object>>) riskTaxonomyObj 
            : null;

        if (categories == null) {
            // Try to extract categories from the map structure
            // This handles the case where riskTaxonomy is a map with category names as keys
            for (Map.Entry<String, Object> entry : riskTaxonomyMap.entrySet()) {
                if (entry.getKey().equals("categories")) {
                    continue;
                }
                String categoryName = entry.getKey();
                Object categoryData = entry.getValue();
                
                if (categoryData instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> category = (Map<String, Object>) categoryData;
                    sb.append(categoryName).append(":\n");
                    
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> detected = (List<Map<String, Object>>) category.get("detected");
                    if (detected != null && !detected.isEmpty()) {
                        for (Map<String, Object> risk : detected) {
                            String text = (String) risk.get("text");
                            if (text != null) {
                                sb.append("• ").append(text);
                                addCitation(sb, risk, chunks);
                                sb.append("\n");
                            }
                        }
                    } else {
                        sb.append("✓ Not detected – No risks found in this category.\n");
                    }
                    sb.append("\n");
                }
            }
            return sb.length() > 0 ? sb.toString() : null;
        }

        // Handle as list of categories (standard structure)
        for (Map<String, Object> category : categories) {
            String categoryName = (String) category.get("name");
            if (categoryName != null) {
                sb.append(categoryName).append(":\n");
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> detected = (List<Map<String, Object>>) category.get("detected");
            if (detected != null && !detected.isEmpty()) {
                for (Map<String, Object> risk : detected) {
                    String text = (String) risk.get("text");
                    if (text != null) {
                        sb.append("• ").append(text);
                        addCitation(sb, risk, chunks);
                        sb.append("\n");
                    }
                }
            } else {
                sb.append("✓ Not detected – No risks found in this category.\n");
            }
            sb.append("\n");
        }

        return sb.length() > 0 ? sb.toString() : null;
    }

    private void addCitation(StringBuilder sb, Map<String, Object> item, List<DocumentChunk> chunks) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> citations = (List<Map<String, Object>>) item.get("citations");
        if (citations != null && !citations.isEmpty()) {
            Map<String, Object> citation = citations.get(0);
            Object pageNumber = citation.get("page_number");
            if (pageNumber != null) {
                sb.append(" [Page ").append(pageNumber).append("]");
            }
        }
    }

    private String formatTimestamp(Instant instant) {
        return DateTimeFormatter.ISO_INSTANT.format(instant);
    }

    private String formatFileSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.2f KB", bytes / 1024.0);
        } else {
            return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
        }
    }
}

