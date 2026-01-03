package com.policyinsight.shared.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Entity representing a text chunk extracted from a document.
 * Maps to the document_chunks table.
 */
@Entity
@Table(name = "document_chunks", indexes = {
    @Index(name = "idx_document_chunks_job_uuid", columnList = "job_uuid")
}, uniqueConstraints = {
    @UniqueConstraint(name = "uk_document_chunks_job_uuid_chunk_index", columnNames = {"job_uuid", "chunk_index"})
})
public class DocumentChunk {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "job_uuid", nullable = false, updatable = false)
    @NotNull
    private UUID jobUuid;

    @Column(name = "chunk_index")
    private Integer chunkIndex;

    @Column(name = "text", columnDefinition = "TEXT")
    private String text;

    @Column(name = "page_number")
    private Integer pageNumber;

    @Column(name = "start_offset")
    private Integer startOffset;

    @Column(name = "end_offset")
    private Integer endOffset;

    @Column(name = "span_confidence", precision = 3, scale = 2)
    private BigDecimal spanConfidence;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // Constructors
    public DocumentChunk() {
    }

    public DocumentChunk(UUID jobUuid) {
        this.jobUuid = jobUuid;
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

    public Integer getChunkIndex() {
        return chunkIndex;
    }

    public void setChunkIndex(Integer chunkIndex) {
        this.chunkIndex = chunkIndex;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public Integer getPageNumber() {
        return pageNumber;
    }

    public void setPageNumber(Integer pageNumber) {
        this.pageNumber = pageNumber;
    }

    public Integer getStartOffset() {
        return startOffset;
    }

    public void setStartOffset(Integer startOffset) {
        this.startOffset = startOffset;
    }

    public Integer getEndOffset() {
        return endOffset;
    }

    public void setEndOffset(Integer endOffset) {
        this.endOffset = endOffset;
    }

    public BigDecimal getSpanConfidence() {
        return spanConfidence;
    }

    public void setSpanConfidence(BigDecimal spanConfidence) {
        this.spanConfidence = spanConfidence;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}

