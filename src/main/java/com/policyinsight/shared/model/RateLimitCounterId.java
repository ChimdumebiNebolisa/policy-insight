package com.policyinsight.shared.model;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/**
 * Composite primary key for RateLimitCounter entity.
 */
public class RateLimitCounterId implements Serializable {

    private String ipAddress;
    private String endpoint;
    private Instant windowStart;

    public RateLimitCounterId() {
    }

    public RateLimitCounterId(String ipAddress, String endpoint, Instant windowStart) {
        this.ipAddress = ipAddress;
        this.endpoint = endpoint;
        this.windowStart = windowStart;
    }

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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RateLimitCounterId that = (RateLimitCounterId) o;
        return Objects.equals(ipAddress, that.ipAddress) &&
               Objects.equals(endpoint, that.endpoint) &&
               Objects.equals(windowStart, that.windowStart);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ipAddress, endpoint, windowStart);
    }
}

