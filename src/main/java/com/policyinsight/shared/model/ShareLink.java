package com.policyinsight.shared.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Entity representing a shareable link for a report.
 * Maps to the share_links table.
 */
@Entity
@Table(name = "share_links", indexes = {
    @Index(name = "idx_token", columnList = "share_token"),
    @Index(name = "idx_expires_at", columnList = "expires_at")
})
public class ShareLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "job_uuid", nullable = false, updatable = false)
    @NotNull
    private UUID jobUuid;

    @Column(name = "share_token", nullable = false, unique = true, updatable = false)
    @NotNull
    private UUID shareToken;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    @NotNull
    private Instant expiresAt;

    @Column(name = "access_count", nullable = false)
    private Integer accessCount = 0;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    // Constructors
    public ShareLink() {
        this.shareToken = UUID.randomUUID();
        this.accessCount = 0;
    }

    public ShareLink(UUID jobUuid) {
        this.jobUuid = jobUuid;
        this.shareToken = UUID.randomUUID();
        this.accessCount = 0;
        // Default expiration: 7 days from creation
        this.expiresAt = Instant.now().plusSeconds(7 * 24 * 60 * 60);
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public UUID getJobUuid() {
        return jobUuid;
    }

    public void setJobUuid(UUID jobUuid) {
        this.jobUuid = jobUuid;
    }

    public UUID getShareToken() {
        return shareToken;
    }

    public void setShareToken(UUID shareToken) {
        this.shareToken = shareToken;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Integer getAccessCount() {
        return accessCount;
    }

    public void setAccessCount(Integer accessCount) {
        this.accessCount = accessCount;
    }

    /**
     * Check if the share link is expired.
     * @return true if expired, false otherwise
     */
    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    /**
     * Check if the share link is revoked.
     * @return true if revoked, false otherwise
     */
    public boolean isRevoked() {
        return revokedAt != null;
    }

    /**
     * Revoke the share link.
     */
    public void revoke() {
        this.revokedAt = Instant.now();
    }

    /**
     * Increment the access count.
     */
    public void incrementAccessCount() {
        this.accessCount++;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public void setRevokedAt(Instant revokedAt) {
        this.revokedAt = revokedAt;
    }
}

