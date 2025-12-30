package com.policyinsight.observability;

import com.policyinsight.shared.repository.PolicyJobRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.concurrent.TimeUnit;

/**
 * Service for emitting custom Datadog metrics.
 * Only active when datadog.enabled=true.
 *
 * Metrics:
 * - policyinsight.queue.backlog: Gauge of pending jobs in queue
 * - policyinsight.llm.cost_usd: Counter for LLM API costs
 * - policyinsight.llm.latency_ms: Timer for LLM call latency
 * - policyinsight.llm.tokens: Counter for LLM token usage
 * - policyinsight.llm.cost_estimate_usd: Counter for estimated LLM costs
 * - policyinsight.job.duration: Timer for job processing duration
 * - policyinsight.job.success: Counter for successful jobs
 * - policyinsight.job.failure: Counter for failed jobs
 */
@Service
@ConditionalOnProperty(name = "datadog.enabled", havingValue = "true", matchIfMissing = false)
public class DatadogMetricsService implements DatadogMetricsServiceInterface {

    private static final Logger logger = LoggerFactory.getLogger(DatadogMetricsService.class);

    private final MeterRegistry meterRegistry;
    private final PolicyJobRepository policyJobRepository;

    private Timer llmLatencyTimer;
    private Timer jobDurationTimer;
    @SuppressWarnings("unused")
    private Counter jobSuccessCounter;
    @SuppressWarnings("unused")
    private Counter jobFailureCounter;
    @SuppressWarnings("unused")
    private Counter llmTokensCounter;
    @SuppressWarnings("unused")
    private Counter llmCostEstimateCounter;

    @Autowired
    public DatadogMetricsService(MeterRegistry meterRegistry, PolicyJobRepository policyJobRepository) {
        this.meterRegistry = meterRegistry;
        this.policyJobRepository = policyJobRepository;
    }

    @PostConstruct
    public void initializeMetrics() {
        // Register queue backlog gauge (updated via scheduled task)
        Gauge.builder("policyinsight.job.backlog", this, DatadogMetricsService::getQueueBacklog)
                .description("Number of pending jobs in the processing queue")
                .tag("service", "policy-insight")
                .register(meterRegistry);

        // Initialize LLM latency timer (for getLlmLatencyTimer method)
        llmLatencyTimer = Timer.builder("policyinsight.llm.latency_ms")
                .description("LLM API call latency in milliseconds")
                .tag("service", "policy-insight")
                .register(meterRegistry);

        // Initialize job duration timer
        jobDurationTimer = Timer.builder("policyinsight.job.duration")
                .description("Job processing duration in milliseconds")
                .tag("service", "policy-insight")
                .register(meterRegistry);

        // Initialize job success counter
        jobSuccessCounter = Counter.builder("policyinsight.job.success")
                .description("Number of successfully completed jobs")
                .tag("service", "policy-insight")
                .register(meterRegistry);

        // Initialize job failure counter
        jobFailureCounter = Counter.builder("policyinsight.job.failure")
                .description("Number of failed jobs")
                .tag("service", "policy-insight")
                .register(meterRegistry);

        // Initialize LLM tokens counter
        llmTokensCounter = Counter.builder("policyinsight.llm.tokens")
                .description("Total LLM tokens used (input + output)")
                .tag("service", "policy-insight")
                .register(meterRegistry);

        // Initialize LLM cost estimate counter
        llmCostEstimateCounter = Counter.builder("policyinsight.llm.cost_estimate_usd")
                .description("Estimated LLM API cost in USD")
                .tag("service", "policy-insight")
                .register(meterRegistry);

        logger.info("Datadog metrics service initialized");
    }

    /**
     * Get current queue backlog (number of PENDING jobs).
     * Called by Gauge to get current value.
     */
    private double getQueueBacklog() {
        try {
            return policyJobRepository.findByStatusOrderByCreatedAtDesc("PENDING").size();
        } catch (Exception e) {
            logger.warn("Failed to get queue backlog: {}", e.getMessage());
            return 0.0;
        }
    }

    /**
     * Record LLM API cost.
     * @param costUsd Cost in USD
     * @param model Model name (e.g., "gemini-2.0-flash-exp")
     * @param taskType Task type (e.g., "classification", "risk_analysis", "summary")
     */
    public void recordLlmCost(double costUsd, String model, String taskType) {
        Counter.builder("policyinsight.llm.cost_usd")
                .description("Cumulative LLM API cost in USD")
                .tag("service", "policy-insight")
                .tag("model", model != null ? model : "unknown")
                .tag("task_type", taskType != null ? taskType : "unknown")
                .register(meterRegistry)
                .increment(costUsd);
        logger.debug("Recorded LLM cost: ${} for model={}, task={}", costUsd, model, taskType);
    }

