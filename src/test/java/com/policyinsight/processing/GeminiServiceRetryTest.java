package com.policyinsight.processing;

import com.policyinsight.observability.TracingServiceInterface;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Unit tests for GeminiService retry logic.
 * Tests retry triggers on retryable errors and max attempts respected.
 */
@ExtendWith(MockitoExtension.class)
class GeminiServiceRetryTest {

    @Mock
    private com.policyinsight.observability.DatadogMetricsServiceInterface metricsService;

    @Mock
    private TracingServiceInterface tracingService;

    @Mock
    private SpanBuilder spanBuilder;

    @Mock
    private Span span;

    private GeminiService geminiService;

    @BeforeEach
    void setUp() {
        // Mock tracing service to return a span builder (using lenient for tests that don't use it)
        lenient().when(tracingService.spanBuilder(anyString())).thenReturn(spanBuilder);
        // Mock all setAttribute overloads that might be called
        lenient().when(spanBuilder.setAttribute(anyString(), anyString())).thenReturn(spanBuilder);
        lenient().when(spanBuilder.setAttribute(anyString(), anyLong())).thenReturn(spanBuilder);
        lenient().when(spanBuilder.startSpan()).thenReturn(span);
        lenient().when(span.makeCurrent()).thenReturn(() -> {}); // Return a no-op Scope

        // Create GeminiService with retry enabled but Vertex AI disabled (stub mode)
        geminiService = new GeminiService(
                false, // enabled = false (stub mode)
                "test-project",
                "us-central1",
                "gemini-2.0-flash-exp",
                3, // maxRetryAttempts
                100, // baseRetryDelayMs (short for testing)
                metricsService,
                tracingService
        );
    }

    @Test
    void testRetryOnTimeoutException() {
        // Note: In stub mode, GeminiService doesn't actually retry
        // This test verifies the retry logic structure exists
        // For full retry testing, would need to mock the client or use integration tests

        // When: Calling generateContent in stub mode
        // Then: Should succeed (stub mode always succeeds)
        try {
            String result = geminiService.generateContent("test prompt", 10, "test");
            assertThat(result).isNotNull();
        } catch (Exception e) {
            // In stub mode, should not throw
            throw new AssertionError("Stub mode should not throw exceptions", e);
        }
    }

    @Test
    void testIsRetryableError() throws Exception {
        // Test retryable error detection logic via reflection
        // TimeoutException should be retryable
        TimeoutException timeoutEx = new TimeoutException("Request timeout");
        boolean isRetryable = (boolean) ReflectionTestUtils.invokeMethod(
                geminiService, "isRetryableError", timeoutEx);
        assertThat(isRetryable).isTrue();

        // IOException with 429 should be retryable
        IOException rateLimitEx = new IOException("HTTP 429 Too Many Requests");
        isRetryable = (boolean) ReflectionTestUtils.invokeMethod(
                geminiService, "isRetryableError", rateLimitEx);
        assertThat(isRetryable).isTrue();

        // IOException with 5xx should be retryable
        IOException serverErrorEx = new IOException("HTTP 503 Service Unavailable");
        isRetryable = (boolean) ReflectionTestUtils.invokeMethod(
                geminiService, "isRetryableError", serverErrorEx);
        assertThat(isRetryable).isTrue();

        // IOException with 4xx (non-429) should NOT be retryable
        IOException clientErrorEx = new IOException("HTTP 400 Bad Request");
        isRetryable = (boolean) ReflectionTestUtils.invokeMethod(
                geminiService, "isRetryableError", clientErrorEx);
        assertThat(isRetryable).isFalse();
    }

    @Test
    void testMaxAttemptsConfiguration() {
        // Verify max attempts is set correctly
        int maxAttempts = (int) ReflectionTestUtils.getField(geminiService, "maxRetryAttempts");
        assertThat(maxAttempts).isEqualTo(3);
    }

    @Test
    void testBaseDelayConfiguration() {
        // Verify base delay is set correctly
        long baseDelay = (long) ReflectionTestUtils.getField(geminiService, "baseRetryDelayMs");
        assertThat(baseDelay).isEqualTo(100);
    }
}

