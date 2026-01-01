package com.policyinsight.shared.repository;

import com.policyinsight.shared.model.ShareLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for ShareLink entities.
 */
@Repository
public interface ShareLinkRepository extends JpaRepository<ShareLink, Long> {

    /**
     * Find a share link by its token.
     * @param shareToken the share token
     * @return Optional containing the ShareLink if found
     */
    Optional<ShareLink> findByShareToken(UUID shareToken);

    /**
     * Find all share links for a given job UUID.
     * @param jobUuid the job UUID
     * @return list of ShareLinks
     */
    List<ShareLink> findByJobUuid(UUID jobUuid);

    /**
     * Find all expired share links.
     * @param now current timestamp
     * @return list of expired ShareLinks
     */
    @Query("SELECT s FROM ShareLink s WHERE s.expiresAt < :now")
    List<ShareLink> findExpiredLinks(@Param("now") Instant now);

    /**
     * Delete all expired share links.
     * @param now current timestamp
     */
    @Query("DELETE FROM ShareLink s WHERE s.expiresAt < :now")
    void deleteExpiredLinks(@Param("now") Instant now);
}

