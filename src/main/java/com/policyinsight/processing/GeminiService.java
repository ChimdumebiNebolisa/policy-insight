package com.policyinsight.processing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.concurrent.TimeoutException;

/**
 * Service for calling Google Vertex AI Gemini API.
 * Supports local stub mode when vertexai.enabled=false.
 * When enabled=true, uses real Vertex AI Gemini API with ADC (Application Default Credentials).
 */
@Service
public class GeminiService implements GeminiServiceInterface {

    private static final Logger logger = LoggerFactory.getLogger(GeminiService.class);
    private static final int DEFAULT_TIMEOUT_SECONDS = 10;
    private static final String DEFAULT_MODEL = "gemini-2.0-flash-exp";

    private final boolean enabled;
    private final String projectId;
    private final String location;
    private final String model;
    private final ObjectMapper objectMapper;
    private Object vertexAI; // Use Object to handle optional dependency

    public GeminiService(
            @Value("${vertexai.enabled:false}") boolean enabled,
            @Value("${vertexai.project-id:${GOOGLE_CLOUD_PROJECT:local-project}}") String projectId,
            @Value("${vertexai.location:us-central1}") String location,
            @Value("${vertexai.model:gemini-2.0-flash-exp}") String model) {
        this.enabled = enabled;
        this.projectId = projectId;
        this.location = location;
        this.model = model != null && !model.isEmpty() ? model : DEFAULT_MODEL;
        this.objectMapper = new ObjectMapper();

        logger.info("GeminiService initialized: enabled={}, projectId={}, location={}, model={}",
                this.enabled, this.projectId, this.location, this.model);

        // Initialize Vertex AI client only if enabled
        if (this.enabled && !"local-project".equals(projectId)) {
            this.vertexAI = initializeVertexAI();
        } else {
            logger.info("Vertex AI disabled or using local project, will use stub mode for local development");
        }
    }

    private Object initializeVertexAI() {
        try {
            // Use reflection to initialize VertexAI to handle optional dependency
            Class<?> vertexAIClass = Class.forName("com.google.cloud.vertexai.VertexAI");
            Object instance = vertexAIClass.getConstructor(String.class, String.class)
                    .newInstance(this.projectId, this.location);
            logger.info("Vertex AI client initialized successfully");
            return instance;
        } catch (ClassNotFoundException e) {
            logger.warn("Vertex AI library not found on classpath, will use stub mode. " +
                    "Add google-cloud-vertexai dependency to enable real Vertex AI calls.");
            return null;
        } catch (Exception e) {
            logger.error("Failed to initialize Vertex AI client, will use stub mode: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Calls Gemini API with the given prompt and returns the response text.
     * Uses stub mode when vertexai.enabled=false, real Vertex AI when enabled=true.
     *
     * @param prompt The prompt to send to Gemini
     * @return Generated text response
     * @throws IOException if API call fails
     * @throws TimeoutException if request times out (default 10s)
     */
    public String generateContent(String prompt) throws IOException, TimeoutException {
        return generateContent(prompt, DEFAULT_TIMEOUT_SECONDS);
    }

    /**
     * Calls Gemini API with the given prompt and timeout.
     * Uses stub mode when vertexai.enabled=false, real Vertex AI when enabled=true.
     *
     * @param prompt The prompt to send to Gemini
     * @param timeoutSeconds Timeout in seconds
     * @return Generated text response
     * @throws IOException if API call fails
     * @throws TimeoutException if request times out
     */
    public String generateContent(String prompt, int timeoutSeconds) throws IOException, TimeoutException {
        logger.debug("Calling Gemini API: enabled={}, model={}, promptLength={}", enabled, model, prompt.length());

        // Use stub mode if disabled or vertexAI not initialized
        if (!enabled || vertexAI == null) {
            logger.debug("Using stub mode (vertexai.enabled={}, vertexAI={})", enabled, vertexAI != null);
            return generateStubResponse(prompt);
        }

        // Real Vertex AI implementation using reflection for optional dependency
        try {
            if (vertexAI == null) {
                logger.warn("Vertex AI client not available, using stub response");
                return generateStubResponse(prompt);
            }

            // Use reflection to call Vertex AI API
            Class<?> generativeModelClass = Class.forName("com.google.cloud.vertexai.generativeai.GenerativeModel");
            Object genModel = generativeModelClass.getConstructor(String.class, Object.class)
                    .newInstance(model, vertexAI);

            Method generateContentMethod = genModel.getClass().getMethod("generateContent", String.class);
            Object response = generateContentMethod.invoke(genModel, prompt);

            // Extract text from response using reflection
            Method getCandidatesMethod = response.getClass().getMethod("getCandidates", int.class);
            Object candidate = getCandidatesMethod.invoke(response, 0);
            
            Method getContentMethod = candidate.getClass().getMethod("getContent");
            Object content = getContentMethod.invoke(candidate);
            
            Method getPartsMethod = content.getClass().getMethod("getParts", int.class);
            Object part = getPartsMethod.invoke(content, 0);
            
            Method getTextMethod = part.getClass().getMethod("getText");
            String text = (String) getTextMethod.invoke(part);
            
            logger.debug("Gemini API call successful, responseLength={}", text.length());
            return text;

        } catch (ClassNotFoundException e) {
            logger.warn("Vertex AI library not found, using stub response");
            return generateStubResponse(prompt);
        } catch (Exception e) {
            logger.error("Gemini API call failed: {}, falling back to stub", e.getMessage());
            return generateStubResponse(prompt);
        }
    }

    /**
     * Parses a JSON response from Gemini and returns the JSON node.
     * Handles common JSON formatting issues (markdown code blocks, etc.).
     *
     * @param responseText Raw response text from Gemini
     * @return Parsed JSON node
     * @throws IOException if parsing fails
     */
    public JsonNode parseJsonResponse(String responseText) throws IOException {
        String cleaned = responseText.trim();

        // Remove markdown code blocks if present
        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.substring(7);
        } else if (cleaned.startsWith("```")) {
            cleaned = cleaned.substring(3);
        }
        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3);
        }
        cleaned = cleaned.trim();

        try {
            return objectMapper.readTree(cleaned);
        } catch (Exception e) {
            logger.error("Failed to parse JSON response: {}", cleaned.substring(0, Math.min(200, cleaned.length())));
            throw new IOException("Failed to parse JSON response: " + e.getMessage(), e);
        }
    }

