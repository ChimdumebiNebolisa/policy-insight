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
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnExpression("'${app.ai.provider:mock}' == 'gemini' && '${app.gemini.api-key:}' != ''")
public class GeminiAnalyzer implements AiAnalyzer {

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
            throw new AiAnalyzerException("GEMINI_API_KEY is required when APP_AI_PROVIDER=gemini.");
        }
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
            URI uri = URI.create("https://generativelanguage.googleapis.com/v1beta/models/"
                    + properties.model() + ":generateContent");
            String response = transport.post(uri, properties.apiKey(), request, Duration.ofSeconds(properties.timeoutSeconds()));
            return objectMapper.readValue(extractText(response), type);
        } catch (JsonProcessingException ex) {
            throw new AiAnalyzerException("Gemini returned malformed model output.", ex);
        } catch (IOException ex) {
            throw new AiAnalyzerException("Gemini API request failed.", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AiAnalyzerException("Gemini API request was interrupted.", ex);
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
                Generate a JSON risk report for the policy document chunks below.
                Return only JSON matching these fields:
                documentOverview: string
                summaryBullets, obligations, restrictions, terminationTriggers, riskTaxonomy:
                arrays of objects with text:string, chunkIds:array of UUID strings, unsupported:false
                Every claim must cite one or more exact chunk IDs from the provided chunks.

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
                throw new IOException("Gemini API returned HTTP " + response.statusCode());
            }
            return response.body();
        }
    }
}
