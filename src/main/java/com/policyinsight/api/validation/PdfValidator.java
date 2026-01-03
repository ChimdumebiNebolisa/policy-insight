package com.policyinsight.api.validation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;

/**
 * Validates PDF files by checking magic bytes (%PDF-) in addition to MIME type.
 * This prevents malicious files from being processed even if they have a PDF MIME type.
 */
@Component
public class PdfValidator {

    private static final Logger logger = LoggerFactory.getLogger(PdfValidator.class);
    private static final byte[] PDF_MAGIC_BYTES = "%PDF".getBytes();
    private static final int MAGIC_BYTES_LENGTH = 4;

    /**
     * Validates that the file content starts with PDF magic bytes (%PDF-).
     * Reads the first 4 bytes and checks if they match the PDF signature.
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
}

