package com.policyinsight.processing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.policyinsight.shared.model.DocumentChunk;
import com.policyinsight.shared.model.QaInteraction;
import com.policyinsight.shared.repository.DocumentChunkRepository;
import com.policyinsight.shared.repository.QaInteractionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.*;
import java.util.UUID;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

/**
 * Service for grounded Q&A with cite-or-abstain enforcement.
 * Uses Gemini to answer questions based on document chunks, or abstains if no evidence exists.
 */
@Service
public class QaService {

    private static final Logger logger = LoggerFactory.getLogger(QaService.class);
    private static final int QA_TIMEOUT_SECONDS = 3; // PRD constraint: 3-second timeout per answer
    private static final int MAX_QUESTIONS_PER_SESSION = 3; // PRD constraint: up to 3 questions per document session

    private final GeminiService geminiService;
    private final DocumentChunkRepository chunkRepository;
    private final QaInteractionRepository qaInteractionRepository;
    private final ObjectMapper objectMapper;

    public QaService(
            GeminiService geminiService,
            DocumentChunkRepository chunkRepository,
            QaInteractionRepository qaInteractionRepository) {
        this.geminiService = geminiService;
        this.chunkRepository = chunkRepository;
        this.qaInteractionRepository = qaInteractionRepository;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Answer a question about a document using grounded Q&A with cite-or-abstain enforcement.
     *
     * @param jobUuid The job UUID for the document
     * @param question The user's question
     * @return QaResult containing the answer, citations, and confidence
     * @throws IllegalArgumentException if job not found, question limit exceeded, or document not ready
     */
    @Transactional
    public QaResult answerQuestion(UUID jobUuid, String question) throws IllegalArgumentException {
        logger.info("Processing Q&A request: jobUuid={}, questionLength={}", jobUuid, question.length());

        // Check question count limit (3 per session)
        long questionCount = qaInteractionRepository.countByJobUuid(jobUuid);
        if (questionCount >= MAX_QUESTIONS_PER_SESSION) {
            throw new IllegalArgumentException(
                    String.format("Maximum question limit (%d) reached for this document session", MAX_QUESTIONS_PER_SESSION));
        }

        // Retrieve document chunks
        List<DocumentChunk> chunks = chunkRepository.findByJobUuidOrderByChunkIndex(jobUuid);
        if (chunks.isEmpty()) {
            throw new IllegalArgumentException("Document chunks not found. Document may not be processed yet.");
        }

        // Build context from chunks
        String context = buildChunkContext(chunks);

        // Build grounded Q&A prompt with cite-or-abstain enforcement
        String prompt = buildGroundedQaPrompt(question, context, chunks);

        try {
            // Call Gemini with timeout
            long startTime = System.currentTimeMillis();
            String response = geminiService.generateContent(prompt, QA_TIMEOUT_SECONDS);
            long latencyMs = System.currentTimeMillis() - startTime;

            logger.debug("Gemini Q&A response received: latencyMs={}, responseLength={}", latencyMs, response.length());

            // Parse response and extract citations
            QaResult result = parseQaResponse(response, chunks, question, latencyMs);

            // Store Q&A interaction
            QaInteraction interaction = new QaInteraction(jobUuid, question, result.getAnswer());
            interaction.setConfidence(result.isGrounded() ? "CONFIDENT" : "ABSTAINED");
            interaction.setCitedChunks(buildCitedChunksJson(result.getCitations()));
            qaInteractionRepository.save(interaction);

            logger.info("Q&A interaction saved: jobUuid={}, grounded={}, confidence={}",
                    jobUuid, result.isGrounded(), interaction.getConfidence());

            return result;

        } catch (TimeoutException e) {
            logger.warn("Q&A request timed out: jobUuid={}", jobUuid);
            throw new IllegalArgumentException("Q&A request timed out. Please try again with a simpler question.");
        } catch (IOException e) {
            logger.error("Failed to call Gemini API for Q&A: jobUuid={}", jobUuid, e);
            throw new RuntimeException("Failed to process question. Please try again later.", e);
        }
    }

    /**
     * Build context string from document chunks for the prompt.
     */
    private String buildChunkContext(List<DocumentChunk> chunks) {
        StringBuilder context = new StringBuilder();
        for (DocumentChunk chunk : chunks) {
            context.append(String.format("[Chunk %d, Page %d]: %s\n\n",
                    chunk.getId(),
                    chunk.getPageNumber() != null ? chunk.getPageNumber() : 0,
                    chunk.getText() != null ? chunk.getText() : ""));
        }
        return context.toString();
    }

    /**
     * Build grounded Q&A prompt with cite-or-abstain enforcement.
     */
    private String buildGroundedQaPrompt(String question, String context, List<DocumentChunk> chunks) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are a legal document analyzer. STRICT RULE: Every claim you make MUST cite specific chunks by ID. ");
        prompt.append("If no evidence exists in the document, respond with: \"Insufficient evidence: This document does not address [question topic].\"\n\n");
        prompt.append("Do NOT:\n");
        prompt.append("- Infer intent\n");
        prompt.append("- Speculate\n");
        prompt.append("- Use phrases like \"likely\" or \"probably\"\n\n");
        prompt.append("For your answer, provide:\n");
        prompt.append("- A plain-English answer (max 300 chars)\n");
        prompt.append("- Chunk IDs that support your answer (e.g., [5, 7, 12])\n");
        prompt.append("- If no evidence exists, respond with: \"Insufficient evidence: This document does not address [question topic].\"\n\n");
        prompt.append("Format your response as JSON:\n");
        prompt.append("{\n");
        prompt.append("  \"answer\": \"your answer here\",\n");
        prompt.append("  \"chunk_ids\": [5, 7, 12],\n");
        prompt.append("  \"is_grounded\": true\n");
        prompt.append("}\n\n");
        prompt.append("Document Context:\n");
        prompt.append(context);
        prompt.append("\n\nQuestion: ").append(question).append("\n\n");
        prompt.append("Answer (JSON format only):");

        return prompt.toString();
    }

