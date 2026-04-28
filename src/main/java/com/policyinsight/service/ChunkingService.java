package com.policyinsight.service;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ChunkingService {

    private static final int MAX_CHARS = 1800;

    public List<String> chunk(String text) {
        String normalized = text.replace("\r\n", "\n").replaceAll("[ \\t]+", " ").trim();
        if (normalized.isBlank()) {
            return List.of();
        }

        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < normalized.length()) {
            int end = Math.min(start + MAX_CHARS, normalized.length());
            if (end < normalized.length()) {
                int paragraphBreak = normalized.lastIndexOf("\n\n", end);
                int sentenceBreak = normalized.lastIndexOf(". ", end);
                int splitAt = Math.max(paragraphBreak, sentenceBreak);
                if (splitAt > start + (MAX_CHARS / 2)) {
                    end = splitAt + 1;
                }
            }
            chunks.add(normalized.substring(start, end).trim());
            start = end;
        }
        return chunks;
    }
}
