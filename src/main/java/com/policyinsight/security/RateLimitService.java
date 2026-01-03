package com.policyinsight.security;

import com.policyinsight.shared.model.RateLimitCounter;
import com.policyinsight.shared.model.RateLimitCounterId;
import com.policyinsight.shared.repository.RateLimitCounterRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * Service for rate limiting using DB-backed counters.
 * Supports per-IP and per-endpoint rate limiting with configurable windows.
 */
@Service
public class RateLimitService {

    private static final Logger logger = LoggerFactory.getLogger(RateLimitService.class);

    private final RateLimitCounterRepository rateLimitCounterRepository;

    // Default limits (configurable via application.yml)
    private final int uploadMaxPerHour;
    private final int qaMaxPerHour;
    private final int qaMaxPerJob;

    public RateLimitService(
            RateLimitCounterRepository rateLimitCounterRepository,
            @Value("${app.rate-limit.upload.max-per-hour:10}") int uploadMaxPerHour,
            @Value("${app.rate-limit.qa.max-per-hour:20}") int qaMaxPerHour,
            @Value("${app.rate-limit.qa.max-per-job:3}") int qaMaxPerJob) {
        this.rateLimitCounterRepository = rateLimitCounterRepository;
        this.uploadMaxPerHour = uploadMaxPerHour;
        this.qaMaxPerHour = qaMaxPerHour;
        this.qaMaxPerJob = qaMaxPerJob;
    }

    /**
     * Extracts client IP address from request, handling X-Forwarded-For header.
     * Returns the left-most IP (original client) if present, else remote address.
     */
    public String extractClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            // X-Forwarded-For can contain multiple IPs: "client, proxy1, proxy2"
            // Take the left-most (original client)
            String[] ips = xForwardedFor.split(",");
            if (ips.length > 0) {
                return ips[0].trim();
            }
        }
        return request.getRemoteAddr();
    }

    /**
     * Checks if a rate limit has been exceeded for the given IP and endpoint.
     * Uses a sliding window approach with 1-hour windows.
     *
     * @param ipAddress client IP address
     * @param endpoint endpoint identifier (e.g., "/api/documents/upload")
     * @param maxRequests maximum requests allowed per hour
     * @return true if limit exceeded, false otherwise
     */
    @Transactional
    public boolean checkRateLimit(String ipAddress, String endpoint, int maxRequests) {
        Instant now = Instant.now();
        // Round down to hour boundary for window start
        Instant windowStart = now.truncatedTo(ChronoUnit.HOURS);

        // Atomically increment or insert counter
        incrementCounter(ipAddress, endpoint, windowStart);

        // Sum all counts in the last hour (sliding window)
        Instant oneHourAgo = now.minus(1, ChronoUnit.HOURS);
        Long totalCount = rateLimitCounterRepository.sumCountsSince(ipAddress, endpoint, oneHourAgo);

        boolean exceeded = totalCount != null && totalCount > maxRequests;

        if (exceeded) {
            logger.debug("Rate limit exceeded: ip={}, endpoint={}, count={}, max={}",
                    ipAddress, endpoint, totalCount, maxRequests);
        }

        return exceeded;
    }

    /**
     * Atomically increments or inserts a rate limit counter.
     * Uses find-then-save pattern within a transaction for atomicity.
     */
    private Integer incrementCounter(String ipAddress, String endpoint, Instant windowStart) {
        RateLimitCounterId id = new RateLimitCounterId(ipAddress, endpoint, windowStart);
        Optional<RateLimitCounter> existing = rateLimitCounterRepository.findById(id);

        if (existing.isPresent()) {
            RateLimitCounter counter = existing.get();
            counter.setCount(counter.getCount() + 1);
            rateLimitCounterRepository.save(counter);
            return counter.getCount();
        } else {
            RateLimitCounter counter = new RateLimitCounter(ipAddress, endpoint, windowStart);
            counter.setCount(1);
            rateLimitCounterRepository.save(counter);
            return 1;
        }
    }

    /**
     * Checks upload rate limit for the given request.
     */
    public boolean checkUploadRateLimit(HttpServletRequest request) {
        String ipAddress = extractClientIp(request);
        return checkRateLimit(ipAddress, "/api/documents/upload", uploadMaxPerHour);
    }

    /**
     * Checks Q&A rate limit for the given request.
     */
    public boolean checkQaRateLimit(HttpServletRequest request) {
        String ipAddress = extractClientIp(request);
        return checkRateLimit(ipAddress, "/api/questions", qaMaxPerHour);
    }

    /**
     * Gets the maximum Q&A questions allowed per job.
     */
    public int getQaMaxPerJob() {
        return qaMaxPerJob;
    }

    /**
     * Gets the current count for a specific IP and endpoint in the last hour.
     * Used for debugging/monitoring.
     */
    public long getCurrentCount(String ipAddress, String endpoint) {
        Instant oneHourAgo = Instant.now().minus(1, ChronoUnit.HOURS);
        Long count = rateLimitCounterRepository.sumCountsSince(ipAddress, endpoint, oneHourAgo);
        return count != null ? count : 0L;
    }
}

