package com.policyinsight.shared.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Entity representing an analysis report for a document.
 * Maps to the reports table.
 * Uses JSONB columns for flexible nested data structures.
 */
@Entity
@Table(name = "reports", indexes = {
    @Index(name = "idx_reports_job_uuid", columnList = "job_uuid")
})
public class Report {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "job_uuid", nullable = false, unique = true, updatable = false)
    @NotNull
    private UUID jobUuid;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "document_overview", columnDefinition = "JSONB")
    private Map<String, Object> documentOverview;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "summary_bullets", columnDefinition = "JSONB")
    private Map<String, Object> summaryBullets;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "obligations", columnDefinition = "JSONB")
    private Map<String, Object> obligations;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "restrictions", columnDefinition = "JSONB")
    private Map<String, Object> restrictions;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "termination_triggers", columnDefinition = "JSONB")
    private Map<String, Object> terminationTriggers;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "risk_taxonomy", columnDefinition = "JSONB")
    private Map<String, Object> riskTaxonomy;

    @Column(name = "generated_at")
    private Instant generatedAt;

    @Column(name = "gcs_path", length = 255)
    private String gcsPath;

    // Constructors
    public Report() {
    }

    public Report(UUID jobUuid) {
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

    public Map<String, Object> getDocumentOverview() {
        return documentOverview;
    }

    public void setDocumentOverview(Map<String, Object> documentOverview) {
        this.documentOverview = documentOverview;
    }

    public Map<String, Object> getSummaryBullets() {
        return summaryBullets;
    }

    public void setSummaryBullets(Map<String, Object> summaryBullets) {
        this.summaryBullets = summaryBullets;
    }

    public Map<String, Object> getObligations() {
        return obligations;
    }

    public void setObligations(Map<String, Object> obligations) {
        this.obligations = obligations;
    }

    public Map<String, Object> getRestrictions() {
        return restrictions;
    }

    public void setRestrictions(Map<String, Object> restrictions) {
        this.restrictions = restrictions;
    }

    public Map<String, Object> getTerminationTriggers() {
        return terminationTriggers;
    }

    public void setTerminationTriggers(Map<String, Object> terminationTriggers) {
        this.terminationTriggers = terminationTriggers;
    }

    public Map<String, Object> getRiskTaxonomy() {
        return riskTaxonomy;
    }

    public void setRiskTaxonomy(Map<String, Object> riskTaxonomy) {
        this.riskTaxonomy = riskTaxonomy;
    }

    public Instant getGeneratedAt() {
        return generatedAt;
    }

    public void setGeneratedAt(Instant generatedAt) {
        this.generatedAt = generatedAt;
    }

    public String getGcsPath() {
        return gcsPath;
    }

    public void setGcsPath(String gcsPath) {
        this.gcsPath = gcsPath;
    }
}

