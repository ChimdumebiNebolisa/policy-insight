package com.policyinsight.shared.repository;

import com.policyinsight.shared.model.Report;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for Report entities.
 */
@Repository
public interface ReportRepository extends JpaRepository<Report, Long> {

    /**
     * Find a report by its job UUID.
     * @param jobUuid the job UUID
     * @return Optional containing the Report if found
     */
    Optional<Report> findByJobUuid(UUID jobUuid);

    /**
     * Check if a report exists for the given job UUID.
     * @param jobUuid the job UUID
     * @return true if exists, false otherwise
     */
    boolean existsByJobUuid(UUID jobUuid);

    /**
     * Delete a report by its job UUID.
     * @param jobUuid the job UUID
     */
    void deleteByJobUuid(UUID jobUuid);
}