    /**
     * Record LLM API latency.
     * @param durationMs Latency in milliseconds
     * @param model Model name
     * @param taskType Task type
     */
    public void recordLlmLatency(long durationMs, String model, String taskType) {
        Timer.builder("policyinsight.llm.latency_ms")
                .description("LLM API call latency in milliseconds")
                .tag("service", "policy-insight")
                .tag("model", model != null ? model : "unknown")
                .tag("task_type", taskType != null ? taskType : "unknown")
                .register(meterRegistry)
                .record(durationMs, java.util.concurrent.TimeUnit.MILLISECONDS);
        logger.debug("Recorded LLM latency: {}ms for model={}, task={}", durationMs, model, taskType);
    }

    /**
     * Get the LLM latency timer for manual recording.
     * @return Timer instance
     */
    @Override
    public Timer getLlmLatencyTimer() {
        return llmLatencyTimer;
    }

    /**
     * Record job processing duration.
     * @param durationMs Duration in milliseconds
     * @param jobId Job ID (optional, for tagging)
     */
    public void recordJobDuration(long durationMs, String jobId) {
        Timer.Sample sample = Timer.start(meterRegistry);
        sample.stop(jobDurationTimer);
        if (jobId != null) {
            Timer.builder("policyinsight.job.duration")
                    .tag("service", "policy-insight")
                    .tag("job_id", jobId)
                    .register(meterRegistry)
                    .record(durationMs, TimeUnit.MILLISECONDS);
        }
        logger.debug("Recorded job duration: {}ms for job={}", durationMs, jobId);
    }

    /**
     * Record successful job completion.
     * @param jobId Job ID (optional, for tagging)
     */
    public void recordJobSuccess(String jobId) {
        Counter counter = Counter.builder("policyinsight.job.success")
                .tag("service", "policy-insight")
                .tag("job_id", jobId != null ? jobId : "unknown")
                .register(meterRegistry);
        counter.increment();
        logger.debug("Recorded job success for job={}", jobId);
    }

    /**
     * Record failed job.
     * @param jobId Job ID (optional, for tagging)
     * @param errorType Error type/category (optional)
     */
    public void recordJobFailure(String jobId, String errorType) {
        Counter counter = Counter.builder("policyinsight.job.failure")
                .tag("service", "policy-insight")
                .tag("job_id", jobId != null ? jobId : "unknown")
                .tag("error_type", errorType != null ? errorType : "unknown")
                .register(meterRegistry);
        counter.increment();
        logger.debug("Recorded job failure for job={}, errorType={}", jobId, errorType);
    }

    /**
     * Record LLM token usage.
     * @param inputTokens Input tokens
     * @param outputTokens Output tokens
     * @param model Model name
     * @param taskType Task type
     */
    public void recordLlmTokens(int inputTokens, int outputTokens, String model, String taskType) {
        int totalTokens = inputTokens + outputTokens;
        Counter counter = Counter.builder("policyinsight.llm.tokens")
                .tag("service", "policy-insight")
                .tag("model", model != null ? model : "unknown")
                .tag("task_type", taskType != null ? taskType : "unknown")
                .tag("token_type", "total")
                .register(meterRegistry);
        counter.increment(totalTokens);

        // Also record input and output separately
        Counter.builder("policyinsight.llm.tokens")
                .tag("service", "policy-insight")
                .tag("model", model != null ? model : "unknown")
                .tag("task_type", taskType != null ? taskType : "unknown")
                .tag("token_type", "input")
                .register(meterRegistry)
                .increment(inputTokens);

        Counter.builder("policyinsight.llm.tokens")
                .tag("service", "policy-insight")
                .tag("model", model != null ? model : "unknown")
                .tag("task_type", taskType != null ? taskType : "unknown")
                .tag("token_type", "output")
                .register(meterRegistry)
                .increment(outputTokens);

        logger.debug("Recorded LLM tokens: input={}, output={}, total={} for model={}, task={}",
                inputTokens, outputTokens, totalTokens, model, taskType);
    }

    /**
     * Record estimated LLM cost.
     * @param costUsd Cost in USD
     * @param model Model name
     * @param taskType Task type
     */
    public void recordLlmCostEstimate(double costUsd, String model, String taskType) {
        llmCostEstimateCounter.increment(costUsd);
        Counter.builder("policyinsight.llm.cost_estimate_usd")
                .tag("service", "policy-insight")
                .tag("model", model != null ? model : "unknown")
                .tag("task_type", taskType != null ? taskType : "unknown")
                .register(meterRegistry)
                .increment(costUsd);
        logger.debug("Recorded LLM cost estimate: ${} for model={}, task={}", costUsd, model, taskType);
    }
}

