package com.policyinsight.security;

import com.policyinsight.shared.repository.RateLimitCounterRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Scheduled task to clean up old rate limit counters.
 * Deletes counters older than 24 hours to prevent unbounded table growth.
 */
@Component
public class RateLimitCleanupTask {

    private static final Logger logger = LoggerFactory.getLogger(RateLimitCleanupTask.class);

    private final RateLimitCounterRepository rateLimitCounterRepository;

    public RateLimitCleanupTask(RateLimitCounterRepository rateLimitCounterRepository) {
        this.rateLimitCounterRepository = rateLimitCounterRepository;
    }

    /**
     * Runs every hour to delete rate limit counters older than 24 hours.
     */
    @Scheduled(fixedDelayString = "3600000") // 1 hour in milliseconds
    @Transactional
    public void cleanupOldCounters() {
        try {
            Instant cutoff = Instant.now().minus(24, ChronoUnit.HOURS);
            rateLimitCounterRepository.deleteOldCounters(cutoff);
            logger.debug("Cleaned up old rate limit counters (older than 24 hours)");
        } catch (Exception e) {
            logger.error("Failed to cleanup old rate limit counters", e);
        }
    }
}

