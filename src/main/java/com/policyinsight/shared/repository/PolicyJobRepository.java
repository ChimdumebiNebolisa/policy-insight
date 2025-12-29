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
}

