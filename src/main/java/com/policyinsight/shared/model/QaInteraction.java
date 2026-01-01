package com.policyinsight.shared.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Entity representing a Q&A interaction for a document.
 * Maps to the qa_interactions table.
 */
@Entity
@Table(name = "qa_interactions", indexes = {
    @Index(name = "idx_qa_interactions_job_uuid", columnList = "job_uuid")
})
public class QaInteraction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "job_uuid", nullable = false, updatable = false)
    @NotNull
    private UUID jobUuid;

    @Column(name = "question", nullable = false, columnDefinition = "TEXT")
    @NotBlank
    private String question;

    @Column(name = "answer", nullable = false, columnDefinition = "TEXT")
    @NotBlank
    private String answer;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "cited_chunks", columnDefinition = "JSONB")
    private Map<String, Object> citedChunks;

    @Column(name = "confidence", length = 20)
    @Size(max = 20)
    private String confidence; // CONFIDENT, ABSTAINED

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // Constructors
    public QaInteraction() {
    }

    public QaInteraction(UUID jobUuid, String question, String answer) {
        this.jobUuid = jobUuid;
        this.question = question;
        this.answer = answer;
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

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public String getAnswer() {
        return answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }

    public Map<String, Object> getCitedChunks() {
        return citedChunks;
    }

    public void setCitedChunks(Map<String, Object> citedChunks) {
        this.citedChunks = citedChunks;
    }

    public String getConfidence() {
        return confidence;
    }

    public void setConfidence(String confidence) {
        this.confidence = confidence;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}

