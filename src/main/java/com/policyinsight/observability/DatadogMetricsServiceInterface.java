package com.policyinsight.observability;

import io.micrometer.core.instrument.Timer;

/**
 * Interface for Datadog metrics service to support both enabled and disabled modes.
 */
public interface DatadogMetricsServiceInterface {
    void recordLlmCost(double costUsd, String model, String taskType);
    void recordLlmLatency(long durationMs, String model, String taskType);
    Timer getLlmLatencyTimer();
    void recordJobDuration(long durationMs, String jobId);
    void recordJobSuccess(String jobId);
    void recordJobFailure(String jobId, String errorType);
    void recordLlmTokens(int inputTokens, int outputTokens, String model, String taskType);
    void recordLlmCostEstimate(double costUsd, String model, String taskType);
    void recordLlmRetry(int retryCount, String model, String taskType, String errorCategory);
}

