package com.policyinsight.processing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;

import com.policyinsight.processing.model.ExtractedText;

/**
 * Service for extracting text from PDFs using Google Cloud Document AI.
 * Only loads when documentai.enabled=true.
 *
 * NOTE: This is a stub implementation for local development.
 * In production with Document AI enabled, replace this with the actual Document AI client implementation.
 * The real implementation would use:
 * - com.google.cloud.documentai.v1.DocumentProcessorServiceClient
 * - com.google.cloud.documentai.v1.ProcessRequest
 * - com.google.cloud.documentai.v1.ProcessResponse
 */
@Service
@ConditionalOnProperty(prefix = "documentai", name = "enabled", havingValue = "true", matchIfMissing = false)
public class DocumentAiService {

    private static final Logger logger = LoggerFactory.getLogger(DocumentAiService.class);

    private final String processorId;

    public DocumentAiService(
            @Value("${documentai.processor-id:}") String processorId) {
        this.processorId = processorId;
    }

    /**
     * Extracts text from a PDF using Document AI.
     *
     * NOTE: This is a stub that throws an exception.
     * In production, implement the actual Document AI client calls here.
     *
     * @param pdfInputStream PDF file input stream
     * @param mimeType MIME type (should be "application/pdf")
     * @return ExtractedText with pages and confidence scores
     * @throws IOException if extraction fails after retries
     */
    public ExtractedText extractText(InputStream pdfInputStream, String mimeType) throws IOException {
        if (processorId == null || processorId.isEmpty()) {
            throw new IllegalStateException("Document AI processor ID not configured");
        }

        // Stub implementation - in production, this would call Document AI API
        // For now, throw an exception so fallback is used
        logger.warn("Document AI service is enabled but stub implementation is being used. " +
                "Falling back to PDFBox extraction. To use real Document AI, implement the client calls.");
        throw new IOException("Document AI stub implementation - use fallback OCR service");
    }
}
