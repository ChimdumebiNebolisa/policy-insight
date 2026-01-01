package com.policyinsight.shared.repository;

import com.policyinsight.shared.model.DocumentChunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for DocumentChunk entities.
 */
@Repository
public interface DocumentChunkRepository extends JpaRepository<DocumentChunk, Long> {

    /**
     * Find all chunks for a given job UUID, ordered by chunk index.
     * @param jobUuid the job UUID
     * @return list of DocumentChunks
     */
    @Query("SELECT d FROM DocumentChunk d WHERE d.jobUuid = :jobUuid ORDER BY d.chunkIndex ASC")
    List<DocumentChunk> findByJobUuidOrderByChunkIndex(@Param("jobUuid") UUID jobUuid);

    /**
     * Find all chunks for a given job UUID.
     * @param jobUuid the job UUID
     * @return list of DocumentChunks
     */
    List<DocumentChunk> findByJobUuid(UUID jobUuid);

    /**
     * Count chunks for a given job UUID.
     * @param jobUuid the job UUID
     * @return count of chunks
     */
    long countByJobUuid(UUID jobUuid);

    /**
     * Delete all chunks for a given job UUID.
     * @param jobUuid the job UUID
     */
    void deleteByJobUuid(UUID jobUuid);
}

