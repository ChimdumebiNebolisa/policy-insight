package com.policyinsight.observability;

import com.policyinsight.util.NonNulls;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import javax.annotation.Nonnull;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Stub implementation when Datadog is disabled.
 * Prevents NullPointerException when TracingService is not available.
 */
@Service
@ConditionalOnProperty(name = "datadog.enabled", havingValue = "false", matchIfMissing = true)
public class TracingServiceStub implements TracingServiceInterface {

    @Override
    public <T> T trace(@Nonnull String spanName, @Nonnull Supplier<T> operation) {
        return operation.get();
    }

    @Override
    public void trace(@Nonnull String spanName, @Nonnull Runnable operation) {
        operation.run();
    }

    @Override
    @Nonnull
    public SpanBuilder spanBuilder(@Nonnull String spanName) {
        // Return a no-op span builder
        return new NoOpSpanBuilder();
    }

    @Override
    public Span getCurrentSpan() {
        return Span.getInvalid();
    }

    private static class NoOpSpanBuilder implements SpanBuilder {
        @Override
        public SpanBuilder setParent(@Nonnull io.opentelemetry.context.Context context) { return this; }
        @Override
        public SpanBuilder setNoParent() { return this; }
        @Override
        public SpanBuilder addLink(@Nonnull io.opentelemetry.api.trace.SpanContext spanContext) { return this; }
        @Override
        public SpanBuilder addLink(@Nonnull io.opentelemetry.api.trace.SpanContext spanContext, @Nonnull io.opentelemetry.api.common.Attributes attributes) { return this; }
        @Override
        public SpanBuilder setAttribute(@Nonnull String key, @Nonnull String value) { return this; }
        @Override
        public SpanBuilder setAttribute(@Nonnull String key, long value) { return this; }
        @Override
        public SpanBuilder setAttribute(@Nonnull String key, double value) { return this; }
        @Override
        public SpanBuilder setAttribute(@Nonnull String key, boolean value) { return this; }
        @Override
        public SpanBuilder setAllAttributes(@Nonnull io.opentelemetry.api.common.Attributes attributes) { return this; }
        @Override
        public SpanBuilder setSpanKind(@Nonnull io.opentelemetry.api.trace.SpanKind spanKind) { return this; }
        @Override
        public SpanBuilder setStartTimestamp(long timestamp, @Nonnull TimeUnit unit) { return this; }
        @Override
        public <T> SpanBuilder setAttribute(@Nonnull AttributeKey<T> key, @Nonnull T value) { return this; }
        @Override
        @Nonnull
        public Span startSpan() {
            // Wrap OpenTelemetry's unannotated return through NonNulls boundary
            // to satisfy JDT null analysis
            return NonNulls.nn(Span.getInvalid(), "Span.getInvalid() returned null");
        }
    }
}