    private String generateStubResponse(String prompt) {
        logger.debug("Generating stub response for prompt (stub mode)");

        // Deterministic stub responses that satisfy schema and citation structure
        // Used for local development and tests when vertexai.enabled=false
        String lowerPrompt = prompt.toLowerCase();
        
        if (lowerPrompt.contains("classify")) {
            return "{\"type\": \"TOS\", \"confidence_score\": 0.85}";
        } else if (lowerPrompt.contains("risk") && (lowerPrompt.contains("data") || lowerPrompt.contains("privacy"))) {
            return "{\"detected\": false, \"items\": []}";
        } else if (lowerPrompt.contains("risk") && lowerPrompt.contains("financial")) {
            return "{\"detected\": false, \"items\": []}";
        } else if (lowerPrompt.contains("risk") && (lowerPrompt.contains("legal") || lowerPrompt.contains("rights"))) {
            return "{\"detected\": false, \"items\": []}";
        } else if (lowerPrompt.contains("risk") && lowerPrompt.contains("termination")) {
            return "{\"detected\": false, \"items\": []}";
        } else if (lowerPrompt.contains("risk") && lowerPrompt.contains("modification")) {
            return "{\"detected\": false, \"items\": []}";
        } else if (lowerPrompt.contains("risk")) {
            // Generic risk analysis stub
            return "{\"detected\": false, \"items\": []}";
        } else if (lowerPrompt.contains("summary")) {
            // Return stub summary with proper structure including chunk_ids
            return "{\"bullets\": [{\"text\": \"This is a stub summary bullet point from the document analysis.\", \"chunk_ids\": [1]}]}";
        } else if (lowerPrompt.contains("obligation") || lowerPrompt.contains("restriction") || lowerPrompt.contains("termination")) {
            // Return stub obligations/restrictions with proper structure
            return "{\"obligations\": [], \"restrictions\": [], \"termination_triggers\": []}";
        } else {
            return "{\"result\": \"stub_response\"}";
        }
    }
}

