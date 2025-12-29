package com.policyinsight.processing.model;

import java.math.BigDecimal;

/**
 * Represents a text chunk with citation information.
 */
public class TextChunk {
    private final int chunkIndex;
    private final String text;
    private final int pageNumber;
    private final int startOffset;
    private final int endOffset;
    private final BigDecimal spanConfidence;

    public TextChunk(int chunkIndex, String text, int pageNumber, int startOffset, int endOffset, BigDecimal spanConfidence) {
        this.chunkIndex = chunkIndex;
        this.text = text;
        this.pageNumber = pageNumber;
        this.startOffset = startOffset;
        this.endOffset = endOffset;
        this.spanConfidence = spanConfidence;
    }

    public int getChunkIndex() {
        return chunkIndex;
    }

    public String getText() {
        return text;
    }

    public int getPageNumber() {
        return pageNumber;
    }

    public int getStartOffset() {
        return startOffset;
    }

    public int getEndOffset() {
        return endOffset;
    }

    public BigDecimal getSpanConfidence() {
        return spanConfidence;
    }
}

