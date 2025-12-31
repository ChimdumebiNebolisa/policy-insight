package com.policyinsight.observability;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

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
    public <T> T trace(String spanName, Supplier<T> operation) {
        return operation.get();
    }

    @Override
    public void trace(String spanName, Runnable operation) {
        operation.run();
    }

    @Override
    public SpanBuilder spanBuilder(String spanName) {
        // Return a no-op span builder
        return new NoOpSpanBuilder();
    }

    @Override
    public Span getCurrentSpan() {
        return Span.getInvalid();
    }

    private static class NoOpSpanBuilder implements SpanBuilder {
        @Override
        public SpanBuilder setParent(io.opentelemetry.context.Context context) { return this; }
        @Override
        public SpanBuilder setNoParent() { return this; }
        @Override
        public SpanBuilder addLink(io.opentelemetry.api.trace.SpanContext spanContext) { return this; }
        @Override
        public SpanBuilder addLink(io.opentelemetry.api.trace.SpanContext spanContext, io.opentelemetry.api.common.Attributes attributes) { return this; }
        @Override
        public SpanBuilder setAttribute(String key, String value) { return this; }
        @Override
        public SpanBuilder setAttribute(String key, long value) { return this; }
        @Override
        public SpanBuilder setAttribute(String key, double value) { return this; }
        @Override
        public SpanBuilder setAttribute(String key, boolean value) { return this; }
        @Override
        public SpanBuilder setAllAttributes(io.opentelemetry.api.common.Attributes attributes) { return this; }
        @Override
        public SpanBuilder setSpanKind(io.opentelemetry.api.trace.SpanKind spanKind) { return this; }
        @Override
        public SpanBuilder setStartTimestamp(long timestamp, TimeUnit unit) { return this; }
        @Override
        public <T> SpanBuilder setAttribute(AttributeKey<T> key, T value) { return this; }
        @Override
        public Span startSpan() { return Span.getInvalid(); }
    }
}

