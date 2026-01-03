package com.policyinsight.shared.repository;

import com.policyinsight.shared.model.RateLimitCounter;
import com.policyinsight.shared.model.RateLimitCounterId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

/**
 * Repository for rate limit counters with atomic upsert support.
 */
@Repository
public interface RateLimitCounterRepository extends JpaRepository<RateLimitCounter, RateLimitCounterId> {

    /**
     * Find a rate limit counter for a specific IP, endpoint, and window.
     */
    Optional<RateLimitCounter> findByIpAddressAndEndpointAndWindowStart(
            String ipAddress, String endpoint, Instant windowStart
    );

    /**
     * Sum counts for all windows within a time range for a specific IP and endpoint.
     * Used to calculate total requests in a sliding window.
     */
    @Query("""
        SELECT COALESCE(SUM(r.count), 0)
        FROM RateLimitCounter r
        WHERE r.ipAddress = :ipAddress
        AND r.endpoint = :endpoint
        AND r.windowStart >= :since
        """)
    Long sumCountsSince(
            @Param("ipAddress") String ipAddress,
            @Param("endpoint") String endpoint,
            @Param("since") Instant since
    );

    /**
     * Delete old rate limit counters (cleanup task).
     */
    @Modifying
    @Query("DELETE FROM RateLimitCounter r WHERE r.windowStart < :before")
    void deleteOldCounters(@Param("before") Instant before);
}

