package com.policyinsight.processing;

import com.fasterxml.jackson.databind.JsonNode;
import com.policyinsight.shared.model.DocumentChunk;
import com.policyinsight.shared.model.PolicyJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeoutException;

/**
 * Service for generating report sections using Gemini:
 * - Document Overview
 * - Plain-English Summary (max 10 bullets)
 * - Obligations & Restrictions
 * - Termination Triggers
 */
@Service
public class ReportGenerationService {

    private static final Logger logger = LoggerFactory.getLogger(ReportGenerationService.class);
    private static final int MAX_SUMMARY_BULLETS = 10;

    private final GeminiService geminiService;

    @Autowired
    public ReportGenerationService(GeminiService geminiService) {
        this.geminiService = geminiService;
    }

    /**
     * Generates the document overview section.
     *
     * @param job PolicyJob with classification info
     * @param chunks Document chunks
     * @return Map with overview fields
     */
    public Map<String, Object> generateDocumentOverview(PolicyJob job, List<DocumentChunk> chunks) {
        Map<String, Object> overview = new HashMap<>();

        overview.put("document_type", job.getClassification() != null ? job.getClassification() : "UNKNOWN");
        overview.put("classification_confidence", job.getClassificationConfidence() != null
                ? job.getClassificationConfidence() : 0.0);
        overview.put("filename", job.getPdfFilename());
        overview.put("file_size_bytes", job.getFileSizeBytes());
        overview.put("total_chunks", chunks.size());
        overview.put("created_at", job.getCreatedAt() != null ? job.getCreatedAt().toString() : Instant.now().toString());

        // Try to extract parties and dates from chunks (basic extraction)
        // In a full implementation, this could use Gemini
        overview.put("parties", extractParties(chunks));
        overview.put("effective_date", extractEffectiveDate(chunks));

        return overview;
    }

    /**
     * Generates plain-English summary bullets (max 10) with citations.
     *
     * @param chunks Document chunks
     * @return Map with "bullets" array
     */
    public Map<String, Object> generateSummary(List<DocumentChunk> chunks) throws IOException, TimeoutException {
        logger.info("Generating summary bullets from {} chunks", chunks.size());

        String prompt = buildSummaryPrompt(chunks);

        try {
            String response = geminiService.generateContent(prompt, 10, "summary");
            JsonNode jsonResponse = geminiService.parseJsonResponse(response);

            Map<String, Object> summary = new HashMap<>();
            List<Map<String, Object>> bullets = new ArrayList<>();

            if (jsonResponse.has("bullets") && jsonResponse.get("bullets").isArray()) {
                Set<Long> validChunkIds = getValidChunkIds(chunks);
                int bulletCount = 0;

                for (JsonNode bulletNode : jsonResponse.get("bullets")) {
                    if (bulletCount >= MAX_SUMMARY_BULLETS) {
                        logger.warn("Summary contains more than {} bullets, truncating", MAX_SUMMARY_BULLETS);
                        break;
                    }

                    Map<String, Object> bullet = new HashMap<>();

                    if (bulletNode.has("text")) {
                        bullet.put("text", bulletNode.get("text").asText());
                    }

                    List<Long> chunkIds = new ArrayList<>();
                    if (bulletNode.has("chunk_ids") && bulletNode.get("chunk_ids").isArray()) {
                        for (JsonNode chunkIdNode : bulletNode.get("chunk_ids")) {
                            long chunkId = chunkIdNode.asLong();
                            if (validChunkIds.contains(chunkId)) {
                                chunkIds.add(chunkId);
                            }
                        }
                    }
                    bullet.put("chunk_ids", chunkIds);

                    // Get page references from chunks
                    List<Integer> pageRefs = new ArrayList<>();
                    for (Long chunkId : chunkIds) {
                        for (DocumentChunk chunk : chunks) {
                            if (chunk.getId() != null && chunk.getId().equals(chunkId)) {
                                pageRefs.add(chunk.getPageNumber());
                            }
                        }
                    }
                    bullet.put("page_refs", pageRefs.stream().distinct().sorted().toList());

                    bullets.add(bullet);
                    bulletCount++;
                }
            }

            summary.put("bullets", bullets);
            return summary;

        } catch (Exception e) {
            logger.error("Failed to generate summary: {}", e.getMessage(), e);
            // Return empty summary on error
            Map<String, Object> errorSummary = new HashMap<>();
            errorSummary.put("bullets", Collections.emptyList());
            return errorSummary;
        }
    }

    /**
     * Generates obligations, restrictions, and termination triggers sections.
     *
     * @param chunks Document chunks
     * @return Map with "obligations", "restrictions", and "termination_triggers" arrays
     */
    public Map<String, Object> generateObligationsAndRestrictions(List<DocumentChunk> chunks)
            throws IOException, TimeoutException {
        logger.info("Generating obligations and restrictions from {} chunks", chunks.size());

        String prompt = buildObligationsPrompt(chunks);

        try {
            String response = geminiService.generateContent(prompt, 10, "obligations");
            JsonNode jsonResponse = geminiService.parseJsonResponse(response);

            Map<String, Object> result = new HashMap<>();
            Set<Long> validChunkIds = getValidChunkIds(chunks);

            // Extract obligations
            result.put("obligations", extractItems(jsonResponse, "obligations", chunks, validChunkIds));

            // Extract restrictions
            result.put("restrictions", extractItems(jsonResponse, "restrictions", chunks, validChunkIds));

            // Extract termination triggers
            result.put("termination_triggers", extractItems(jsonResponse, "termination_triggers", chunks, validChunkIds));

            return result;

        } catch (Exception e) {
            logger.error("Failed to generate obligations and restrictions: {}", e.getMessage(), e);
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("obligations", Collections.emptyList());
            errorResult.put("restrictions", Collections.emptyList());
            errorResult.put("termination_triggers", Collections.emptyList());
            return errorResult;
        }
    }

