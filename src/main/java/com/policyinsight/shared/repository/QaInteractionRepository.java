package com.policyinsight.shared.repository;

import com.policyinsight.shared.model.QaInteraction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for QaInteraction entities.
 */
@Repository
public interface QaInteractionRepository extends JpaRepository<QaInteraction, Long> {

    /**
     * Find all Q&A interactions for a given job UUID, ordered by creation date descending.
     * @param jobUuid the job UUID
     * @return list of QaInteractions
     */
    @Query("SELECT q FROM QaInteraction q WHERE q.jobUuid = :jobUuid ORDER BY q.createdAt DESC")
    List<QaInteraction> findByJobUuidOrderByCreatedAtDesc(@Param("jobUuid") UUID jobUuid);

    /**
     * Find all Q&A interactions for a given job UUID.
     * @param jobUuid the job UUID
     * @return list of QaInteractions
     */
    List<QaInteraction> findByJobUuid(UUID jobUuid);

    /**
     * Count Q&A interactions for a given job UUID.
     * @param jobUuid the job UUID
     * @return count of interactions
     */
    long countByJobUuid(UUID jobUuid);
}

