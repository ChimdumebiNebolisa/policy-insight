package com.policyinsight.processing;

import com.fasterxml.jackson.databind.JsonNode;
import com.policyinsight.shared.model.DocumentChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeoutException;

/**
 * Service for analyzing risks in documents using the 5-category risk taxonomy.
 * Categories: Data/Privacy, Financial, Legal Rights Waivers, Termination, Modification
 */
@Service
public class RiskAnalysisService {

    private static final Logger logger = LoggerFactory.getLogger(RiskAnalysisService.class);

    private final GeminiService geminiService;

    // Risk categories as defined in PRD
    public enum RiskCategory {
        DATA_PRIVACY("Data_Privacy", "Data/Privacy risks including data collection, sharing, retention policies"),
        FINANCIAL("Financial", "Financial risks including fees, penalties, payment obligations"),
        LEGAL_RIGHTS_WAIVERS("Legal_Rights_Waivers", "Waivers of legal rights, arbitration clauses, class action waivers"),
        TERMINATION("Termination", "Termination conditions, cancellation fees, auto-renewal clauses"),
        MODIFICATION("Modification", "Rights to modify terms, unilateral changes, notice requirements");

        private final String key;
        private final String description;

        RiskCategory(String key, String description) {
            this.key = key;
            this.description = description;
        }

        public String getKey() {
            return key;
        }

        public String getDescription() {
            return description;
        }
    }

    @Autowired
    public RiskAnalysisService(GeminiService geminiService) {
        this.geminiService = geminiService;
    }

    /**
     * Analyzes risks across all 5 categories for the given document chunks.
     *
     * @param chunks List of document chunks to analyze
     * @return Map with risk category keys and their analysis results
     */
    public Map<String, Object> analyzeRisks(List<DocumentChunk> chunks) throws IOException, TimeoutException {
        logger.info("Starting risk analysis for {} chunks", chunks.size());

        Map<String, Object> riskTaxonomy = new LinkedHashMap<>();

        // Analyze each risk category
        for (RiskCategory category : RiskCategory.values()) {
            logger.debug("Analyzing risk category: {}", category.getKey());
            Map<String, Object> categoryResult = analyzeCategory(chunks, category);
            riskTaxonomy.put(category.getKey(), categoryResult);
        }

        logger.info("Risk analysis completed");
        return riskTaxonomy;
    }

    /**
     * Analyzes a specific risk category.
     *
     * @param chunks Document chunks to analyze
     * @param category Risk category to analyze
     * @return Result map with "detected" boolean and "items" array
     */
    private Map<String, Object> analyzeCategory(List<DocumentChunk> chunks, RiskCategory category) 
            throws IOException, TimeoutException {
        
        // Build prompt for Gemini
        String prompt = buildRiskAnalysisPrompt(chunks, category);

        try {
            String response = geminiService.generateContent(prompt, 10);
            JsonNode jsonResponse = geminiService.parseJsonResponse(response);

            // Validate and extract results
            Map<String, Object> result = new HashMap<>();
            
            if (jsonResponse.has("detected") && jsonResponse.get("detected").asBoolean()) {
                result.put("detected", true);
                
                List<Map<String, Object>> items = new ArrayList<>();
                if (jsonResponse.has("items") && jsonResponse.get("items").isArray()) {
                    for (JsonNode item : jsonResponse.get("items")) {
                        Map<String, Object> riskItem = new HashMap<>();
                        
                        if (item.has("text")) {
                            riskItem.put("text", item.get("text").asText());
                        }
                        
                        if (item.has("severity")) {
                            String severity = item.get("severity").asText().toLowerCase();
                            // Validate severity
                            if (severity.equals("low") || severity.equals("medium") || severity.equals("high")) {
                                riskItem.put("severity", severity);
                            } else {
                                riskItem.put("severity", "medium"); // Default
                            }
                        } else {
                            riskItem.put("severity", "medium"); // Default
                        }
                        
                        // Extract chunk IDs and validate they exist
                        List<Long> chunkIds = new ArrayList<>();
                        if (item.has("chunk_ids") && item.get("chunk_ids").isArray()) {
                            Set<Long> validChunkIds = getValidChunkIds(chunks);
                            for (JsonNode chunkIdNode : item.get("chunk_ids")) {
                                long chunkId = chunkIdNode.asLong();
                                if (validChunkIds.contains(chunkId)) {
                                    chunkIds.add(chunkId);
                                } else {
                                    logger.warn("Invalid chunk_id {} in risk analysis response, ignoring", chunkId);
                                }
                            }
                        }
                        riskItem.put("chunk_ids", chunkIds);
                        
                        items.add(riskItem);
                    }
                }
                result.put("items", items);
            } else {
                // Not detected
                result.put("detected", false);
                result.put("message", "Not detected in this document.");
                result.put("items", Collections.emptyList());
            }

            return result;

        } catch (Exception e) {
            logger.error("Failed to analyze risk category {}: {}", category.getKey(), e.getMessage(), e);
            // Return "not detected" on error to be safe
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("detected", false);
            errorResult.put("message", "Analysis failed: " + e.getMessage());
            errorResult.put("items", Collections.emptyList());
            return errorResult;
        }
    }

    /**
     * Builds the prompt for Gemini risk analysis.
     */
    private String buildRiskAnalysisPrompt(List<DocumentChunk> chunks, RiskCategory category) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are analyzing a legal document for risks. Scan all provided document excerpts and identify ");
        prompt.append(category.getDescription());
        prompt.append(".\n\n");
        prompt.append("Document excerpts:\n");
        
        for (DocumentChunk chunk : chunks) {
            prompt.append(String.format("[Chunk ID: %d, Page: %d]\n%s\n\n", 
                    chunk.getId(), chunk.getPageNumber(), chunk.getText()));
        }
        
        prompt.append("Return a JSON response with this structure:\n");
        prompt.append("{\n");
        prompt.append("  \"detected\": true/false,\n");
        prompt.append("  \"items\": [\n");
        prompt.append("    {\n");
        prompt.append("      \"text\": \"description of the risk\",\n");
        prompt.append("      \"severity\": \"low\" or \"medium\" or \"high\",\n");
        prompt.append("      \"chunk_ids\": [list of chunk IDs that support this risk]\n");
        prompt.append("    }\n");
        prompt.append("  ]\n");
        prompt.append("}\n\n");
        prompt.append("If no risks are found in this category, return: {\"detected\": false, \"items\": []}\n");
        prompt.append("Every risk item MUST cite at least one chunk_id. Do not include risks without citations.");

        return prompt.toString();
    }

    /**
     * Gets valid chunk IDs from the chunks list.
     */
    private Set<Long> getValidChunkIds(List<DocumentChunk> chunks) {
        Set<Long> validIds = new HashSet<>();
        for (DocumentChunk chunk : chunks) {
            if (chunk.getId() != null) {
                validIds.add(chunk.getId());
            }
        }
        return validIds;
    }
}

