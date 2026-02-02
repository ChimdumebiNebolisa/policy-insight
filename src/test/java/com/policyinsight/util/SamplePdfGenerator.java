package com.policyinsight.util;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Utility to generate a realistic multi-page sample PDF for the demo.
 * Run the test once to regenerate static/sample/sample.pdf.
 */
public class SamplePdfGenerator {

    /**
     * Run this test manually to regenerate the sample PDF.
     * Disabled by default so it doesn't run in CI.
     */
    @Test
    @Disabled("Run manually to regenerate sample.pdf")
    public void regenerateSamplePdf() throws IOException {
        Path outputPath = Paths.get("src/main/resources/static/sample/sample.pdf");
        generateSamplePdf(outputPath);
        System.out.println("Generated: " + outputPath.toAbsolutePath());
    }

    private static final float MARGIN = 72; // 1 inch
    private static final float LINE_HEIGHT = 14;
    private static final float TITLE_SIZE = 18;
    private static final float HEADING_SIZE = 14;
    private static final float BODY_SIZE = 11;

    public static void main(String[] args) throws IOException {
        Path outputPath = Paths.get("src/main/resources/static/sample/sample.pdf");
        generateSamplePdf(outputPath);
        System.out.println("Generated: " + outputPath.toAbsolutePath());
    }

    public static void generateSamplePdf(Path outputPath) throws IOException {
        try (PDDocument document = new PDDocument()) {
            int[] pageNumbers = {1, 2, 3};

            // Page 1: Title, Scope, Definitions
            PDPage page1 = addPage(document);
            try (PDPageContentStream cs = new PDPageContentStream(document, page1)) {
                float y = page1.getMediaBox().getHeight() - MARGIN;

                y = drawCenteredText(cs, "SAMPLE SERVICE AGREEMENT", TITLE_SIZE, y, page1);
                y -= LINE_HEIGHT * 2;

                y = drawCenteredText(cs, "Effective Date: January 1, 2025", BODY_SIZE, y, page1);
                y -= LINE_HEIGHT * 3;

                y = drawHeading(cs, "1. SCOPE OF SERVICES", y);
                y = drawParagraph(cs, "Provider agrees to deliver software-as-a-service (SaaS) analytics " +
                        "capabilities to Customer. Services include document ingestion, text extraction, " +
                        "risk classification, and report generation. Provider shall maintain 99.5% uptime " +
                        "measured monthly, excluding scheduled maintenance windows.", y);
                y -= LINE_HEIGHT;

                y = drawParagraph(cs, "Customer acknowledges that Services are provided 'as-is' for " +
                        "informational purposes. Output does not constitute legal advice. Customer is " +
                        "responsible for reviewing results with qualified legal counsel before relying " +
                        "on any analysis.", y);
                y -= LINE_HEIGHT * 2;

                y = drawHeading(cs, "2. DEFINITIONS", y);
                y = drawParagraph(cs, "'Confidential Information' means any non-public data disclosed by " +
                        "either party, including but not limited to uploaded documents, analysis results, " +
                        "and system architecture details.", y);
                y -= LINE_HEIGHT;

                y = drawParagraph(cs, "'Processing' means ingestion, parsing, storage, analysis, and " +
                        "delivery of Customer documents through Provider's systems.", y);

                drawFooter(cs, page1, 1);
            }

            // Page 2: Payment, Term, Termination
            PDPage page2 = addPage(document);
            try (PDPageContentStream cs = new PDPageContentStream(document, page2)) {
                float y = page2.getMediaBox().getHeight() - MARGIN;

                y = drawHeading(cs, "3. PAYMENT TERMS", y);
                y = drawParagraph(cs, "Customer shall pay the applicable subscription fee monthly in " +
                        "advance. Fees are non-refundable except as required by law. Provider may " +
                        "adjust pricing upon 30 days written notice; Customer may terminate without " +
                        "penalty if objecting to the increase.", y);
                y -= LINE_HEIGHT;

                y = drawParagraph(cs, "Late payments accrue interest at 1.5% per month. Provider may " +
                        "suspend access after 15 days of non-payment.", y);
                y -= LINE_HEIGHT * 2;

                y = drawHeading(cs, "4. TERM AND RENEWAL", y);
                y = drawParagraph(cs, "Initial term is twelve (12) months from Effective Date. " +
                        "Agreement auto-renews for successive one-year terms unless either party " +
                        "provides written notice at least 30 days before renewal.", y);
                y -= LINE_HEIGHT * 2;

                y = drawHeading(cs, "5. TERMINATION", y);
                y = drawParagraph(cs, "Either party may terminate for material breach if the breach " +
                        "remains uncured for 30 days after written notice. Provider may terminate " +
                        "immediately if Customer violates Acceptable Use Policy.", y);
                y -= LINE_HEIGHT;

                y = drawParagraph(cs, "Upon termination: (a) Customer loses access to Services; " +
                        "(b) Provider deletes Customer data within 30 days unless retention required " +
                        "by law; (c) accrued payment obligations survive.", y);
                y -= LINE_HEIGHT * 2;

                y = drawHeading(cs, "6. DATA HANDLING", y);
                y = drawParagraph(cs, "Customer retains ownership of uploaded documents. Provider " +
                        "obtains a limited license to process documents solely to deliver Services. " +
                        "Provider may aggregate anonymized usage metrics for service improvement.", y);

                drawFooter(cs, page2, 2);
            }

            // Page 3: Liability, Confidentiality, Governing Law
            PDPage page3 = addPage(document);
            try (PDPageContentStream cs = new PDPageContentStream(document, page3)) {
                float y = page3.getMediaBox().getHeight() - MARGIN;

                y = drawHeading(cs, "7. LIMITATION OF LIABILITY", y);
                y = drawParagraph(cs, "TO THE MAXIMUM EXTENT PERMITTED BY LAW, PROVIDER'S TOTAL " +
                        "LIABILITY SHALL NOT EXCEED FEES PAID BY CUSTOMER IN THE TWELVE (12) MONTHS " +
                        "PRECEDING THE CLAIM.", y);
                y -= LINE_HEIGHT;

                y = drawParagraph(cs, "NEITHER PARTY SHALL BE LIABLE FOR INDIRECT, INCIDENTAL, " +
                        "SPECIAL, CONSEQUENTIAL, OR PUNITIVE DAMAGES, INCLUDING LOST PROFITS OR " +
                        "BUSINESS INTERRUPTION, EVEN IF ADVISED OF THEIR POSSIBILITY.", y);
                y -= LINE_HEIGHT * 2;

                y = drawHeading(cs, "8. CONFIDENTIALITY", y);
                y = drawParagraph(cs, "Each party shall protect Confidential Information using at " +
                        "least the same care it uses for its own confidential data. Disclosure is " +
                        "permitted only to employees and contractors with need-to-know who are bound " +
                        "by confidentiality obligations.", y);
                y -= LINE_HEIGHT;

                y = drawParagraph(cs, "Obligations continue for three (3) years after disclosure or, " +
                        "for trade secrets, until information becomes public through no fault of " +
                        "receiving party.", y);
                y -= LINE_HEIGHT * 2;

                y = drawHeading(cs, "9. GOVERNING LAW AND DISPUTES", y);
                y = drawParagraph(cs, "This Agreement is governed by the laws of Delaware, USA, " +
                        "without regard to conflict-of-law principles. Any dispute shall be resolved " +
                        "through binding arbitration in Wilmington, DE under AAA Commercial Rules.", y);
                y -= LINE_HEIGHT;

                y = drawParagraph(cs, "Each party waives the right to jury trial and class action. " +
                        "Arbitration award is final and enforceable in any court of competent " +
                        "jurisdiction.", y);
                y -= LINE_HEIGHT * 2;

                y = drawHeading(cs, "10. MISCELLANEOUS", y);
                y = drawParagraph(cs, "This Agreement constitutes the entire understanding between " +
                        "the parties. Amendments require written consent. Failure to enforce any " +
                        "provision is not a waiver. If any provision is unenforceable, remaining " +
                        "provisions remain in effect.", y);

                drawFooter(cs, page3, 3);
            }

            document.save(outputPath.toFile());
        }
    }

