package com.policyinsight.repository;

import com.policyinsight.model.ShareLink;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;

public interface ShareLinkRepository extends JpaRepository<ShareLink, UUID> {

    Optional<ShareLink> findByTokenHashAndExpiresAtAfter(String tokenHash, Instant now);

    @Modifying
    int deleteByExpiresAtBefore(Instant expiresAtBefore);
}
