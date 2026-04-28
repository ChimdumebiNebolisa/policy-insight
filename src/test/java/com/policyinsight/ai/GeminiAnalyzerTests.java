package com.policyinsight.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.policyinsight.ai.dto.QaAnswer;
import com.policyinsight.config.GeminiProperties;
import com.policyinsight.model.DocumentChunk;
import com.policyinsight.model.PolicyJob;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class GeminiAnalyzerTests {

    @Test
    void missingApiKeyFailsCleanly() {
        GeminiAnalyzer analyzer = new GeminiAnalyzer(
                new GeminiProperties("", "gemini-1.5-flash", 30),
                new ObjectMapper(),
                (URI uri, String apiKey, String jsonBody, Duration timeout) -> "{}"
        );

        assertThatThrownBy(() -> analyzer.generateReport(List.of()))
                .isInstanceOf(AiAnalyzerException.class)
                .hasMessageContaining("GEMINI_API_KEY");
    }

    @Test
    void parsesQaResponseWithoutRealApiKey() {
        GeminiAnalyzer analyzer = new GeminiAnalyzer(
                new GeminiProperties("test-key", "gemini-1.5-flash", 30),
                new ObjectMapper(),
                (URI uri, String apiKey, String jsonBody, Duration timeout) -> """
                        {"candidates":[{"content":{"parts":[{"text":"{\\"answer\\":\\"Use the notice clause.\\",\\"chunkIds\\":[\\"%s\\"],\\"unsupported\\":false}"}]}}]}
                        """.formatted(chunkId())
        );

        QaAnswer answer = analyzer.answerQuestion(List.of(chunk()), "What should I use?");

        assertThat(answer.answer()).contains("notice clause");
        assertThat(answer.chunkIds()).containsExactly(UUID.fromString(chunkId()));
    }

    @Test
    void malformedModelOutputFailsCleanly() {
        GeminiAnalyzer analyzer = new GeminiAnalyzer(
                new GeminiProperties("test-key", "gemini-1.5-flash", 30),
                new ObjectMapper(),
                (URI uri, String apiKey, String jsonBody, Duration timeout) -> """
                        {"candidates":[{"content":{"parts":[{"text":"not-json"}]}}]}
                        """
        );

        assertThatThrownBy(() -> analyzer.answerQuestion(List.of(chunk()), "Question"))
                .isInstanceOf(AiAnalyzerException.class)
                .hasMessageContaining("malformed");
    }

    private static DocumentChunk chunk() {
        PolicyJob job = new PolicyJob("hash", Instant.now().plus(1, ChronoUnit.HOURS));
        DocumentChunk chunk = new DocumentChunk(job, 0, "The policy requires notice.");
        ReflectionTestUtils.setField(chunk, "id", UUID.fromString(chunkId()));
        return chunk;
    }

    private static String chunkId() {
        return "11111111-1111-1111-1111-111111111111";
    }
}
