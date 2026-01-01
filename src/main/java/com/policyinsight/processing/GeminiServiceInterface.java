package com.policyinsight.processing;

import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

/**
 * Interface for Gemini service to support both real and stub implementations.
 */
public interface GeminiServiceInterface {
    String generateContent(String prompt) throws IOException, TimeoutException;
    String generateContent(String prompt, int timeoutSeconds) throws IOException, TimeoutException;
    JsonNode parseJsonResponse(String responseText) throws IOException;
}

