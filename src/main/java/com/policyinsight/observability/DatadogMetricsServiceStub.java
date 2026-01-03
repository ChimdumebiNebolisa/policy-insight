package com.policyinsight.observability;

import io.micrometer.core.instrument.Timer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Stub implementation when Datadog is disabled.
 * Prevents NullPointerException when DatadogMetricsService is not available.
 */
@Service
@ConditionalOnProperty(name = "datadog.enabled", havingValue = "false", matchIfMissing = true)
public class DatadogMetricsServiceStub implements DatadogMetricsServiceInterface {

    @Override
    public void recordLlmCost(double costUsd, String model, String taskType) {
        // No-op when Datadog is disabled
    }

    @Override
    public void recordLlmLatency(long durationMs, String model, String taskType) {
        // No-op when Datadog is disabled
    }

    @Override
    public Timer getLlmLatencyTimer() {
        return null;
    }

    @Override
    public void recordJobDuration(long durationMs, String jobId) {
        // No-op when Datadog is disabled
    }

    @Override
    public void recordJobSuccess(String jobId) {
        // No-op when Datadog is disabled
    }

    @Override
    public void recordJobFailure(String jobId, String errorType) {
        // No-op when Datadog is disabled
    }

    @Override
    public void recordLlmTokens(int inputTokens, int outputTokens, String model, String taskType) {
        // No-op when Datadog is disabled
    }

    @Override
    public void recordLlmCostEstimate(double costUsd, String model, String taskType) {
        // No-op when Datadog is disabled
    }

    @Override
    public void recordLlmRetry(int retryCount, String model, String taskType, String errorCategory) {
        // No-op when Datadog is disabled
    }
}

