package com.policyinsight.repository;

import com.policyinsight.model.ShareLink;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShareLinkRepository extends JpaRepository<ShareLink, UUID> {

    Optional<ShareLink> findByTokenHashAndExpiresAtAfter(String tokenHash, Instant now);
}
