package com.policyinsight;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.font.PDType1Font;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Test utility for generating valid minimal PDF documents using Apache PDFBox.
 * Used in integration tests to create deterministic, valid PDF fixtures.
 */
public class TestPdfFactory {

    /**
     * Creates a minimal valid PDF document containing the specified text.
     * The PDF will have a single page with the text written using Helvetica font.
     *
     * @param text the text content to include in the PDF
     * @return byte array containing the PDF document
     * @throws IOException if PDF generation fails
     */
    public static byte[] minimalPdfBytes(String text) throws IOException {
        PDDocument document = null;
        try {
            document = new PDDocument();
            PDPage page = new PDPage();
            document.addPage(page);

            PDPageContentStream contentStream = null;
            try {
                contentStream = new PDPageContentStream(document, page);
                contentStream.beginText();
                // Use Standard14Fonts for PDFBox 3.0.1
                PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
                contentStream.setFont(font, 12);
                contentStream.newLineAtOffset(50, 750);
                contentStream.showText(text != null ? text : "");
                contentStream.endText();
            } finally {
                if (contentStream != null) {
                    contentStream.close();
                }
            }

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            document.save(outputStream);
            return outputStream.toByteArray();
        } finally {
            if (document != null) {
                document.close();
            }
        }
    }
}

