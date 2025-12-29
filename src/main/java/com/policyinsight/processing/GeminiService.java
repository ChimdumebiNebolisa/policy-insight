package com.policyinsight.processing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

/**
 * Service for calling Google Vertex AI Gemini API.
 * Supports local stub mode when vertexai.enabled=false or credentials unavailable.
 */
@Service
public class GeminiService implements GeminiServiceInterface {

    private static final Logger logger = LoggerFactory.getLogger(GeminiService.class);
    private static final int DEFAULT_TIMEOUT_SECONDS = 10;
    private static final String DEFAULT_MODEL = "gemini-2.0-flash-exp";

    private final String projectId;
    private final String location;
    private final String model;
    private final ObjectMapper objectMapper;

    public GeminiService(
            @Value("${vertexai.project-id:${GOOGLE_CLOUD_PROJECT:local-project}}") String projectId,
            @Value("${vertexai.location:us-central1}") String location,
            @Value("${vertexai.model:gemini-2.0-flash-exp}") String model) {
        this.projectId = projectId;
        this.location = location;
        this.model = model != null && !model.isEmpty() ? model : DEFAULT_MODEL;
        this.objectMapper = new ObjectMapper();

        logger.info("GeminiService initialized: projectId={}, location={}, model={}", 
                this.projectId, this.location, this.model);
        
        // Check if we can initialize Vertex AI client
        try {
            // Try to get default credentials
            String credentialsPath = System.getenv("GOOGLE_APPLICATION_CREDENTIALS");
            if (credentialsPath == null || credentialsPath.isEmpty()) {
                logger.warn("GOOGLE_APPLICATION_CREDENTIALS not set. Will use stub implementation for local development.");
                logger.warn("To use real Vertex AI, set GOOGLE_APPLICATION_CREDENTIALS or use 'gcloud auth application-default login'");
            }
        } catch (Exception e) {
            logger.warn("Could not initialize Vertex AI client, will use stub: {}", e.getMessage());
        }
    }

    /**
     * Calls Gemini API with the given prompt and returns the response text.
     * For local development without GCP credentials, returns a stub response.
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
     *
     * @param prompt The prompt to send to Gemini
     * @param timeoutSeconds Timeout in seconds
     * @return Generated text response
     * @throws IOException if API call fails
     * @throws TimeoutException if request times out
     */
    public String generateContent(String prompt, int timeoutSeconds) throws IOException, TimeoutException {
        logger.debug("Calling Gemini API: model={}, promptLength={}", model, prompt.length());

        try {
            // TODO: Implement actual Vertex AI Gemini API call
            // For now, use stub implementation for local development
            // This allows the app to compile and run tests without GCP credentials
            
            if (isLocalMode()) {
                return generateStubResponse(prompt);
            }

            // Real Vertex AI implementation would go here:
            // VertexAI vertexAI = new VertexAI(projectId, location);
            // GenerativeModel genModel = new GenerativeModel(model, vertexAI);
            // GenerateContentResponse response = genModel.generateContent(prompt);
            // return response.getText();
            
            logger.warn("Vertex AI client not fully implemented, using stub response");
            return generateStubResponse(prompt);

        } catch (Exception e) {
            logger.error("Gemini API call failed", e);
            throw new IOException("Failed to call Gemini API: " + e.getMessage(), e);
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

    private boolean isLocalMode() {
        String creds = System.getenv("GOOGLE_APPLICATION_CREDENTIALS");
        return creds == null || creds.isEmpty() || "local-project".equals(projectId);
    }

    private String generateStubResponse(String prompt) {
        logger.debug("Generating stub response for prompt (local mode)");
        
        // Very basic stub - returns a simple JSON response
        // In real implementation, this would call Vertex AI Gemini
        if (prompt.toLowerCase().contains("classify")) {
            return "{\"type\": \"TOS\", \"confidence_score\": 0.85}";
        } else if (prompt.toLowerCase().contains("risk")) {
            return "{\"detected\": false, \"items\": []}";
        } else if (prompt.toLowerCase().contains("summary")) {
            return "{\"bullets\": [{\"text\": \"This is a stub summary bullet\", \"chunk_ids\": [1]}]}";
        } else {
            return "{\"result\": \"stub_response\"}";
        }
    }
}

