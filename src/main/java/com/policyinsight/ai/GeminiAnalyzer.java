package com.policyinsight.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.policyinsight.ai.dto.QaAnswer;
import com.policyinsight.ai.dto.RiskReport;
import com.policyinsight.config.GeminiProperties;
import com.policyinsight.model.DocumentChunk;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GeminiAnalyzer implements AiAnalyzer {

    public static final String SAFE_ANALYSIS_FAILURE_MESSAGE = "We extracted the document, but AI analysis failed. Check the AI configuration or try again.";

    private static final Logger log = LoggerFactory.getLogger(GeminiAnalyzer.class);

    private final GeminiProperties properties;
    private final ObjectMapper objectMapper;
    private final GeminiTransport transport;

    public GeminiAnalyzer(GeminiProperties properties, ObjectMapper objectMapper) {
        this(properties, objectMapper, new JavaNetGeminiTransport());
    }

    GeminiAnalyzer(GeminiProperties properties, ObjectMapper objectMapper, GeminiTransport transport) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.transport = transport;
    }

    @Override
    public RiskReport generateReport(List<DocumentChunk> chunks) {
        return callGemini(reportPrompt(chunks), RiskReport.class);
    }

    @Override
    public QaAnswer answerQuestion(List<DocumentChunk> chunks, String question) {
        return callGemini(qaPrompt(chunks, question), QaAnswer.class);
    }

    private <T> T callGemini(String prompt, Class<T> type) {
        if (properties.apiKey() == null || properties.apiKey().isBlank()) {
            log.warn("Gemini request blocked: model={}, apiKeyPresent=false, reason=missing GEMINI_API_KEY", properties.model());
            throw new AiAnalyzerException(SAFE_ANALYSIS_FAILURE_MESSAGE);
        }
        URI uri = URI.create("https://generativelanguage.googleapis.com/v1beta/models/"
                + properties.model() + ":generateContent");
        try {
            String request = objectMapper.writeValueAsString(Map.of(
                    "contents", List.of(Map.of(
                            "role", "user",
                            "parts", List.of(Map.of("text", prompt))
                    )),
                    "generationConfig", Map.of(
                            "responseMimeType", "application/json",
                            "temperature", 0.2
                    )
            ));
            String response = transport.post(uri, properties.apiKey(), request, Duration.ofSeconds(properties.timeoutSeconds()));
            return objectMapper.readValue(extractText(response), type);
        } catch (JsonProcessingException ex) {
            log.warn("Gemini response parsing failed: model={}, apiKeyPresent=true, reason={}", properties.model(), ex.toString());
            throw new AiAnalyzerException(SAFE_ANALYSIS_FAILURE_MESSAGE, ex);
        } catch (GeminiHttpException ex) {
            log.warn(
                    "Gemini API request failed: status={}, model={}, apiKeyPresent=true, responseSummary={}",
                    ex.statusCode(),
                    properties.model(),
                    summarize(ex.responseBody())
            );
            throw new AiAnalyzerException(SAFE_ANALYSIS_FAILURE_MESSAGE, ex);
        } catch (IOException ex) {
            log.warn(
                    "Gemini API request failed: status=unavailable, model={}, apiKeyPresent=true, timeoutSeconds={}, networkFailure={}",
                    properties.model(),
                    properties.timeoutSeconds(),
                    ex.toString()
            );
            throw new AiAnalyzerException(SAFE_ANALYSIS_FAILURE_MESSAGE, ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warn(
                    "Gemini API request interrupted: status=unavailable, model={}, apiKeyPresent=true, timeoutSeconds={}",
                    properties.model(),
                    properties.timeoutSeconds()
            );
            throw new AiAnalyzerException(SAFE_ANALYSIS_FAILURE_MESSAGE, ex);
        }
    }

    private String extractText(String responseBody) throws JsonProcessingException {
        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode text = root.path("candidates").path(0).path("content").path("parts").path(0).path("text");
        if (!text.isTextual() || text.asText().isBlank()) {
            throw new AiAnalyzerException("Gemini response did not include text output.");
        }
        return text.asText();
    }

    private String reportPrompt(List<DocumentChunk> chunks) {
        return """
                Generate a JSON risk and compliance report for the document chunks below.
                The document may be a contract, agreement, policy, employment document, or any legal/policy text.
                Return only valid JSON matching these exact fields — no markdown, no commentary:
                documentOverview: string (1–3 sentences describing the document type and purpose)
                summaryBullets: executive summary points and key questions the reader should ask
                obligations: key obligations, costs, payment terms, deadlines, and notice requirements
                restrictions: restrictions, prohibited actions, data handling, privacy, and confidentiality terms
                terminationTriggers: termination and renewal triggers, missing or unclear language, and recommended next steps
                riskTaxonomy: risks, red flags, one-sided clauses, and items requiring legal or professional review
                Each of the five arrays contains objects with:
                  text: string
                  chunkIds: array of UUID strings (must match exactly one or more chunk IDs from the provided chunks)
                  unsupported: false
                Every claim must cite at least one exact chunk ID from the provided chunks.

                Chunks:
                %s
                """.formatted(formatChunks(chunks));
    }

    private String qaPrompt(List<DocumentChunk> chunks, String question) {
        return """
                Answer the question using only the provided policy document chunks.
                Return only JSON with fields: answer:string, chunkIds:array of UUID strings, unsupported:false.
                If the chunks do not answer the question, say that the document does not state the answer and return no chunkIds.

                Question:
                %s

                Chunks:
                %s
                """.formatted(question, formatChunks(chunks));
    }

    private String formatChunks(List<DocumentChunk> chunks) {
        return chunks.stream()
                .map(chunk -> "chunk_id: " + chunk.getId() + "\ntext: " + chunk.getTextContent())
                .collect(Collectors.joining("\n\n"));
    }

    private String summarize(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return "(empty)";
        }
        String sanitized = responseBody.replaceAll("\\s+", " ").trim();
        String apiKey = properties.apiKey();
        if (apiKey != null && !apiKey.isBlank()) {
            sanitized = sanitized.replace(apiKey, "[REDACTED]");
        }
        if (sanitized.length() <= 500) {
            return sanitized;
        }
        return sanitized.substring(0, 500) + "...";
    }

    interface GeminiTransport {
        String post(URI uri, String apiKey, String jsonBody, Duration timeout) throws IOException, InterruptedException;
    }

    static final class JavaNetGeminiTransport implements GeminiTransport {
        private final HttpClient httpClient = HttpClient.newHttpClient();

        @Override
        public String post(URI uri, String apiKey, String jsonBody, Duration timeout) throws IOException, InterruptedException {
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(timeout)
                    .header("Content-Type", "application/json")
                    .header("x-goog-api-key", apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new GeminiHttpException(response.statusCode(), response.body());
            }
            return response.body();
        }
    }

    static final class GeminiHttpException extends IOException {
        private final int statusCode;
        private final String responseBody;

        GeminiHttpException(int statusCode, String responseBody) {
            super("Gemini API returned HTTP " + statusCode);
            this.statusCode = statusCode;
            this.responseBody = responseBody;
        }

        int statusCode() {
            return statusCode;
        }

        String responseBody() {
            return responseBody;
        }
    }
}
