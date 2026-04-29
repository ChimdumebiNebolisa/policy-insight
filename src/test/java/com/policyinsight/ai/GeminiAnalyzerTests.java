package com.policyinsight.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.policyinsight.ai.dto.QaAnswer;
import com.policyinsight.config.GeminiProperties;
import com.policyinsight.model.DocumentChunk;
import com.policyinsight.model.PolicyJob;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.test.util.ReflectionTestUtils;

class GeminiAnalyzerTests {

    @Test
    void missingApiKeyFailsCleanly() {
        GeminiAnalyzer analyzer = new GeminiAnalyzer(
                new GeminiProperties("", "gemini-2.5-flash", 30),
                new ObjectMapper(),
                (URI uri, String apiKey, String jsonBody, Duration timeout) -> "{}"
        );

        assertThatThrownBy(() -> analyzer.generateReport(List.of()))
                .isInstanceOf(AiAnalyzerException.class)
                .hasMessageContaining(GeminiAnalyzer.SAFE_ANALYSIS_FAILURE_MESSAGE);
    }

    @Test
    void parsesQaResponseWithoutRealApiKey() {
        GeminiAnalyzer analyzer = new GeminiAnalyzer(
                new GeminiProperties("test-key", "gemini-2.5-flash", 30),
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
                new GeminiProperties("test-key", "gemini-2.5-flash", 30),
                new ObjectMapper(),
                (URI uri, String apiKey, String jsonBody, Duration timeout) -> """
                        {"candidates":[{"content":{"parts":[{"text":"not-json"}]}}]}
                        """
        );

        assertThatThrownBy(() -> analyzer.answerQuestion(List.of(chunk()), "Question"))
                .isInstanceOf(AiAnalyzerException.class)
                .hasMessageContaining(GeminiAnalyzer.SAFE_ANALYSIS_FAILURE_MESSAGE);
    }

    @Test
    void sendsExpectedEndpointModelAndJsonModeRequest() {
        CapturingTransport transport = new CapturingTransport("""
                {"candidates":[{"content":{"parts":[{"text":"{\\"answer\\":\\"Use the notice clause.\\",\\"chunkIds\\":[\\"%s\\"],\\"unsupported\\":false}"}]}}]}
                """.formatted(chunkId()));
        GeminiAnalyzer analyzer = new GeminiAnalyzer(
                new GeminiProperties("test-key", "gemini-2.5-flash", 30),
                new ObjectMapper(),
                transport
        );

        analyzer.answerQuestion(List.of(chunk()), "What should I use?");

        assertThat(transport.uri.toString())
                .isEqualTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent");
        assertThat(transport.timeout).isEqualTo(Duration.ofSeconds(30));
        assertThat(transport.apiKey).isEqualTo("test-key");
        assertThat(transport.jsonBody)
                .contains("\"contents\"")
                .contains("\"generationConfig\"")
                .contains("\"responseMimeType\":\"application/json\"")
                .contains("\"temperature\":0.2");
    }

    @Test
    void httpErrorLogsSafeDiagnosticsWithoutApiKey() {
        String secret = "test-secret-key";
        Logger logger = (Logger) LoggerFactory.getLogger(GeminiAnalyzer.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        GeminiAnalyzer analyzer = new GeminiAnalyzer(
                new GeminiProperties(secret, "gemini-2.5-flash", 30),
                new ObjectMapper(),
                (URI uri, String apiKey, String jsonBody, Duration timeout) -> {
                    throw new GeminiAnalyzer.GeminiHttpException(400, "{\"error\":\"bad request " + secret + "\"}");
                }
        );

        assertThatThrownBy(() -> analyzer.answerQuestion(List.of(chunk()), "Question"))
                .isInstanceOf(AiAnalyzerException.class)
                .hasMessageContaining(GeminiAnalyzer.SAFE_ANALYSIS_FAILURE_MESSAGE)
                .hasMessageNotContaining(secret);

        logger.detachAppender(appender);
        assertThat(appender.list).isNotEmpty();
        String logged = appender.list.getLast().getFormattedMessage();
        assertThat(logged)
                .contains("status=400")
                .contains("model=gemini-2.5-flash")
                .contains("apiKeyPresent=true")
                .contains("[REDACTED]")
                .doesNotContain(secret);
    }

    @Test
    void networkFailureLogsTimeoutAndSafeMessage() {
        Logger logger = (Logger) LoggerFactory.getLogger(GeminiAnalyzer.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        GeminiAnalyzer analyzer = new GeminiAnalyzer(
                new GeminiProperties("test-key", "gemini-2.5-flash", 45),
                new ObjectMapper(),
                (URI uri, String apiKey, String jsonBody, Duration timeout) -> {
                    throw new IOException("timed out");
                }
        );

        assertThatThrownBy(() -> analyzer.answerQuestion(List.of(chunk()), "Question"))
                .isInstanceOf(AiAnalyzerException.class)
                .hasMessageContaining(GeminiAnalyzer.SAFE_ANALYSIS_FAILURE_MESSAGE);

        logger.detachAppender(appender);
        String logged = appender.list.getLast().getFormattedMessage();
        assertThat(logged)
                .contains("status=unavailable")
                .contains("timeoutSeconds=45")
                .contains("networkFailure=");
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

    private static final class CapturingTransport implements GeminiAnalyzer.GeminiTransport {
        private final String response;
        private URI uri;
        private String apiKey;
        private String jsonBody;
        private Duration timeout;

        private CapturingTransport(String response) {
            this.response = response;
        }

        @Override
        public String post(URI uri, String apiKey, String jsonBody, Duration timeout) {
            this.uri = uri;
            this.apiKey = apiKey;
            this.jsonBody = jsonBody;
            this.timeout = timeout;
            return response;
        }
    }
}
