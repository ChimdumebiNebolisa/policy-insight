package com.policyinsight.service;

import java.io.IOException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;

@Service
public class PdfTextExtractor {

    public String extractText(byte[] pdfBytes) {
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            String text = new PDFTextStripper().getText(document).trim();
            if (text.isBlank()) {
                throw new BadUploadException("No extractable text was found in the PDF.");
            }
            return text;
        } catch (BadUploadException ex) {
            throw ex;
        } catch (IOException ex) {
            throw new BadUploadException("Unable to extract text from the PDF.");
        }
    }
}
