package com.policyinsight.processing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import com.policyinsight.processing.model.ExtractedText;
import com.policyinsight.processing.model.TextChunk;

/**
 * Service for chunking extracted text into semantic segments with citation mapping.
 */
@Service
public class TextChunkerService {

    private static final Logger logger = LoggerFactory.getLogger(TextChunkerService.class);
    private static final int MAX_CHUNK_SIZE_CHARS = 1000; // From tasks.md
    private static final int MIN_CHUNK_SIZE_CHARS = 200; // Minimum to avoid tiny chunks
    private static final Pattern PARAGRAPH_BREAK = Pattern.compile("\\n\\s*\\n");

    /**
     * Chunks extracted text into semantic segments with page/offset tracking.
     *
     * @param extractedText Extracted text with page information
     * @return List of TextChunk objects with citation information
     */
    public List<TextChunk> chunkText(ExtractedText extractedText) {
        List<TextChunk> chunks = new ArrayList<>();
        int chunkIndex = 0;

        for (ExtractedText.PageText page : extractedText.getPages()) {
            String pageText = page.getText();
            if (pageText == null || pageText.trim().isEmpty()) {
                continue;
            }

            // Split by paragraphs first for semantic boundaries
            String[] paragraphs = PARAGRAPH_BREAK.split(pageText);
            StringBuilder currentChunk = new StringBuilder();
            int currentOffset = 0;

            for (String paragraph : paragraphs) {
                paragraph = paragraph.trim();
                if (paragraph.isEmpty()) {
                    continue;
                }

                // If adding this paragraph would exceed max size, finalize current chunk
                if (currentChunk.length() > 0 && 
                    currentChunk.length() + paragraph.length() + 1 > MAX_CHUNK_SIZE_CHARS) {
                    
                    if (currentChunk.length() >= MIN_CHUNK_SIZE_CHARS) {
                        chunks.add(createChunk(chunkIndex++, currentChunk.toString(), 
                                page.getPageNumber(), currentOffset, 
                                currentOffset + currentChunk.length(), 
                                page.getConfidence()));
                        currentOffset += currentChunk.length();
                    }
                    currentChunk = new StringBuilder();
                }

                // Add paragraph to current chunk
                if (currentChunk.length() > 0) {
                    currentChunk.append("\n\n");
                }
                currentChunk.append(paragraph);

                // If chunk is large enough, finalize it
                if (currentChunk.length() >= MAX_CHUNK_SIZE_CHARS) {
                    chunks.add(createChunk(chunkIndex++, currentChunk.toString(), 
                            page.getPageNumber(), currentOffset, 
                            currentOffset + currentChunk.length(), 
                            page.getConfidence()));
                    currentOffset += currentChunk.length();
                    currentChunk = new StringBuilder();
                }
            }

            // Finalize any remaining chunk for this page
            if (currentChunk.length() >= MIN_CHUNK_SIZE_CHARS) {
                chunks.add(createChunk(chunkIndex++, currentChunk.toString(), 
                        page.getPageNumber(), currentOffset, 
                        currentOffset + currentChunk.length(), 
                        page.getConfidence()));
            }
        }

        logger.info("Chunked text into {} chunks", chunks.size());
        return chunks;
    }

    private TextChunk createChunk(int chunkIndex, String text, int pageNumber, 
                                   int startOffset, int endOffset, double confidence) {
        BigDecimal spanConfidence = BigDecimal.valueOf(confidence)
                .setScale(2, RoundingMode.HALF_UP);
        return new TextChunk(chunkIndex, text, pageNumber, startOffset, endOffset, spanConfidence);
    }
}