    /**
     * Parse Gemini response and extract citations.
     */
    private QaResult parseQaResponse(String response, List<DocumentChunk> chunks, String question, long latencyMs) {
        try {
            // Try to parse as JSON first
            JsonNode jsonNode = objectMapper.readTree(response);
            String answer = jsonNode.has("answer") ? jsonNode.get("answer").asText() : response;
            boolean isGrounded = jsonNode.has("is_grounded") ? jsonNode.get("is_grounded").asBoolean() : true;

            List<Long> chunkIds = new ArrayList<>();
            if (jsonNode.has("chunk_ids") && jsonNode.get("chunk_ids").isArray()) {
                for (JsonNode idNode : jsonNode.get("chunk_ids")) {
                    chunkIds.add(idNode.asLong());
                }
            }

            // Validate chunk IDs exist
            Set<Long> validChunkIds = chunks.stream()
                    .map(DocumentChunk::getId)
                    .collect(Collectors.toSet());

            List<QaResult.Citation> citations = new ArrayList<>();
            for (Long chunkId : chunkIds) {
                if (validChunkIds.contains(chunkId)) {
                    DocumentChunk chunk = chunks.stream()
                            .filter(c -> c.getId().equals(chunkId))
                            .findFirst()
                            .orElse(null);
                    if (chunk != null) {
                        citations.add(new QaResult.Citation(
                                chunkId,
                                chunk.getPageNumber() != null ? chunk.getPageNumber() : 0,
                                chunk.getText() != null && chunk.getText().length() > 100
                                        ? chunk.getText().substring(0, 100) + "..."
                                        : chunk.getText()));
                    }
                }
            }

            // Check if answer indicates abstention
            if (answer.toLowerCase().contains("insufficient evidence") ||
                answer.toLowerCase().contains("does not address") ||
                citations.isEmpty()) {
                isGrounded = false;
                if (!answer.toLowerCase().contains("insufficient evidence")) {
                    answer = "Insufficient evidence: This document does not address your question.";
                }
            }

            return new QaResult(question, answer, isGrounded, citations, latencyMs);

        } catch (Exception e) {
            logger.warn("Failed to parse Q&A response as JSON, treating as plain text: {}", e.getMessage());
            // Fallback: treat as plain text, assume abstention if no citations found
            return new QaResult(question, response, false, Collections.emptyList(), latencyMs);
        }
    }

    /**
     * Build cited chunks JSON for storage.
     */
    private Map<String, Object> buildCitedChunksJson(List<QaResult.Citation> citations) {
        Map<String, Object> citedChunks = new HashMap<>();
        List<Map<String, Object>> citationsList = new ArrayList<>();
        for (QaResult.Citation citation : citations) {
            Map<String, Object> citationMap = new HashMap<>();
            citationMap.put("chunk_id", citation.getChunkId());
            citationMap.put("page_number", citation.getPageNumber());
            citationMap.put("text_span", citation.getTextSpan());
            citationsList.add(citationMap);
        }
        citedChunks.put("citations", citationsList);
        return citedChunks;
    }

    /**
     * Get existing Q&A interactions for a document.
     */
    public List<QaInteraction> getQaInteractions(UUID jobUuid) {
        return qaInteractionRepository.findByJobUuidOrderByCreatedAtDesc(jobUuid);
    }

    /**
     * Get question count for a document session.
     */
    public long getQuestionCount(UUID jobUuid) {
        return qaInteractionRepository.countByJobUuid(jobUuid);
    }

    /**
     * Result of a Q&A operation.
     */
    public static class QaResult {
        private final String question;
        private final String answer;
        private final boolean grounded;
        private final List<Citation> citations;
        private final long latencyMs;

        public QaResult(String question, String answer, boolean grounded, List<Citation> citations, long latencyMs) {
            this.question = question;
            this.answer = answer;
            this.grounded = grounded;
            this.citations = citations;
            this.latencyMs = latencyMs;
        }

        public String getQuestion() {
            return question;
        }

        public String getAnswer() {
            return answer;
        }

        public boolean isGrounded() {
            return grounded;
        }

        public List<Citation> getCitations() {
            return citations;
        }

        public long getLatencyMs() {
            return latencyMs;
        }

        public static class Citation {
            private final Long chunkId;
            private final Integer pageNumber;
            private final String textSpan;

            public Citation(Long chunkId, Integer pageNumber, String textSpan) {
                this.chunkId = chunkId;
                this.pageNumber = pageNumber;
                this.textSpan = textSpan;
            }

            public Long getChunkId() {
                return chunkId;
            }

            public Integer getPageNumber() {
                return pageNumber;
            }

            public String getTextSpan() {
                return textSpan;
            }
        }
    }
}

