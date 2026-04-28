package com.policyinsight.repository;

import com.policyinsight.model.DocumentChunk;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentChunkRepository extends JpaRepository<DocumentChunk, UUID> {

    List<DocumentChunk> findByJobIdOrderByChunkIndex(UUID jobId);

    long countByJobIdAndIdIn(UUID jobId, Collection<UUID> ids);
}
