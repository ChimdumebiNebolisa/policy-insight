package com.policyinsight.processing;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import com.policyinsight.processing.model.ExtractedText;

/**
 * Fallback service for extracting text from PDFs using PDFBox.
 * Used when Document AI is unavailable or disabled.
 */
@Service
public class FallbackOcrService {

    private static final Logger logger = LoggerFactory.getLogger(FallbackOcrService.class);

    /**
     * Extracts text from a PDF using PDFBox (text extraction only, no OCR).
     *
     * @param pdfInputStream PDF file input stream
     * @return ExtractedText with pages and confidence scores
     * @throws IOException if extraction fails
     */
    public ExtractedText extractText(InputStream pdfInputStream) throws IOException {
        logger.info("Using fallback PDFBox text extraction");

        List<ExtractedText.PageText> pages = new ArrayList<>();

        byte[] pdfBytes = pdfInputStream.readAllBytes();
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            int totalPages = document.getNumberOfPages();

            for (int pageNum = 1; pageNum <= totalPages; pageNum++) {
                stripper.setStartPage(pageNum);
                stripper.setEndPage(pageNum);
                String pageText = stripper.getText(document);

                // Lower confidence for fallback (0.5 as per PRD)
                double confidence = 0.5;
                pages.add(new ExtractedText.PageText(pageNum, pageText, confidence));
            }

            logger.info("PDFBox extracted {} pages", totalPages);
        }

        return new ExtractedText(pages, true, 0.5);
    }
}

