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
}