    private String buildSummaryPrompt(List<DocumentChunk> chunks) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Summarize the key findings from this legal document in plain English.\n\n");
        prompt.append("Document excerpts:\n");

        for (DocumentChunk chunk : chunks) {
            prompt.append(String.format("[Chunk ID: %d, Page: %d]\n%s\n\n",
                    chunk.getId(), chunk.getPageNumber(), chunk.getText()));
        }

        prompt.append("Return a JSON response with this structure:\n");
        prompt.append("{\n");
        prompt.append("  \"bullets\": [\n");
        prompt.append("    {\n");
        prompt.append("      \"text\": \"plain English summary bullet point\",\n");
        prompt.append("      \"chunk_ids\": [list of chunk IDs that support this bullet]\n");
        prompt.append("    }\n");
        prompt.append("  ]\n");
        prompt.append("}\n\n");
        prompt.append("Generate at most ").append(MAX_SUMMARY_BULLETS).append(" bullets. ");
        prompt.append("Each bullet MUST cite at least one chunk_id. Use clear, non-technical language.");

        return prompt.toString();
    }

    private String buildObligationsPrompt(List<DocumentChunk> chunks) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Extract obligations, restrictions, and termination triggers from this legal document.\n\n");
        prompt.append("Document excerpts:\n");

        for (DocumentChunk chunk : chunks) {
            prompt.append(String.format("[Chunk ID: %d, Page: %d]\n%s\n\n",
                    chunk.getId(), chunk.getPageNumber(), chunk.getText()));
        }

        prompt.append("Return a JSON response with this structure:\n");
        prompt.append("{\n");
        prompt.append("  \"obligations\": [\n");
        prompt.append("    {\"text\": \"description\", \"severity\": \"low/medium/high\", \"chunk_ids\": [...]}\n");
        prompt.append("  ],\n");
        prompt.append("  \"restrictions\": [\n");
        prompt.append("    {\"text\": \"description\", \"severity\": \"low/medium/high\", \"chunk_ids\": [...]}\n");
        prompt.append("  ],\n");
        prompt.append("  \"termination_triggers\": [\n");
        prompt.append("    {\"text\": \"description\", \"severity\": \"low/medium/high\", \"chunk_ids\": [...]}\n");
        prompt.append("  ]\n");
        prompt.append("}\n\n");
        prompt.append("Each item MUST cite at least one chunk_id. If a category has no items, use an empty array.");

        return prompt.toString();
    }

    private List<Map<String, Object>> extractItems(JsonNode jsonResponse, String key,
                                                    List<DocumentChunk> chunks, Set<Long> validChunkIds) {
        List<Map<String, Object>> items = new ArrayList<>();

        if (jsonResponse.has(key) && jsonResponse.get(key).isArray()) {
            for (JsonNode itemNode : jsonResponse.get(key)) {
                Map<String, Object> item = new HashMap<>();

                if (itemNode.has("text")) {
                    item.put("text", itemNode.get("text").asText());
                }

                if (itemNode.has("severity")) {
                    String severity = itemNode.get("severity").asText().toLowerCase();
                    if (severity.equals("low") || severity.equals("medium") || severity.equals("high")) {
                        item.put("severity", severity);
                    } else {
                        item.put("severity", "medium");
                    }
                } else {
                    item.put("severity", "medium");
                }

                List<Long> chunkIds = new ArrayList<>();
                if (itemNode.has("chunk_ids") && itemNode.get("chunk_ids").isArray()) {
                    for (JsonNode chunkIdNode : itemNode.get("chunk_ids")) {
                        long chunkId = chunkIdNode.asLong();
                        if (validChunkIds.contains(chunkId)) {
                            chunkIds.add(chunkId);
                        }
                    }
                }
                item.put("chunk_ids", chunkIds);

                // Add page references
                List<Integer> pageRefs = new ArrayList<>();
                for (Long chunkId : chunkIds) {
                    for (DocumentChunk chunk : chunks) {
                        if (chunk.getId() != null && chunk.getId().equals(chunkId)) {
                            pageRefs.add(chunk.getPageNumber());
                        }
                    }
                }
                item.put("page_refs", pageRefs.stream().distinct().sorted().toList());

                items.add(item);
            }
        }

        return items;
    }

    private Set<Long> getValidChunkIds(List<DocumentChunk> chunks) {
        Set<Long> validIds = new HashSet<>();
        for (DocumentChunk chunk : chunks) {
            if (chunk.getId() != null) {
                validIds.add(chunk.getId());
            }
        }
        return validIds;
    }

    private List<String> extractParties(List<DocumentChunk> chunks) {
        // Basic extraction - in full implementation, could use Gemini
        List<String> parties = new ArrayList<>();
        for (DocumentChunk chunk : chunks) {
            String text = chunk.getText().toLowerCase();
            if (text.contains("party") || text.contains("company") || text.contains("user")) {
                // Very basic - would need more sophisticated extraction
                parties.add("Extracted from document");
                break;
            }
        }
        return parties.isEmpty() ? List.of("Not specified") : parties;
    }

    private String extractEffectiveDate(List<DocumentChunk> chunks) {
        // Basic extraction - in full implementation, could use Gemini
        for (DocumentChunk chunk : chunks) {
            String text = chunk.getText();
            // Look for common date patterns
            if (text.matches(".*\\d{1,2}[/-]\\d{1,2}[/-]\\d{2,4}.*")) {
                return "Extracted from document";
            }
        }
        return "Not specified";
    }
}

