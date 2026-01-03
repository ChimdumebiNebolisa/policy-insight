package com.policyinsight.shared.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Entity representing a document analysis job.
 * Maps to the policy_jobs table.
 */
@Entity
@Table(name = "policy_jobs", indexes = {
    @Index(name = "idx_uuid", columnList = "job_uuid"),
    @Index(name = "idx_status_created", columnList = "status, created_at")
})
public class PolicyJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "job_uuid", nullable = false, unique = true, updatable = false)
    @NotNull
    private UUID jobUuid;

    @Column(name = "status", length = 20)
    @Size(max = 20)
    private String status = "PENDING";

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "pdf_gcs_path", length = 255)
    @Size(max = 255)
    private String pdfGcsPath;

    @Column(name = "pdf_filename", length = 255)
    @Size(max = 255)
    private String pdfFilename;

    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    @Column(name = "classification", length = 50)
    @Size(max = 50)
    private String classification;

    @Column(name = "classification_confidence", precision = 3, scale = 2)
    private BigDecimal classificationConfidence;

    @Column(name = "doc_type_detected_page")
    private Integer docTypeDetectedPage;

    @Column(name = "report_gcs_path", length = 255)
    @Size(max = 255)
    private String reportGcsPath;

    @Column(name = "chunks_json_gcs_path", length = 255)
    @Size(max = 255)
    private String chunksJsonGcsPath;

    @Column(name = "dd_trace_id", length = 255)
    @Size(max = 255)
    private String ddTraceId;

    @Column(name = "access_token_hmac", length = 255)
    @Size(max = 255)
    private String accessTokenHmac;

    // Constructors
    public PolicyJob() {
        this.jobUuid = UUID.randomUUID();
    }

    public PolicyJob(UUID jobUuid) {
        this.jobUuid = jobUuid;
        this.status = "PENDING";
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public String getPdfGcsPath() {
        return pdfGcsPath;
    }

    public void setPdfGcsPath(String pdfGcsPath) {
        this.pdfGcsPath = pdfGcsPath;
    }

    public String getPdfFilename() {
        return pdfFilename;
    }

    public void setPdfFilename(String pdfFilename) {
        this.pdfFilename = pdfFilename;
    }

    public Long getFileSizeBytes() {
        return fileSizeBytes;
    }

    public void setFileSizeBytes(Long fileSizeBytes) {
        this.fileSizeBytes = fileSizeBytes;
    }

    public String getClassification() {
        return classification;
    }

    public void setClassification(String classification) {
        this.classification = classification;
    }

    public BigDecimal getClassificationConfidence() {
        return classificationConfidence;
    }

    public void setClassificationConfidence(BigDecimal classificationConfidence) {
        this.classificationConfidence = classificationConfidence;
    }

    public Integer getDocTypeDetectedPage() {
        return docTypeDetectedPage;
    }

    public void setDocTypeDetectedPage(Integer docTypeDetectedPage) {
        this.docTypeDetectedPage = docTypeDetectedPage;
    }

    public String getReportGcsPath() {
        return reportGcsPath;
    }

    public void setReportGcsPath(String reportGcsPath) {
        this.reportGcsPath = reportGcsPath;
    }

    public String getChunksJsonGcsPath() {
        return chunksJsonGcsPath;
    }

    public void setChunksJsonGcsPath(String chunksJsonGcsPath) {
        this.chunksJsonGcsPath = chunksJsonGcsPath;
    }

    public String getDdTraceId() {
        return ddTraceId;
    }

    public void setDdTraceId(String ddTraceId) {
        this.ddTraceId = ddTraceId;
    }

    public String getAccessTokenHmac() {
        return accessTokenHmac;
    }

    public void setAccessTokenHmac(String accessTokenHmac) {
        this.accessTokenHmac = accessTokenHmac;
    }
}

