package com.policyinsight.api.validation;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;

/**
 * Validates PDF files by checking magic bytes (%PDF-), page count, and text length.
 * This prevents malicious files and enforces processing limits.
 */
@Component
public class PdfValidator {

    private static final Logger logger = LoggerFactory.getLogger(PdfValidator.class);
    private static final byte[] PDF_MAGIC_BYTES = "%PDF".getBytes();
    private static final int MAGIC_BYTES_LENGTH = 4;

    private final int maxPages;
    private final int maxTextLength;

    public PdfValidator(
            @Value("${app.validation.pdf.max-pages:100}") int maxPages,
            @Value("${app.validation.pdf.max-text-length:1048576}") int maxTextLength) {
        this.maxPages = maxPages;
        this.maxTextLength = maxTextLength;
        logger.info("PdfValidator initialized: maxPages={}, maxTextLength={}", maxPages, maxTextLength);
    }

    /**
     * Validates that the file content starts with PDF magic bytes (%PDF-).
     * Reads the first 4 bytes and checks if they match the PDF signature.
     * Used at upload time (cheap validation).
     *
     * @param inputStream File input stream (will be reset after reading)
     * @return true if file has valid PDF magic bytes, false otherwise
     * @throws IOException if reading fails
     */
    public boolean validateMagicBytes(InputStream inputStream) throws IOException {
        if (inputStream == null) {
            logger.warn("PDF validation failed: input stream is null");
            return false;
        }

        // Mark the stream so we can reset it after reading
        if (!inputStream.markSupported()) {
            logger.warn("PDF validation: input stream does not support mark/reset, cannot validate magic bytes");
            return false; // Conservative: fail if we can't reset
        }

        inputStream.mark(MAGIC_BYTES_LENGTH + 1); // Mark position

        try {
            byte[] header = new byte[MAGIC_BYTES_LENGTH];
            int bytesRead = inputStream.read(header);

            if (bytesRead < MAGIC_BYTES_LENGTH) {
                logger.warn("PDF validation failed: file too short (read {} bytes, expected at least {})", bytesRead, MAGIC_BYTES_LENGTH);
                return false;
            }

            // Check if first 4 bytes match "%PDF"
            for (int i = 0; i < MAGIC_BYTES_LENGTH; i++) {
                if (header[i] != PDF_MAGIC_BYTES[i]) {
                    logger.warn("PDF validation failed: magic bytes mismatch at position {} (expected {}, got {})",
                            i, PDF_MAGIC_BYTES[i], header[i]);
                    return false;
                }
            }

            logger.debug("PDF validation passed: magic bytes verified");
            return true;

        } finally {
            // Reset stream to beginning for actual processing
            try {
                inputStream.reset();
            } catch (IOException e) {
                logger.error("Failed to reset input stream after PDF validation", e);
                throw new IOException("Failed to reset input stream after validation", e);
            }
        }
    }

    /**
     * Validates that the PDF does not exceed the maximum page count.
     * Used in worker after downloading PDF (more expensive validation).
     *
     * @param inputStream PDF input stream (will be closed after reading)
     * @param maxPages Maximum allowed pages (if null, uses configured default)
     * @throws IllegalArgumentException if page count exceeds limit
     * @throws IOException if reading fails
     */
    public void validateMaxPages(InputStream inputStream, Integer maxPages) throws IOException {
        int limit = maxPages != null ? maxPages : this.maxPages;

        if (inputStream == null) {
            throw new IllegalArgumentException("Input stream is null");
        }

        try (PDDocument document = Loader.loadPDF(inputStream.readAllBytes())) {
            int pageCount = document.getNumberOfPages();
            logger.debug("PDF page count: {} (limit: {})", pageCount, limit);

            if (pageCount > limit) {
                logger.warn("PDF validation failed: page count ({}) exceeds maximum ({}), rejecting document", pageCount, limit);
                throw new IllegalArgumentException(
                        String.format("PDF page count (%d) exceeds maximum allowed (%d pages)", pageCount, limit));
            }

            logger.debug("PDF page count validation passed: {} pages", pageCount);
        } catch (Exception e) {
            logger.error("Failed to load PDF for page count validation", e);
            throw new IOException("Failed to validate PDF page count: " + e.getMessage(), e);
        }
    }

    /**
     * Validates that the extracted text does not exceed the maximum length.
     * Used in worker after text extraction (prevents excessive processing costs).
     *
     * @param text Extracted text to validate
     * @param maxLength Maximum allowed text length in characters (if null, uses configured default)
     * @throws IllegalArgumentException if text length exceeds limit
     */
    public void validateMaxTextLength(String text, Integer maxLength) {
        int limit = maxLength != null ? maxLength : this.maxTextLength;

        if (text == null) {
            throw new IllegalArgumentException("Text is null");
        }

        int textLength = text.length();
        logger.debug("Extracted text length: {} (limit: {})", textLength, limit);

        if (textLength > limit) {
            logger.warn("Text validation failed: length ({}) exceeds maximum ({}), rejecting document", textLength, limit);
            throw new IllegalArgumentException(
                    String.format("Extracted text length (%d characters) exceeds maximum allowed (%d characters / %.1f MB)",
                            textLength, limit, limit / 1048576.0));
        }

        logger.debug("Text length validation passed: {} characters", textLength);
    }
}

