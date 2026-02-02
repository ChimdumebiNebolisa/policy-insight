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

    private static final String DOC_ID = "SA-2025-001";
    private static final String DOC_TITLE = "Sample Service Agreement";
    private static final float MARGIN = 72; // 1 inch
    private static final float MARGIN_TOP = 90; // Extra space for header
    private static final float LINE_HEIGHT = 12;
    private static final float TITLE_SIZE = 18;
    private static final float HEADING_SIZE = 11;
    private static final float BODY_SIZE = 9;
    private static final float SMALL_SIZE = 8;
    private static final int TOTAL_PAGES = 3;

    public static void main(String[] args) throws IOException {
        Path outputPath = Paths.get("src/main/resources/static/sample/sample.pdf");
        generateSamplePdf(outputPath);
        System.out.println("Generated: " + outputPath.toAbsolutePath());
    }

    public static void generateSamplePdf(Path outputPath) throws IOException {
        try (PDDocument document = new PDDocument()) {

            // ========== PAGE 1: Title, Parties, Recitals, Scope, Definitions ==========
            PDPage page1 = addPage(document);
            try (PDPageContentStream cs = new PDPageContentStream(document, page1)) {
                drawHeader(cs, page1);
                float y = page1.getMediaBox().getHeight() - MARGIN_TOP;

                // Title
                y = drawCenteredText(cs, "SERVICE AGREEMENT", TITLE_SIZE, y, page1);
                y -= LINE_HEIGHT * 0.8f;
                y = drawCenteredText(cs, "Document ID: " + DOC_ID, SMALL_SIZE, y, page1);
                y -= LINE_HEIGHT * 1.5f;

                // Parties block
                y = drawParagraph(cs, "This Service Agreement (the \"Agreement\") is entered into as of the Effective Date set forth below by and between the following parties:", y);
                y -= LINE_HEIGHT;

                y = drawText(cs, "PROVIDER:", BODY_SIZE, y, true);
                y = drawText(cs, "PolicyInsight, Inc., a Delaware corporation (\"Provider\" or \"Company\")", BODY_SIZE, y, false);
                y = drawText(cs, "Principal Place of Business: 100 Innovation Drive, Suite 400, Wilmington, DE 19801", SMALL_SIZE, y, false);
                y = drawText(cs, "State of Incorporation: Delaware | EIN: XX-XXXXXXX", SMALL_SIZE, y, false);
                y -= LINE_HEIGHT * 0.8f;

                y = drawText(cs, "CUSTOMER:", BODY_SIZE, y, true);
                y = drawText(cs, "Sample Customer LLC, a California limited liability company (\"Customer\" or \"Client\")", BODY_SIZE, y, false);
                y = drawText(cs, "Principal Place of Business: 500 Enterprise Boulevard, Suite 200, San Francisco, CA 94105", SMALL_SIZE, y, false);
                y -= LINE_HEIGHT * 0.8f;

                y = drawText(cs, "EFFECTIVE DATE: January 1, 2025", BODY_SIZE, y, true);
                y -= LINE_HEIGHT * 1.2f;

                // Recitals
                y = drawHeading(cs, "RECITALS", y);
                y = drawParagraph(cs, "WHEREAS, Provider has developed and operates a proprietary software-as-a-service platform that utilizes artificial intelligence and natural language processing technologies to analyze legal documents, extract key terms, identify potential risks, and generate comprehensive reports with citations to source text (the \"Platform\");", y);
                y -= LINE_HEIGHT * 0.3f;
                y = drawParagraph(cs, "WHEREAS, Customer desires to engage Provider to provide access to the Platform and related services for the purpose of analyzing contracts, policies, terms of service, and other legal documents for Customer's internal business purposes;", y);
                y -= LINE_HEIGHT * 0.3f;
                y = drawParagraph(cs, "WHEREAS, Provider is willing to provide such services subject to the terms and conditions set forth in this Agreement;", y);
                y -= LINE_HEIGHT * 0.3f;
                y = drawParagraph(cs, "NOW, THEREFORE, in consideration of the mutual covenants, promises, and agreements contained herein, and for other good and valuable consideration, the receipt and sufficiency of which are hereby acknowledged, the parties agree as follows:", y);
                y -= LINE_HEIGHT * 1.2f;

                // Article 1
                y = drawHeading(cs, "ARTICLE 1: SCOPE OF SERVICES", y);
                y = drawParagraph(cs, "1.1 Service Description. Subject to the terms and conditions of this Agreement, Provider agrees to provide Customer with access to the following software-as-a-service capabilities (collectively, the \"Services\"): (a) secure document ingestion through web upload, API integration, or email forwarding; (b) automated text extraction using optical character recognition (OCR) and machine learning; (c) intelligent document classification and categorization; (d) comprehensive risk analysis and identification of key contractual terms; (e) generation of plain-English summaries with direct citations to source text; (f) interactive question-answering functionality; and (g) exportable reports in PDF and structured data formats.", y);
                y -= LINE_HEIGHT * 0.5f;
                y = drawParagraph(cs, "1.2 Service Levels. Provider shall maintain the Platform with a target availability of ninety-nine and one-half percent (99.5%) uptime, measured on a monthly basis, excluding: (i) scheduled maintenance windows announced at least forty-eight (48) hours in advance; (ii) emergency maintenance required to address security vulnerabilities; and (iii) downtime caused by factors outside Provider's reasonable control, including force majeure events, Customer's systems, or third-party services.", y);
                y -= LINE_HEIGHT * 0.5f;
                y = drawParagraph(cs, "1.3 Support Services. Provider shall provide Customer with reasonable technical support during normal business hours (9:00 AM to 6:00 PM Eastern Time, Monday through Friday, excluding federal holidays). Provider shall use commercially reasonable efforts to respond to support requests within one (1) business day and to resolve critical issues affecting service availability within four (4) hours.", y);
                y -= LINE_HEIGHT * 0.5f;
                y = drawParagraph(cs, "1.4 Disclaimer of Legal Advice. CUSTOMER EXPRESSLY ACKNOWLEDGES AND AGREES THAT THE SERVICES ARE PROVIDED FOR INFORMATIONAL PURPOSES ONLY AND DO NOT CONSTITUTE LEGAL ADVICE. THE OUTPUT GENERATED BY THE PLATFORM IS NOT A SUBSTITUTE FOR CONSULTATION WITH QUALIFIED LEGAL COUNSEL. CUSTOMER IS SOLELY RESPONSIBLE FOR REVIEWING ALL RESULTS WITH APPROPRIATE LEGAL PROFESSIONALS BEFORE RELYING ON ANY ANALYSIS OR TAKING ANY ACTION BASED THEREON.", y);
                y -= LINE_HEIGHT * 1.2f;

                // Article 2
                y = drawHeading(cs, "ARTICLE 2: DEFINITIONS", y);
                y = drawParagraph(cs, "2.1 \"Confidential Information\" means any and all non-public information disclosed by either party to the other, whether orally, in writing, or by inspection, including but not limited to: (a) documents uploaded to the Platform; (b) analysis results and reports generated by the Services; (c) technical information regarding the Platform's architecture, algorithms, and security measures; (d) business information including pricing, customer lists, financial data, and strategic plans; and (e) any information marked as \"confidential\" or that a reasonable person would understand to be confidential given the nature of the information and circumstances of disclosure.", y);
                y -= LINE_HEIGHT * 0.5f;
                y = drawParagraph(cs, "2.2 \"Processing\" means any operation or set of operations performed on Customer Data, including but not limited to: collection, recording, organization, structuring, storage, adaptation, alteration, retrieval, consultation, use, disclosure, dissemination, alignment, combination, restriction, erasure, and destruction.", y);
                y -= LINE_HEIGHT * 0.5f;
                y = drawParagraph(cs, "2.3 \"Authorized Users\" means Customer's employees, agents, contractors, and consultants who are authorized by Customer to access and use the Services under Customer's account, subject to the terms of this Agreement.", y);

                drawFooter(cs, page1, 1);
            }

            // ========== PAGE 2: Payment, Term, Termination, Data Handling ==========
            PDPage page2 = addPage(document);
            try (PDPageContentStream cs = new PDPageContentStream(document, page2)) {
                drawHeader(cs, page2);
                float y = page2.getMediaBox().getHeight() - MARGIN_TOP;

                // Article 3
                y = drawHeading(cs, "ARTICLE 3: FEES AND PAYMENT TERMS", y);
                y = drawParagraph(cs, "3.1 Subscription Fees. Customer shall pay Provider the applicable subscription fees as set forth in the Order Form attached hereto as Exhibit A (the \"Fees\"). Unless otherwise specified, all Fees are quoted in United States Dollars and shall be paid monthly in advance on the first day of each calendar month. Customer shall provide valid payment information at signup and authorizes Provider to charge such payment method for all Fees due hereunder.", y);
                y -= LINE_HEIGHT * 0.5f;
                y = drawParagraph(cs, "3.2 Price Adjustments. Provider reserves the right to modify the Fees upon thirty (30) days' prior written notice to Customer. Any price increase shall take effect at the beginning of the next renewal term following the notice period. If Customer objects to any price increase, Customer may terminate this Agreement without penalty by providing written notice to Provider prior to the effective date of the new pricing.", y);
                y -= LINE_HEIGHT * 0.5f;
                y = drawParagraph(cs, "3.3 Late Payments. Any amounts not paid when due shall accrue interest at the rate of one and one-half percent (1.5%) per month, or the maximum rate permitted by applicable law, whichever is less, calculated from the date such payment was due until the date of actual payment. Provider reserves the right to suspend Customer's access to the Services if any payment remains outstanding for more than fifteen (15) days following written notice of non-payment.", y);
                y -= LINE_HEIGHT * 0.5f;
                y = drawParagraph(cs, "3.4 Taxes. All Fees are exclusive of, and Customer shall pay, all applicable federal, state, local, and foreign sales, use, value-added, withholding, and similar taxes (collectively, \"Taxes\"), excluding taxes based on Provider's net income. If Customer is required by law to withhold any Taxes, Customer shall gross up the payment so that Provider receives the full amount of Fees as if no withholding had occurred.", y);
                y -= LINE_HEIGHT * 1.2f;

                // Article 4
                y = drawHeading(cs, "ARTICLE 4: TERM AND RENEWAL", y);
                y = drawParagraph(cs, "4.1 Initial Term. The initial term of this Agreement shall commence on the Effective Date and continue for a period of twelve (12) months (the \"Initial Term\"), unless earlier terminated in accordance with Article 5.", y);
                y -= LINE_HEIGHT * 0.5f;
                y = drawParagraph(cs, "4.2 Renewal Terms. Upon expiration of the Initial Term, this Agreement shall automatically renew for successive periods of twelve (12) months each (each, a \"Renewal Term\" and together with the Initial Term, the \"Term\"), unless either party provides written notice of its intent not to renew at least thirty (30) days prior to the end of the then-current term.", y);
                y -= LINE_HEIGHT * 1.2f;

                // Article 5
                y = drawHeading(cs, "ARTICLE 5: TERMINATION", y);
                y = drawParagraph(cs, "5.1 Termination for Breach. Either party may terminate this Agreement upon written notice if the other party materially breaches any provision of this Agreement and fails to cure such breach within thirty (30) days after receiving written notice describing the breach in reasonable detail.", y);
                y -= LINE_HEIGHT * 0.5f;
                y = drawParagraph(cs, "5.2 Termination for Cause. Provider may terminate this Agreement immediately upon written notice if Customer: (a) violates the Acceptable Use Policy set forth in Exhibit B; (b) uses the Services for any illegal purpose or in violation of applicable law; (c) attempts to reverse engineer, decompile, or disassemble any portion of the Platform; (d) attempts to circumvent any security measures or access controls; or (e) becomes subject to bankruptcy, insolvency, or similar proceedings.", y);
                y -= LINE_HEIGHT * 0.5f;
                y = drawParagraph(cs, "5.3 Effect of Termination. Upon termination or expiration of this Agreement for any reason: (a) Customer's right to access and use the Services shall immediately cease; (b) Provider shall delete all Customer Data within thirty (30) days, unless retention is required by applicable law or regulation; (c) all accrued payment obligations shall survive and become immediately due; (d) each party shall return or destroy all Confidential Information of the other party; and (e) Articles 2, 6, 7, 8, 9, and 10 shall survive termination.", y);
                y -= LINE_HEIGHT * 1.2f;

                // Article 6
                y = drawHeading(cs, "ARTICLE 6: DATA HANDLING AND SECURITY", y);
                y = drawParagraph(cs, "6.1 Data Ownership. As between the parties, Customer retains all right, title, and interest in and to all documents, data, and other content uploaded to the Platform by or on behalf of Customer (\"Customer Data\"). Provider acquires no ownership interest in Customer Data under this Agreement.", y);
                y -= LINE_HEIGHT * 0.5f;
                y = drawParagraph(cs, "6.2 License Grant. Customer grants Provider a limited, non-exclusive, worldwide, royalty-free license to process Customer Data solely for the purpose of providing the Services and as otherwise permitted under this Agreement. This license terminates upon termination or expiration of this Agreement.", y);
                y -= LINE_HEIGHT * 0.5f;
                y = drawParagraph(cs, "6.3 Security Measures. Provider shall implement and maintain appropriate technical and organizational measures to protect Customer Data against unauthorized access, alteration, disclosure, or destruction. Such measures shall include, at a minimum: (a) encryption of data at rest using AES-256 or equivalent; (b) encryption of data in transit using TLS 1.2 or higher; (c) role-based access controls; (d) regular security assessments and penetration testing; (e) incident response procedures; and (f) employee security training.", y);
                y -= LINE_HEIGHT * 0.5f;
                y = drawParagraph(cs, "6.4 Aggregated Data. Notwithstanding the foregoing, Provider may collect, use, and disclose aggregated, anonymized, and de-identified data derived from Customer's use of the Services for purposes of service improvement, benchmarking, analytics, and research, provided that such data does not identify Customer, any Authorized User, or any individual.", y);

                drawFooter(cs, page2, 2);
            }

            // ========== PAGE 3: Liability, Confidentiality, Disputes, General, Signatures ==========
            PDPage page3 = addPage(document);
            try (PDPageContentStream cs = new PDPageContentStream(document, page3)) {
                drawHeader(cs, page3);
                float y = page3.getMediaBox().getHeight() - MARGIN_TOP;

                // Article 7
                y = drawHeading(cs, "ARTICLE 7: LIMITATION OF LIABILITY", y);
                y = drawParagraph(cs, "7.1 Limitation on Direct Damages. TO THE MAXIMUM EXTENT PERMITTED BY APPLICABLE LAW, PROVIDER'S TOTAL AGGREGATE LIABILITY TO CUSTOMER FOR ANY AND ALL CLAIMS ARISING OUT OF OR RELATING TO THIS AGREEMENT, WHETHER IN CONTRACT, TORT, OR OTHERWISE, SHALL NOT EXCEED THE TOTAL FEES ACTUALLY PAID BY CUSTOMER TO PROVIDER DURING THE TWELVE (12) MONTH PERIOD IMMEDIATELY PRECEDING THE EVENT GIVING RISE TO SUCH LIABILITY.", y);
                y -= LINE_HEIGHT * 0.5f;
                y = drawParagraph(cs, "7.2 Exclusion of Consequential Damages. IN NO EVENT SHALL EITHER PARTY BE LIABLE TO THE OTHER FOR ANY INDIRECT, INCIDENTAL, SPECIAL, CONSEQUENTIAL, EXEMPLARY, OR PUNITIVE DAMAGES, INCLUDING BUT NOT LIMITED TO DAMAGES FOR LOST PROFITS, LOST REVENUES, LOST DATA, LOSS OF USE, LOSS OF GOODWILL, BUSINESS INTERRUPTION, OR COST OF PROCUREMENT OF SUBSTITUTE SERVICES, REGARDLESS OF THE CAUSE OF ACTION OR THE THEORY OF LIABILITY, EVEN IF SUCH PARTY HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.", y);
                y -= LINE_HEIGHT * 1.0f;

                // Article 8
                y = drawHeading(cs, "ARTICLE 8: CONFIDENTIALITY", y);
                y = drawParagraph(cs, "8.1 Confidentiality Obligations. Each party agrees to: (a) hold the other party's Confidential Information in strict confidence; (b) not disclose such Confidential Information to any third party except as expressly permitted herein; (c) use such Confidential Information only for the purposes of performing its obligations or exercising its rights under this Agreement; and (d) protect such Confidential Information using at least the same degree of care it uses to protect its own confidential information of similar nature, but in no event less than reasonable care.", y);
                y -= LINE_HEIGHT * 0.5f;
                y = drawParagraph(cs, "8.2 Permitted Disclosures. A party may disclose Confidential Information: (a) to its employees, agents, contractors, and advisors who have a need to know and who are bound by confidentiality obligations at least as protective as those herein; and (b) as required by law, regulation, or court order, provided that the disclosing party gives prompt written notice to the other party (to the extent legally permitted) to allow the other party to seek a protective order.", y);
                y -= LINE_HEIGHT * 0.5f;
                y = drawParagraph(cs, "8.3 Duration. The obligations under this Article 8 shall continue for a period of three (3) years following the disclosure of the applicable Confidential Information, or for trade secrets, until such information becomes publicly available through no fault of the receiving party.", y);
                y -= LINE_HEIGHT * 1.0f;

                // Article 9
                y = drawHeading(cs, "ARTICLE 9: GOVERNING LAW AND DISPUTE RESOLUTION", y);
                y = drawParagraph(cs, "9.1 Governing Law. This Agreement shall be governed by and construed in accordance with the laws of the State of Delaware, without regard to its conflict of laws principles that would require application of the laws of another jurisdiction.", y);
                y -= LINE_HEIGHT * 0.5f;
                y = drawParagraph(cs, "9.2 Arbitration. Any dispute, claim, or controversy arising out of or relating to this Agreement, including the breach, termination, enforcement, interpretation, or validity thereof, shall be determined by binding arbitration administered by the American Arbitration Association (\"AAA\") in accordance with its Commercial Arbitration Rules. The arbitration shall be conducted in Wilmington, Delaware by a single arbitrator mutually agreed upon by the parties. The arbitrator's award shall be final and binding, and judgment thereon may be entered in any court of competent jurisdiction.", y);
                y -= LINE_HEIGHT * 0.5f;
                y = drawParagraph(cs, "9.3 WAIVER OF JURY TRIAL AND CLASS ACTION. EACH PARTY HEREBY IRREVOCABLY WAIVES ANY RIGHT IT MAY HAVE TO A JURY TRIAL AND TO PARTICIPATE IN ANY CLASS ACTION, CLASS ARBITRATION, OR OTHER REPRESENTATIVE PROCEEDING WITH RESPECT TO ANY CLAIM ARISING OUT OF OR RELATING TO THIS AGREEMENT.", y);
                y -= LINE_HEIGHT * 1.0f;

                // Article 10
                y = drawHeading(cs, "ARTICLE 10: GENERAL PROVISIONS", y);
                y = drawParagraph(cs, "10.1 Entire Agreement. This Agreement, including all exhibits and attachments hereto, constitutes the entire agreement between the parties with respect to its subject matter and supersedes all prior and contemporaneous agreements, proposals, negotiations, representations, and communications, whether oral or written.", y);
                y -= LINE_HEIGHT * 0.5f;
                y = drawParagraph(cs, "10.2 Amendment; Waiver. This Agreement may not be amended or modified except by a written instrument signed by authorized representatives of both parties. No waiver of any provision shall be effective unless in writing, and no waiver shall constitute a waiver of any other provision or a continuing waiver.", y);
                y -= LINE_HEIGHT * 0.5f;
                y = drawParagraph(cs, "10.3 Severability. If any provision of this Agreement is held to be invalid, illegal, or unenforceable, such provision shall be modified to the minimum extent necessary to make it valid and enforceable, and the remaining provisions shall continue in full force and effect.", y);
                y -= LINE_HEIGHT * 0.5f;
                y = drawParagraph(cs, "10.4 Assignment. Neither party may assign this Agreement without the prior written consent of the other party, except that either party may assign this Agreement without consent in connection with a merger, acquisition, corporate reorganization, or sale of all or substantially all of its assets.", y);
                y -= LINE_HEIGHT * 1.5f;

                // Signature block
                y = drawSignatureBlock(cs, y, page3);

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

    private static void drawHeader(PDPageContentStream cs, PDPage page) throws IOException {
        float pageWidth = page.getMediaBox().getWidth();
        float y = page.getMediaBox().getHeight() - 40;

        // Left: Document title
        cs.beginText();
        cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 9);
        cs.newLineAtOffset(MARGIN, y);
        cs.showText(DOC_TITLE);
        cs.endText();

        // Right: Document ID
        PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        String idText = "Doc ID: " + DOC_ID;
        float textWidth = font.getStringWidth(idText) / 1000 * 9;
        cs.beginText();
        cs.setFont(font, 9);
        cs.newLineAtOffset(pageWidth - MARGIN - textWidth, y);
        cs.showText(idText);
        cs.endText();

        // Horizontal line
        cs.setLineWidth(0.5f);
        cs.moveTo(MARGIN, y - 8);
        cs.lineTo(pageWidth - MARGIN, y - 8);
        cs.stroke();
    }

    private static void drawFooter(PDPageContentStream cs, PDPage page, int pageNum) throws IOException {
        float pageWidth = page.getMediaBox().getWidth();
        float y = 40;

        // Horizontal line
        cs.setLineWidth(0.5f);
        cs.moveTo(MARGIN, y + 10);
        cs.lineTo(pageWidth - MARGIN, y + 10);
        cs.stroke();

        // Left: Confidential notice
        cs.beginText();
        cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_OBLIQUE), 8);
        cs.newLineAtOffset(MARGIN, y);
        cs.showText("CONFIDENTIAL - For Authorized Use Only");
        cs.endText();

        // Right: Page number
        PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        String pageText = "Page " + pageNum + " of " + TOTAL_PAGES;
        float textWidth = font.getStringWidth(pageText) / 1000 * 9;
        cs.beginText();
        cs.setFont(font, 9);
        cs.newLineAtOffset(pageWidth - MARGIN - textWidth, y);
        cs.showText(pageText);
        cs.endText();
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

    private static float drawText(PDPageContentStream cs, String text, float fontSize, float y, boolean bold) throws IOException {
        PDType1Font font = bold
                ? new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD)
                : new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        cs.beginText();
        cs.setFont(font, fontSize);
        cs.newLineAtOffset(MARGIN, y);
        cs.showText(text);
        cs.endText();
        return y - fontSize - 2;
    }

    private static float drawHeading(PDPageContentStream cs, String text, float y) throws IOException {
        cs.beginText();
        cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), HEADING_SIZE);
        cs.newLineAtOffset(MARGIN, y);
        cs.showText(text);
        cs.endText();

        return y - HEADING_SIZE - LINE_HEIGHT * 0.3f;
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

    private static float drawSignatureBlock(PDPageContentStream cs, float y, PDPage page) throws IOException {
        float colWidth = (page.getMediaBox().getWidth() - 2 * MARGIN - 40) / 2;

        // Title
        cs.beginText();
        cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 9);
        cs.newLineAtOffset(MARGIN, y);
        cs.showText("IN WITNESS WHEREOF, the parties have executed this Agreement as of the Effective Date.");
        cs.endText();
        y -= LINE_HEIGHT * 2;

        // Provider column (left)
        float leftX = MARGIN;
        float rightX = MARGIN + colWidth + 40;

        // Provider header
        cs.beginText();
        cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 9);
        cs.newLineAtOffset(leftX, y);
        cs.showText("PROVIDER: PolicyInsight, Inc.");
        cs.endText();

        // Customer header
        cs.beginText();
        cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 9);
        cs.newLineAtOffset(rightX, y);
        cs.showText("CUSTOMER: Sample Customer LLC");
        cs.endText();
        y -= LINE_HEIGHT * 2;

        // Signature lines
        PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

        // Provider signature line
        cs.setLineWidth(0.5f);
        cs.moveTo(leftX, y);
        cs.lineTo(leftX + colWidth - 20, y);
        cs.stroke();

        cs.moveTo(rightX, y);
        cs.lineTo(rightX + colWidth - 20, y);
        cs.stroke();
        y -= LINE_HEIGHT * 0.8f;

        cs.beginText();
        cs.setFont(font, 8);
        cs.newLineAtOffset(leftX, y);
        cs.showText("Signature");
        cs.endText();

        cs.beginText();
        cs.setFont(font, 8);
        cs.newLineAtOffset(rightX, y);
        cs.showText("Signature");
        cs.endText();
        y -= LINE_HEIGHT * 1.5f;

        // Name lines
        cs.moveTo(leftX, y);
        cs.lineTo(leftX + colWidth - 20, y);
        cs.stroke();

        cs.moveTo(rightX, y);
        cs.lineTo(rightX + colWidth - 20, y);
        cs.stroke();
        y -= LINE_HEIGHT * 0.8f;

        cs.beginText();
        cs.setFont(font, 8);
        cs.newLineAtOffset(leftX, y);
        cs.showText("Printed Name");
        cs.endText();

        cs.beginText();
        cs.setFont(font, 8);
        cs.newLineAtOffset(rightX, y);
        cs.showText("Printed Name");
        cs.endText();
        y -= LINE_HEIGHT * 1.5f;

        // Title lines
        cs.moveTo(leftX, y);
        cs.lineTo(leftX + colWidth - 20, y);
        cs.stroke();

        cs.moveTo(rightX, y);
        cs.lineTo(rightX + colWidth - 20, y);
        cs.stroke();
        y -= LINE_HEIGHT * 0.8f;

        cs.beginText();
        cs.setFont(font, 8);
        cs.newLineAtOffset(leftX, y);
        cs.showText("Title");
        cs.endText();

        cs.beginText();
        cs.setFont(font, 8);
        cs.newLineAtOffset(rightX, y);
        cs.showText("Title");
        cs.endText();
        y -= LINE_HEIGHT * 1.5f;

        // Date lines
        cs.moveTo(leftX, y);
        cs.lineTo(leftX + colWidth - 20, y);
        cs.stroke();

        cs.moveTo(rightX, y);
        cs.lineTo(rightX + colWidth - 20, y);
        cs.stroke();
        y -= LINE_HEIGHT * 0.8f;

        cs.beginText();
        cs.setFont(font, 8);
        cs.newLineAtOffset(leftX, y);
        cs.showText("Date");
        cs.endText();

        cs.beginText();
        cs.setFont(font, 8);
        cs.newLineAtOffset(rightX, y);
        cs.showText("Date");
        cs.endText();

        return y - LINE_HEIGHT;
    }
}
