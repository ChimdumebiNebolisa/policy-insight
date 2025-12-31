package com.policyinsight.observability;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;

import javax.annotation.Nonnull;
import java.util.function.Supplier;

/**
 * Interface for tracing service to support both enabled and disabled modes.
 */
public interface TracingServiceInterface {
    <T> T trace(@Nonnull String spanName, @Nonnull Supplier<T> operation);
    void trace(@Nonnull String spanName, @Nonnull Runnable operation);
    @Nonnull SpanBuilder spanBuilder(@Nonnull String spanName);
    Span getCurrentSpan();
}