    private static PDPage addPage(PDDocument document) {
        PDPage page = new PDPage(PDRectangle.LETTER);
        document.addPage(page);
        return page;
    }

    private static float drawCenteredText(PDPageContentStream cs, String text, float fontSize, float y, PDPage page) throws IOException {
        PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
        float textWidth = font.getStringWidth(text) / 1000 * fontSize;
        float x = (page.getMediaBox().getWidth() - textWidth) / 2;

        cs.beginText();
        cs.setFont(font, fontSize);
        cs.newLineAtOffset(x, y);
        cs.showText(text);
        cs.endText();

        return y - fontSize;
    }

    private static float drawHeading(PDPageContentStream cs, String text, float y) throws IOException {
        cs.beginText();
        cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), HEADING_SIZE);
        cs.newLineAtOffset(MARGIN, y);
        cs.showText(text);
        cs.endText();

        return y - HEADING_SIZE - LINE_HEIGHT;
    }

    private static float drawParagraph(PDPageContentStream cs, String text, float y) throws IOException {
        PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        float maxWidth = PDRectangle.LETTER.getWidth() - 2 * MARGIN;

        cs.beginText();
        cs.setFont(font, BODY_SIZE);
        cs.newLineAtOffset(MARGIN, y);

        StringBuilder line = new StringBuilder();
        String[] words = text.split(" ");

        for (String word : words) {
            String testLine = line.length() == 0 ? word : line + " " + word;
            float testWidth = font.getStringWidth(testLine) / 1000 * BODY_SIZE;

            if (testWidth > maxWidth) {
                cs.showText(line.toString());
                cs.newLineAtOffset(0, -LINE_HEIGHT);
                y -= LINE_HEIGHT;
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(testLine);
            }
        }

        if (line.length() > 0) {
            cs.showText(line.toString());
            y -= LINE_HEIGHT;
        }

        cs.endText();
        return y;
    }

    private static void drawFooter(PDPageContentStream cs, PDPage page, int pageNum) throws IOException {
        float y = MARGIN / 2;
        String header = "Sample Service Agreement";
        String pageText = "Page " + pageNum + " of 3";

        // Header text (left)
        cs.beginText();
        cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 9);
        cs.newLineAtOffset(MARGIN, y);
        cs.showText(header);
        cs.endText();

        // Page number (right)
        PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        float textWidth = font.getStringWidth(pageText) / 1000 * 9;
        cs.beginText();
        cs.setFont(font, 9);
        cs.newLineAtOffset(page.getMediaBox().getWidth() - MARGIN - textWidth, y);
        cs.showText(pageText);
        cs.endText();
    }
}
