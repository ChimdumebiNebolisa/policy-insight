package com.policyinsight.observability;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;

import java.util.function.Supplier;

/**
 * Interface for tracing service to support both enabled and disabled modes.
 */
public interface TracingServiceInterface {
    <T> T trace(String spanName, Supplier<T> operation);
    void trace(String spanName, Runnable operation);
    SpanBuilder spanBuilder(String spanName);
    Span getCurrentSpan();
}

