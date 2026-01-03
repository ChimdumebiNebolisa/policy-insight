package com.policyinsight.shared.repository;

import com.policyinsight.shared.model.PolicyJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for PolicyJob entities.
 */
@Repository
public interface PolicyJobRepository extends JpaRepository<PolicyJob, Long> {

    /**
     * Find a policy job by its UUID.
     * @param jobUuid the job UUID
     * @return Optional containing the PolicyJob if found
     */
    Optional<PolicyJob> findByJobUuid(UUID jobUuid);

    /**
     * Check if a policy job exists with the given UUID.
     * @param jobUuid the job UUID
     * @return true if exists, false otherwise
     */
    boolean existsByJobUuid(UUID jobUuid);

    /**
     * Find all jobs by status, ordered by creation date descending.
     * @param status the job status
     * @return list of PolicyJobs
     */
    @Query("SELECT p FROM PolicyJob p WHERE p.status = :status ORDER BY p.createdAt DESC")
    java.util.List<PolicyJob> findByStatusOrderByCreatedAtDesc(@Param("status") String status);

    /**
     * Find the oldest PENDING jobs (up to limit) using SELECT FOR UPDATE SKIP LOCKED
     * to atomically claim jobs and prevent race conditions in multi-instance scenarios.
     * This method uses PostgreSQL's SKIP LOCKED feature to ensure only one instance processes a job.
     * @param limit maximum number of jobs to return
     * @return list of PolicyJob entities that were successfully locked
     */
    @Query(value = "SELECT * FROM policy_jobs WHERE status = 'PENDING' ORDER BY created_at ASC LIMIT :limit FOR UPDATE SKIP LOCKED", nativeQuery = true)
    java.util.List<PolicyJob> findOldestPendingJobsForUpdate(@Param("limit") int limit);

    /**
     * Find the oldest PENDING jobs (up to limit) without locking.
     * Used for non-atomic queries or when locking is not needed.
     * @param limit maximum number of jobs to return
     * @return list of PolicyJob entities
     */
    @Query(value = "SELECT * FROM policy_jobs WHERE status = 'PENDING' ORDER BY created_at ASC LIMIT :limit", nativeQuery = true)
    java.util.List<PolicyJob> findOldestPendingJobs(@Param("limit") int limit);

    /**
     * Atomically update job status from PENDING to PROCESSING.
     * This method ensures idempotency by only updating jobs that are in PENDING status.
     * Returns the number of rows updated (0 if job was not PENDING, 1 if successfully updated).
     *
     * @param jobUuid the job UUID to update
     * @return the number of rows updated (0 or 1)
     */
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query(
            value = "UPDATE policy_jobs SET status = 'PROCESSING', started_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP " +
                    "WHERE job_uuid = :jobUuid AND status = 'PENDING'",
            nativeQuery = true
    )
    int updateStatusIfPending(@Param("jobUuid") UUID jobUuid);

    /**
     * Atomically update job status from PENDING to PROCESSING with lease and attempt count.
     * Sets lease_expires_at, increments attempt_count, and updates status atomically.
     *
     * @param jobUuid the job UUID to update
     * @param leaseExpiresAt when the lease expires
     * @return the number of rows updated (0 or 1)
     */
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query(
            value = "UPDATE policy_jobs SET " +
                    "status = 'PROCESSING', " +
                    "started_at = CURRENT_TIMESTAMP, " +
                    "updated_at = CURRENT_TIMESTAMP, " +
                    "lease_expires_at = :leaseExpiresAt, " +
                    "attempt_count = attempt_count + 1 " +
                    "WHERE job_uuid = :jobUuid AND status = 'PENDING'",
            nativeQuery = true
    )
    int updateStatusIfPendingWithLease(
            @Param("jobUuid") UUID jobUuid,
            @Param("leaseExpiresAt") java.time.Instant leaseExpiresAt
    );

    /**
     * Find PROCESSING jobs with expired leases (stale jobs).
     * Used by the reaper to identify jobs that need recovery.
     *
     * @param now current timestamp
     * @return list of stale PROCESSING jobs
     */
    @Query("SELECT p FROM PolicyJob p WHERE p.status = 'PROCESSING' AND p.leaseExpiresAt < :now")
    java.util.List<PolicyJob> findStaleProcessingJobs(@Param("now") java.time.Instant now);
}

