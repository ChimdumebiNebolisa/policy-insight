package com.policyinsight.shared.model;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * Entity representing a rate limit counter for a specific IP address, endpoint, and time window.
 * Used for DB-backed rate limiting with atomic upsert operations.
 */
@Entity
@Table(name = "rate_limit_counters", indexes = {
    @Index(name = "idx_rate_limit_window_start", columnList = "window_start"),
    @Index(name = "idx_rate_limit_lookup", columnList = "ip_address, endpoint, window_start")
})
@IdClass(RateLimitCounterId.class)
public class RateLimitCounter {

    @Id
    @Column(name = "ip_address", length = 45, nullable = false)
    private String ipAddress;

    @Id
    @Column(name = "endpoint", length = 100, nullable = false)
    private String endpoint;

    @Id
    @Column(name = "window_start", nullable = false)
    private Instant windowStart;

    @Column(name = "count", nullable = false)
    private Integer count = 1;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // Constructors
    public RateLimitCounter() {
    }

    public RateLimitCounter(String ipAddress, String endpoint, Instant windowStart) {
        this.ipAddress = ipAddress;
        this.endpoint = endpoint;
        this.windowStart = windowStart;
        this.count = 1;
    }

    // Getters and Setters
    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public Instant getWindowStart() {
        return windowStart;
    }

    public void setWindowStart(Instant windowStart) {
        this.windowStart = windowStart;
    }

    public Integer getCount() {
        return count;
    }

    public void setCount(Integer count) {
        this.count = count;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}

