package com.policyinsight.processing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.regex.Pattern;

/**
 * Service for classifying documents as TOS, PRIVACY_POLICY, or LEASE_AGREEMENT.
 * Uses rules-based classification first, with optional LLM fallback.
 */
@Service
public class DocumentClassifierService {

    private static final Logger logger = LoggerFactory.getLogger(DocumentClassifierService.class);
    private static final double RULES_CONFIDENCE_THRESHOLD = 0.90;
    private static final int CLASSIFICATION_TEXT_LENGTH = 2000;

    // Rules-based patterns
    private static final Pattern TOS_PATTERNS = Pattern.compile(
            "(?i)(terms\\s+of\\s+service|terms\\s+and\\s+conditions|agree\\s+to|bound\\s+by|acceptance)",
            Pattern.CASE_INSENSITIVE);
    
    private static final Pattern PRIVACY_PATTERNS = Pattern.compile(
            "(?i)(privacy\\s+policy|data\\s+collect|personal\\s+information|process\\s+data|cookie)",
            Pattern.CASE_INSENSITIVE);
    
    private static final Pattern LEASE_PATTERNS = Pattern.compile(
            "(?i)(lease\\s+agreement|rent|tenant|landlord|property|monthly\\s+rent|security\\s+deposit)",
            Pattern.CASE_INSENSITIVE);

    /**
     * Classifies a document based on extracted text.
     *
     * @param fullText Full extracted text from document
     * @return ClassificationResult with type and confidence
     */
    public ClassificationResult classify(String fullText) {
        if (fullText == null || fullText.trim().isEmpty()) {
            return new ClassificationResult("UNKNOWN", BigDecimal.valueOf(0.0));
        }

        // Use first 2000 chars for classification (per PRD)
        String classificationText = fullText.length() > CLASSIFICATION_TEXT_LENGTH 
                ? fullText.substring(0, CLASSIFICATION_TEXT_LENGTH) 
                : fullText;

        // Rules-based classification
        int tosMatches = countMatches(TOS_PATTERNS, classificationText);
        int privacyMatches = countMatches(PRIVACY_PATTERNS, classificationText);
        int leaseMatches = countMatches(LEASE_PATTERNS, classificationText);

        String classification = "UNKNOWN";
        double confidence = 0.0;

        if (tosMatches > privacyMatches && tosMatches > leaseMatches && tosMatches >= 2) {
            classification = "TOS";
            confidence = Math.min(0.95, 0.70 + (tosMatches * 0.05));
        } else if (privacyMatches > tosMatches && privacyMatches > leaseMatches && privacyMatches >= 2) {
            classification = "PRIVACY_POLICY";
            confidence = Math.min(0.95, 0.70 + (privacyMatches * 0.05));
        } else if (leaseMatches > tosMatches && leaseMatches > privacyMatches && leaseMatches >= 2) {
            classification = "LEASE_AGREEMENT";
            confidence = Math.min(0.95, 0.70 + (leaseMatches * 0.05));
        }

        BigDecimal confidenceDecimal = BigDecimal.valueOf(confidence)
                .setScale(2, RoundingMode.HALF_UP);

        logger.info("Rules-based classification: {} with confidence: {}", classification, confidenceDecimal);

        // If confidence < 0.90, we would use LLM fallback (for Milestone 5)
        // For now, we return the rules-based result
        if (confidence < RULES_CONFIDENCE_THRESHOLD) {
            logger.warn("Classification confidence {} below threshold {}, but LLM fallback not yet implemented", 
                    confidence, RULES_CONFIDENCE_THRESHOLD);
        }

        return new ClassificationResult(classification, confidenceDecimal);
    }

    private int countMatches(Pattern pattern, String text) {
        return (int) pattern.matcher(text).results().count();
    }

    /**
     * Result of document classification.
     */
    public static class ClassificationResult {
        private final String classification;
        private final BigDecimal confidence;

        public ClassificationResult(String classification, BigDecimal confidence) {
            this.classification = classification;
            this.confidence = confidence;
        }

        public String getClassification() {
            return classification;
        }

        public BigDecimal getConfidence() {
            return confidence;
        }
    }
}

