package com.policyinsight.processing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.policyinsight.shared.model.DocumentChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Validates that all claims in a report have proper citations (chunk_id + page number).
 * Enforces cite-or-abstain: claims without citations are replaced with "Not detected / Not stated".
 */
@Service
public class ReportGroundingValidator {

    private static final Logger logger = LoggerFactory.getLogger(ReportGroundingValidator.class);
    private static final String ABSTAIN_MESSAGE = "Not detected / Not stated";
    private final ObjectMapper objectMapper;

    public ReportGroundingValidator() {
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Validates the entire report structure and enforces cite-or-abstain.
     * 
     * @param reportData The report data structure (Map representation)
     * @param chunks List of stored chunks for the document
     * @return ValidationResult with validation status and any violations found
     */
    public ValidationResult validateReport(Map<String, Object> reportData, List<DocumentChunk> chunks) {
        ValidationResult result = new ValidationResult();
        Set<Long> validChunkIds = getValidChunkIds(chunks);
        Map<Long, Integer> chunkIdToPageNumber = buildChunkIdToPageNumberMap(chunks);

        // Validate summary bullets
        if (reportData.containsKey("summary_bullets")) {
            validateSummaryBullets(reportData.get("summary_bullets"), validChunkIds, chunkIdToPageNumber, result);
        }

        // Validate obligations
        if (reportData.containsKey("obligations")) {
            validateItems(reportData.get("obligations"), "obligations", validChunkIds, chunkIdToPageNumber, result);
        }

        // Validate restrictions
        if (reportData.containsKey("restrictions")) {
            validateItems(reportData.get("restrictions"), "restrictions", validChunkIds, chunkIdToPageNumber, result);
        }

        // Validate termination triggers
        if (reportData.containsKey("termination_triggers")) {
            validateItems(reportData.get("termination_triggers"), "termination_triggers", validChunkIds, chunkIdToPageNumber, result);
        }

        // Validate risk taxonomy
        if (reportData.containsKey("risk_taxonomy")) {
            validateRiskTaxonomy(reportData.get("risk_taxonomy"), validChunkIds, chunkIdToPageNumber, result);
        }

        result.setValid(result.getViolations().isEmpty());
        return result;
    }

    /**
     * Validates summary bullets section.
     */
    private void validateSummaryBullets(Object summaryData, Set<Long> validChunkIds, 
                                       Map<Long, Integer> chunkIdToPageNumber, ValidationResult result) {
        try {
            JsonNode summaryNode = objectMapper.valueToTree(summaryData);
            if (!summaryNode.has("bullets") || !summaryNode.get("bullets").isArray()) {
                return;
            }

            List<Map<String, Object>> bullets = new ArrayList<>();
            for (JsonNode bulletNode : summaryNode.get("bullets")) {
                Map<String, Object> bullet = objectMapper.convertValue(bulletNode, Map.class);
                
                List<Long> chunkIds = extractChunkIds(bulletNode);
                if (chunkIds.isEmpty() || !areChunkIdsValid(chunkIds, validChunkIds)) {
                    // Abstain: replace with "Not detected / Not stated"
                    bullet.put("text", ABSTAIN_MESSAGE);
                    bullet.put("chunk_ids", Collections.emptyList());
                    bullet.put("page_refs", Collections.emptyList());
                    result.addViolation("Summary bullet missing valid citations: " + bullet.get("text"));
                } else {
                    // Ensure page numbers are present
                    List<Integer> pageRefs = chunkIds.stream()
                            .map(chunkId -> chunkIdToPageNumber.getOrDefault(chunkId, -1))
                            .filter(page -> page > 0)
                            .distinct()
                            .sorted()
                            .collect(Collectors.toList());
                    bullet.put("page_refs", pageRefs);
                }
                bullets.add(bullet);
            }

            // Update the summary data with validated bullets
            if (summaryData instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> summaryMap = (Map<String, Object>) summaryData;
                summaryMap.put("bullets", bullets);
            }
        } catch (Exception e) {
            logger.error("Error validating summary bullets", e);
            result.addViolation("Error validating summary bullets: " + e.getMessage());
        }
    }

    /**
     * Validates items in obligations/restrictions/termination_triggers sections.
     * Updates itemsData in place if it's a List, or updates the "items" key if it's a Map.
     */
    @SuppressWarnings("unchecked")
    private void validateItems(Object itemsData, String sectionName, Set<Long> validChunkIds,
                               Map<Long, Integer> chunkIdToPageNumber, ValidationResult result) {
        try {
            List<Map<String, Object>> itemsList;
            
            // Handle different input types: List, Map with "items" key, or JsonNode
            if (itemsData instanceof List) {
                itemsList = (List<Map<String, Object>>) itemsData;
            } else {
                JsonNode itemsNode = objectMapper.valueToTree(itemsData);
                JsonNode arrayNode = itemsNode.isArray() ? itemsNode : 
                        (itemsNode.isObject() && itemsNode.has("items") ? itemsNode.get("items") : itemsNode);
                if (arrayNode == null || !arrayNode.isArray() || arrayNode.isEmpty()) {
                    // Empty list is valid (no items found)
                    return;
                }
                itemsList = objectMapper.convertValue(arrayNode, 
                        objectMapper.getTypeFactory().constructCollectionType(List.class, Map.class));
            }

            // Validate and update each item
            for (int i = 0; i < itemsList.size(); i++) {
                Map<String, Object> item = itemsList.get(i);
                Object textObj = item.get("text");
                String originalText = textObj != null ? textObj.toString() : "";
                
                // Extract chunk IDs
                Object chunkIdsObj = item.get("chunk_ids");
                List<Long> chunkIds = new ArrayList<>();
                if (chunkIdsObj instanceof List) {
                    for (Object id : (List<?>) chunkIdsObj) {
                        if (id instanceof Number) {
                            chunkIds.add(((Number) id).longValue());
                        }
                    }
                }
                
                if (chunkIds.isEmpty() || !areChunkIdsValid(chunkIds, validChunkIds)) {
                    // Abstain: replace with "Not detected / Not stated"
                    item.put("text", ABSTAIN_MESSAGE);
                    item.put("chunk_ids", Collections.emptyList());
                    item.put("page_refs", Collections.emptyList());
                    if (!item.containsKey("severity")) {
                        item.put("severity", "low");
                    }
                    result.addViolation(sectionName + " item missing valid citations: " + originalText);
                } else {
                    // Ensure page numbers are present
                    List<Integer> pageRefs = chunkIds.stream()
                            .map(chunkId -> chunkIdToPageNumber.getOrDefault(chunkId, -1))
                            .filter(page -> page > 0)
                            .distinct()
                            .sorted()
                            .collect(Collectors.toList());
                    item.put("page_refs", pageRefs);
                }
            }
        } catch (Exception e) {
            logger.error("Error validating {} section", sectionName, e);
            result.addViolation("Error validating " + sectionName + ": " + e.getMessage());
        }
    }

    /**
     * Validates risk taxonomy section (all 5 categories).
     */
    private void validateRiskTaxonomy(Object riskData, Set<Long> validChunkIds,
                                     Map<Long, Integer> chunkIdToPageNumber, ValidationResult result) {
        try {
            JsonNode riskNode = objectMapper.valueToTree(riskData);
            if (!riskNode.isObject()) {
                return;
            }

            // Validate each risk category
            String[] categories = {"Data_Privacy", "Financial", "Legal_Rights_Waivers", "Termination", "Modification"};
            for (String category : categories) {
                if (riskNode.has(category)) {
                    JsonNode categoryNode = riskNode.get(category);
                    if (categoryNode.isObject()) {
                        validateRiskCategory(categoryNode, category, validChunkIds, chunkIdToPageNumber, result);
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Error validating risk taxonomy", e);
            result.addViolation("Error validating risk taxonomy: " + e.getMessage());
        }
    }

    /**
     * Validates a single risk category.
     */
    private void validateRiskCategory(JsonNode categoryNode, String categoryName, Set<Long> validChunkIds,
                                     Map<Long, Integer> chunkIdToPageNumber, ValidationResult result) {
        try {
            Map<String, Object> categoryMap = objectMapper.convertValue(categoryNode, Map.class);
            
            if (!categoryNode.has("items") || !categoryNode.get("items").isArray()) {
                return;
            }

            List<Map<String, Object>> items = new ArrayList<>();
            for (JsonNode itemNode : categoryNode.get("items")) {
                Map<String, Object> item = objectMapper.convertValue(itemNode, Map.class);
                
                List<Long> chunkIds = extractChunkIds(itemNode);
                if (chunkIds.isEmpty() || !areChunkIdsValid(chunkIds, validChunkIds)) {
                    // Abstain: replace with "Not detected / Not stated"
                    item.put("text", ABSTAIN_MESSAGE);
                    item.put("chunk_ids", Collections.emptyList());
                    item.put("severity", "low");
                    result.addViolation("Risk category " + categoryName + " item missing valid citations: " + item.get("text"));
                } else {
                    // Ensure page numbers are present (though risk items might not have page_refs in schema)
                    // We validate chunk_ids exist, page numbers come from chunks
                }
                items.add(item);
            }
            
            categoryMap.put("items", items);
        } catch (Exception e) {
            logger.error("Error validating risk category {}", categoryName, e);
            result.addViolation("Error validating risk category " + categoryName + ": " + e.getMessage());
        }
    }

    /**
     * Extracts chunk IDs from a JSON node.
     */
    private List<Long> extractChunkIds(JsonNode node) {
        List<Long> chunkIds = new ArrayList<>();
        if (node.has("chunk_ids") && node.get("chunk_ids").isArray()) {
            for (JsonNode chunkIdNode : node.get("chunk_ids")) {
                if (chunkIdNode.isNumber()) {
                    chunkIds.add(chunkIdNode.asLong());
                }
            }
        }
        return chunkIds;
    }

    /**
     * Checks if all chunk IDs are valid (exist in stored chunks).
     */
    private boolean areChunkIdsValid(List<Long> chunkIds, Set<Long> validChunkIds) {
        return chunkIds.stream().allMatch(validChunkIds::contains);
    }

    /**
     * Gets valid chunk IDs from stored chunks.
     */
    private Set<Long> getValidChunkIds(List<DocumentChunk> chunks) {
        return chunks.stream()
                .map(DocumentChunk::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    /**
     * Builds a map from chunk ID to page number.
     */
    private Map<Long, Integer> buildChunkIdToPageNumberMap(List<DocumentChunk> chunks) {
        Map<Long, Integer> map = new HashMap<>();
        for (DocumentChunk chunk : chunks) {
            if (chunk.getId() != null && chunk.getPageNumber() != null) {
                map.put(chunk.getId(), chunk.getPageNumber());
            }
        }
        return map;
    }

    /**
     * Validation result containing status and violations.
     */
    public static class ValidationResult {
        private boolean valid = true;
        private final List<String> violations = new ArrayList<>();

        public boolean isValid() {
            return valid;
        }

        public void setValid(boolean valid) {
            this.valid = valid;
        }

        public List<String> getViolations() {
            return violations;
        }

        public void addViolation(String violation) {
            this.violations.add(violation);
        }
    }
}

