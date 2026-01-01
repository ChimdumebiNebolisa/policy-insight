package com.policyinsight.observability;

import com.policyinsight.util.NonNulls;
import com.policyinsight.util.Strings;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import javax.annotation.Nonnull;
import java.util.function.Supplier;

/**
 * Service for creating custom OpenTelemetry spans that integrate with Datadog.
 * Uses GlobalOpenTelemetry which is automatically configured by dd-java-agent.
 * Only active when datadog.enabled=true.
 */
@Service
@ConditionalOnProperty(name = "datadog.enabled", havingValue = "true", matchIfMissing = false)
public class TracingService implements TracingServiceInterface {

    private static final Logger logger = LoggerFactory.getLogger(TracingService.class);
    private final Tracer tracer;

    public TracingService() {
        // Get tracer from GlobalOpenTelemetry (configured by dd-java-agent)
        this.tracer = GlobalOpenTelemetry.getTracer("com.policyinsight", "1.0.0");
        logger.info("TracingService initialized with OpenTelemetry tracer");
    }

    /**
     * Execute a function within a custom span.
     * @param spanName Name of the span
     * @param operation Operation to execute
     * @return Result of the operation
     */
    public <T> T trace(@Nonnull String spanName, @Nonnull Supplier<T> operation) {
        Span span = tracer.spanBuilder(Strings.safe(spanName)).startSpan();
        try (Scope scope = span.makeCurrent()) {
            return operation.get();
        } catch (Exception e) {
            span.recordException(e);
            span.setAttribute("error", true);
            span.setAttribute("error.message", Strings.safe(e.getMessage()));
            throw e;
        } finally {
            span.end();
        }
    }

    /**
     * Execute a void operation within a custom span.
     * @param spanName Name of the span
     * @param operation Operation to execute
     */
    public void trace(@Nonnull String spanName, @Nonnull Runnable operation) {
        Span span = tracer.spanBuilder(Strings.safe(spanName)).startSpan();
        try (Scope scope = span.makeCurrent()) {
            operation.run();
        } catch (Exception e) {
            span.recordException(e);
            span.setAttribute("error", true);
            span.setAttribute("error.message", Strings.safe(e.getMessage()));
            throw e;
        } finally {
            span.end();
        }
    }

    /**
     * Create a span builder for more advanced span creation.
     * @param spanName Name of the span
     * @return SpanBuilder instance
     */
    @Nonnull
    public SpanBuilder spanBuilder(@Nonnull String spanName) {
        // Wrap OpenTelemetry's unannotated return through NonNulls boundary
        // to satisfy JDT null analysis while enforcing non-null contract
        return NonNulls.nn(
                tracer.spanBuilder(NonNulls.requireNonBlank(spanName, "spanName is required")),
                "Tracer.spanBuilder returned null");
    }

    /**
     * Get the current active span.
     * @return Current span or null if no active span
     */
    public Span getCurrentSpan() {
        return Span.current();
    }
}

