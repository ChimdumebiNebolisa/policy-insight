package com.policyinsight.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.policyinsight.model.DocumentChunk;
import com.policyinsight.model.PolicyJob;
import com.policyinsight.model.Report;
import com.policyinsight.model.ShareLink;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.containers.PostgreSQLContainer;

@SpringBootTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class PostgresIntegrationIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("app.token-secret", () -> "postgres-integration-test-secret-value");
        registry.add("app.ai.provider", () -> "mock");
        registry.add("app.cleanup.enabled", () -> "false");
    }

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    PolicyJobRepository policyJobRepository;

    @Autowired
    DocumentChunkRepository documentChunkRepository;

    @Autowired
    ReportRepository reportRepository;

    @Autowired
    ShareLinkRepository shareLinkRepository;

    @Test
    void flywayMigrationsApplyAndCoreTablesExist() {
        List<String> tables = jdbcTemplate.queryForList("""
                select table_name
                from information_schema.tables
                where table_schema = 'public'
                """, String.class);

        assertThat(tables).contains(
                "policy_jobs",
                "document_chunks",
                "reports",
                "share_links",
                "qa_interactions",
                "flyway_schema_history"
        );
        assertThat(jdbcTemplate.queryForObject("select count(*) from flyway_schema_history", Integer.class))
                .isGreaterThanOrEqualTo(4);
    }

    @Test
    void statusCheckConstraintRejectsInvalidState() {
        assertThatThrownBy(() -> jdbcTemplate.update("""
                insert into policy_jobs
                    (id, status, owner_token_hash, owner_token_expires_at, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?)
                """,
                UUID.randomUUID(),
                "BAD_STATUS",
                "owner-hash",
                Timestamp.from(Instant.now().plus(1, ChronoUnit.HOURS)),
                Timestamp.from(Instant.now()),
                Timestamp.from(Instant.now())
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void uniqueConstraintsAndCorePersistenceWorkAgainstPostgres() {
        PolicyJob job = policyJobRepository.saveAndFlush(
                new PolicyJob("owner-hash-" + UUID.randomUUID(), Instant.now().plus(1, ChronoUnit.HOURS))
        );
        DocumentChunk chunk = documentChunkRepository.saveAndFlush(new DocumentChunk(job, 0, "Postgres chunk text."));
        Report report = reportRepository.saveAndFlush(new Report(job, "{\"documentOverview\":\"overview\"}"));
        String tokenHash = "share-hash-" + UUID.randomUUID();
        ShareLink shareLink = shareLinkRepository.saveAndFlush(
                new ShareLink(report, tokenHash, Instant.now().plus(1, ChronoUnit.DAYS))
        );

        assertThat(documentChunkRepository.findByJobIdOrderByChunkIndex(job.getId()))
                .extracting(DocumentChunk::getId)
                .containsExactly(chunk.getId());
        assertThat(reportRepository.findByJobId(job.getId())).contains(report);
        assertThat(shareLinkRepository.findByTokenHashAndExpiresAtAfter(tokenHash, Instant.now()))
                .contains(shareLink);

        assertThatThrownBy(() -> jdbcTemplate.update("""
                insert into document_chunks (id, job_id, chunk_index, text_content)
                values (?, ?, ?, ?)
                """, UUID.randomUUID(), job.getId(), 0, "Duplicate index."))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbcTemplate.update("""
                insert into reports (id, job_id, content, created_at)
                values (?, ?, ?, ?)
                """, UUID.randomUUID(), job.getId(), "{\"documentOverview\":\"duplicate\"}", Timestamp.from(Instant.now())))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void cleanupIndexesExist() {
        Integer shareExpiryIndex = indexCount("share_links", "idx_share_links_expires_at");
        Integer statusUpdatedIndex = indexCount("policy_jobs", "idx_policy_jobs_status_updated_at");
        Integer statusCreatedIndex = indexCount("policy_jobs", "idx_policy_jobs_status_created_at");

        assertThat(shareExpiryIndex).isEqualTo(1);
        assertThat(statusUpdatedIndex).isEqualTo(1);
        assertThat(statusCreatedIndex).isEqualTo(1);
    }

    private Integer indexCount(String tableName, String indexName) {
        return jdbcTemplate.queryForObject("""
                select count(*)
                from pg_indexes
                where schemaname = 'public'
                  and tablename = ?
                  and indexname = ?
                """, Integer.class, tableName, indexName);
    }
}
