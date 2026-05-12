package com.policyinsight.repository;

import com.policyinsight.model.ShareLink;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ShareLinkRepository extends JpaRepository<ShareLink, UUID> {

    Optional<ShareLink> findByTokenHashAndExpiresAtAfter(String tokenHash, Instant now);

    long countByReport_Id(UUID reportId);

    @Modifying
    @Query("DELETE FROM ShareLink sl WHERE sl.report.id = :reportId")
    void deleteByReportId(@Param("reportId") UUID reportId);

    @Modifying
    int deleteByExpiresAtBefore(Instant expiresAtBefore);
}
