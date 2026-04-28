package com.policyinsight.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "policy_jobs")
public class PolicyJob {

    @Id
    @GeneratedValue
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private JobStatus status = JobStatus.PROCESSING;

    @Column(name = "owner_token_hash", nullable = false, length = 128)
    private String ownerTokenHash;

    @Column(name = "owner_token_expires_at", nullable = false)
    private Instant ownerTokenExpiresAt;

    @Column(name = "safe_error_message", length = 500)
    private String safeErrorMessage;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PolicyJob() {
    }

    public PolicyJob(String ownerTokenHash, Instant ownerTokenExpiresAt) {
        this.ownerTokenHash = ownerTokenHash;
        this.ownerTokenExpiresAt = ownerTokenExpiresAt;
    }

    public UUID getId() {
        return id;
    }

    public JobStatus getStatus() {
        return status;
    }

    public void setStatus(JobStatus status) {
        this.status = status;
    }

    public String getOwnerTokenHash() {
        return ownerTokenHash;
    }

    public Instant getOwnerTokenExpiresAt() {
        return ownerTokenExpiresAt;
    }

    public String getSafeErrorMessage() {
        return safeErrorMessage;
    }

    public void setSafeErrorMessage(String safeErrorMessage) {
        this.safeErrorMessage = safeErrorMessage;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
