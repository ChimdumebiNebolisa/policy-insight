package com.policyinsight.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.UUID;

@Entity
@Table(
        name = "document_chunks",
        uniqueConstraints = @UniqueConstraint(name = "uk_document_chunks_job_index", columnNames = {"job_id", "chunk_index"})
)
public class DocumentChunk {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_id", nullable = false)
    private PolicyJob job;

    @Column(name = "chunk_index", nullable = false)
    private int chunkIndex;

    @Column(name = "text_content", nullable = false, columnDefinition = "text")
    private String textContent;

    protected DocumentChunk() {
    }

    public DocumentChunk(PolicyJob job, int chunkIndex, String textContent) {
        this.job = job;
        this.chunkIndex = chunkIndex;
        this.textContent = textContent;
    }

    public UUID getId() {
        return id;
    }

    public PolicyJob getJob() {
        return job;
    }

    public int getChunkIndex() {
        return chunkIndex;
    }

    public String getTextContent() {
        return textContent;
    }

    public String getExcerpt() {
        if (textContent == null || textContent.length() <= 520) {
            return textContent;
        }
        return textContent.substring(0, 520).stripTrailing() + "...";
    }
}
