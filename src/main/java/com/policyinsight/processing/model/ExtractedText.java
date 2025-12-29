package com.policyinsight.processing.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents extracted text from a document with page-level information.
 */
public class ExtractedText {
    private final List<PageText> pages;
    private final boolean fallbackUsed;
    private final double averageConfidence;

    public ExtractedText(List<PageText> pages, boolean fallbackUsed, double averageConfidence) {
        this.pages = pages != null ? new ArrayList<>(pages) : new ArrayList<>();
        this.fallbackUsed = fallbackUsed;
        this.averageConfidence = averageConfidence;
    }

    public List<PageText> getPages() {
        return pages;
    }

    public boolean isFallbackUsed() {
        return fallbackUsed;
    }

    public double getAverageConfidence() {
        return averageConfidence;
    }

    /**
     * Get full text concatenated from all pages.
     */
    public String getFullText() {
        StringBuilder sb = new StringBuilder();
        for (PageText page : pages) {
            if (sb.length() > 0) {
                sb.append("\n\n");
            }
            sb.append(page.getText());
        }
        return sb.toString();
    }

    /**
     * Represents text extracted from a single page.
     */
    public static class PageText {
        private final int pageNumber;
        private final String text;
        private final double confidence;

        public PageText(int pageNumber, String text, double confidence) {
            this.pageNumber = pageNumber;
            this.text = text != null ? text : "";
            this.confidence = confidence;
        }

        public int getPageNumber() {
            return pageNumber;
        }

        public String getText() {
            return text;
        }

        public double getConfidence() {
            return confidence;
        }
    }
}

