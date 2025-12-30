package com.policyinsight.processing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.genai.Client;
import com.google.genai.types.HttpOptions;
import com.google.genai.types.GenerateContentResponse;
import com.policyinsight.observability.DatadogMetricsServiceInterface;
import com.policyinsight.observability.TracingServiceInterface;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
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
    private Client client;
    private final DatadogMetricsServiceInterface metricsService;
    private final TracingServiceInterface tracingService;

    // Gemini pricing (approximate, as of 2024)
    // Input: $0.0005 per 1K tokens, Output: $0.0015 per 1K tokens (for gemini-2.0-flash-exp)
    private static final double INPUT_COST_PER_1K_TOKENS = 0.0005;
    private static final double OUTPUT_COST_PER_1K_TOKENS = 0.0015;

    public GeminiService(
            @Value("${vertexai.enabled:false}") boolean enabled,
            @Value("${vertexai.project-id:${GOOGLE_CLOUD_PROJECT:local-project}}") String projectId,
            @Value("${vertexai.location:us-central1}") String location,
            @Value("${vertexai.model:gemini-2.0-flash-exp}") String model,
            @Autowired(required = false) DatadogMetricsServiceInterface metricsService,
            @Autowired(required = false) TracingServiceInterface tracingService) {
        this.enabled = enabled;
        this.projectId = projectId;
        this.location = location;
        this.model = model != null && !model.isEmpty() ? model : DEFAULT_MODEL;
        this.objectMapper = new ObjectMapper();
        this.metricsService = metricsService; // May be null if Datadog is disabled
        this.tracingService = tracingService; // May be null if Datadog is disabled

        logger.info("GeminiService initialized: enabled={}, projectId={}, location={}, model={}",
                this.enabled, this.projectId, this.location, this.model);

        // Initialize Google Gen AI SDK client only if enabled
        if (this.enabled && !"local-project".equals(projectId)) {
            this.client = initializeClient();
        } else {
            logger.info("Vertex AI disabled or using local project, will use stub mode for local development");
        }
    }

    private Client initializeClient() {
        try {
            // Build Vertex AI client using official Google Gen AI SDK pattern
            Client.Builder builder = Client.builder()
                    .project(this.projectId)
                    .location(this.location)
                    .vertexAI(true)
                    .httpOptions(HttpOptions.builder().apiVersion("v1").build());

            Client clientInstance = builder.build();
            logger.info("Google Gen AI SDK client initialized successfully with Vertex AI enabled");
            return clientInstance;
        } catch (Exception e) {
            logger.error("Failed to initialize Google Gen AI SDK client: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to initialize Google Gen AI SDK client", e);
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
     * Tracks latency and cost metrics when Datadog is enabled.
     *
     * @param prompt The prompt to send to Gemini
     * @param timeoutSeconds Timeout in seconds
     * @return Generated text response
     * @throws IOException if API call fails
     * @throws TimeoutException if request times out
     */
    public String generateContent(String prompt, int timeoutSeconds) throws IOException, TimeoutException {
        return generateContent(prompt, timeoutSeconds, "unknown");
    }

    /**
     * Calls Gemini API with the given prompt, timeout, and task type for metrics tracking.
     *
     * @param prompt The prompt to send to Gemini
     * @param timeoutSeconds Timeout in seconds
     * @param taskType Task type for metrics (e.g., "classification", "risk_analysis", "summary", "qa")
     * @return Generated text response
     * @throws IOException if API call fails
     * @throws TimeoutException if request times out
     */
    public String generateContent(String prompt, int timeoutSeconds, String taskType) throws IOException, TimeoutException {
        logger.debug("Calling Gemini API: enabled={}, model={}, promptLength={}, taskType={}",
                enabled, model, prompt.length(), taskType);

        long startTime = System.currentTimeMillis();

        // Create span for LLM call
        Span llmSpan = null;
        if (tracingService != null) {
            llmSpan = tracingService.spanBuilder("llm.call")
                    .setAttribute("stage", "llm")
                    .setAttribute("provider", "gemini")
                    .setAttribute("model", model)
                    .setAttribute("task_type", taskType)
                    .setAttribute("prompt_length", prompt.length())
                    .startSpan();
        }

        try (io.opentelemetry.context.Scope scope = llmSpan != null ? llmSpan.makeCurrent() : null) {
            // Use stub mode ONLY when disabled
            if (!enabled) {
                logger.debug("Using stub mode (vertexai.enabled=false)");
                if (llmSpan != null) {
                    llmSpan.setAttribute("stub_mode", true);
                }
                String stubResponse = generateStubResponse(prompt);
                // Record stub latency (for testing/metrics consistency)
                if (metricsService != null) {
                    long durationMs = System.currentTimeMillis() - startTime;
                    metricsService.recordLlmLatency(durationMs, model, taskType);
                    // Stub has no cost
                }
                if (llmSpan != null) {
                    llmSpan.setStatus(StatusCode.OK);
                    llmSpan.setAttribute("response_length", stubResponse.length());
                }
                return stubResponse;
            }

            // When enabled=true, client must be initialized
            if (client == null) {
                throw new IOException("Vertex AI is enabled but client initialization failed");
            }

            // Real Vertex AI implementation using Google Gen AI SDK
            try {
                // Pattern: client.models.generateContent(modelId, promptOrContent, null)
                GenerateContentResponse response = client.models.generateContent(model, prompt, null);

                // Use SDK accessor response.text()
                String responseText = response.text();

                if (responseText == null || responseText.isBlank()) {
                    throw new IOException("Gemini API returned empty or null response");
                }

                long durationMs = System.currentTimeMillis() - startTime;

                // Estimate token usage and cost
                // Note: Google Gen AI SDK may not expose usage metadata directly
                // We estimate based on prompt and response length (rough approximation: 1 token ≈ 4 chars)
                int estimatedInputTokens = prompt.length() / 4;
                int estimatedOutputTokens = responseText.length() / 4;
                double estimatedCost = (estimatedInputTokens * INPUT_COST_PER_1K_TOKENS / 1000.0) +
                                      (estimatedOutputTokens * OUTPUT_COST_PER_1K_TOKENS / 1000.0);

                // Track metrics if Datadog is enabled
                if (metricsService != null) {
                    metricsService.recordLlmLatency(durationMs, model, taskType);
                    metricsService.recordLlmCost(estimatedCost, model, taskType);
                    metricsService.recordLlmTokens(estimatedInputTokens, estimatedOutputTokens, model, taskType);
                    metricsService.recordLlmCostEstimate(estimatedCost, model, taskType);

                    logger.debug("Gemini API call metrics: duration={}ms, estimatedCost=${}, inputTokens≈{}, outputTokens≈{}",
                            durationMs, estimatedCost, estimatedInputTokens, estimatedOutputTokens);
                }

                // Set span attributes
                if (llmSpan != null) {
                    llmSpan.setStatus(StatusCode.OK);
                    llmSpan.setAttribute("duration_ms", durationMs);
                    llmSpan.setAttribute("tokens.input", estimatedInputTokens);
                    llmSpan.setAttribute("tokens.output", estimatedOutputTokens);
                    llmSpan.setAttribute("tokens.total", estimatedInputTokens + estimatedOutputTokens);
                    llmSpan.setAttribute("cost_estimate_usd", estimatedCost);
                    llmSpan.setAttribute("response_length", responseText.length());
                }

                logger.debug("Gemini API call successful, responseLength={}", responseText.length());
                return responseText;

            } catch (IOException e) {
                logger.error("Gemini API call failed: {}", e.getMessage(), e);
                // Record error latency
                if (metricsService != null) {
                    long durationMs = System.currentTimeMillis() - startTime;
                    metricsService.recordLlmLatency(durationMs, model, taskType);
                }
                if (llmSpan != null) {
                    llmSpan.setStatus(StatusCode.ERROR);
                    llmSpan.setAttribute("error", true);
                    llmSpan.setAttribute("error.message", e.getMessage());
                    llmSpan.recordException(e);
                }
                throw e;
            } catch (Exception e) {
                logger.error("Gemini API call failed: {}", e.getMessage(), e);
                // Record error latency
                if (metricsService != null) {
                    long durationMs = System.currentTimeMillis() - startTime;
                    metricsService.recordLlmLatency(durationMs, model, taskType);
                }
                if (llmSpan != null) {
                    llmSpan.setStatus(StatusCode.ERROR);
                    llmSpan.setAttribute("error", true);
                    llmSpan.setAttribute("error.message", e.getMessage());
                    llmSpan.recordException(e);
                }
                throw new IOException("Gemini API call failed: " + e.getMessage(), e);
            }
        } finally {
            if (llmSpan != null) {
                llmSpan.end();
            }
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
