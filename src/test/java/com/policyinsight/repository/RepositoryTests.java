package com.policyinsight.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.policyinsight.model.DocumentChunk;
import com.policyinsight.model.PolicyJob;
import com.policyinsight.model.QaInteraction;
import com.policyinsight.model.Report;
import com.policyinsight.model.ShareLink;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
class RepositoryTests {

    @Autowired
    PolicyJobRepository policyJobRepository;

    @Autowired
    DocumentChunkRepository documentChunkRepository;

    @Autowired
    ReportRepository reportRepository;

    @Autowired
    ShareLinkRepository shareLinkRepository;

    @Autowired
    QaInteractionRepository qaInteractionRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void repositoriesPersistCoreRecords() {
        PolicyJob job = policyJobRepository.save(new PolicyJob("owner-hash", Instant.now().plus(1, ChronoUnit.HOURS)));
        DocumentChunk chunk = documentChunkRepository.save(new DocumentChunk(job, 0, "Payment terms are due in 30 days."));
        Report report = reportRepository.save(new Report(job, "{\"summaryBullets\":[]}"));
        ShareLink shareLink = shareLinkRepository.save(new ShareLink(report, "share-hash", Instant.now().plus(7, ChronoUnit.DAYS)));
        QaInteraction qa = qaInteractionRepository.save(new QaInteraction(report, "What is due?", "{\"answer\":\"Payment.\"}"));

        List<DocumentChunk> chunks = documentChunkRepository.findByJobIdOrderByChunkIndex(job.getId());

        assertThat(policyJobRepository.findById(job.getId())).isPresent();
        assertThat(chunks).extracting(DocumentChunk::getId).containsExactly(chunk.getId());
        assertThat(reportRepository.findByJobId(job.getId())).isPresent();
        assertThat(shareLinkRepository.findByTokenHashAndExpiresAtAfter("share-hash", Instant.now())).isPresent();
        assertThat(shareLink.getId()).isNotNull();
        assertThat(qaInteractionRepository.findByReportIdOrderByCreatedAtAsc(report.getId()))
                .extracting(QaInteraction::getId)
                .containsExactly(qa.getId());
    }

    @Test
    void hardeningMigrationAddsStatusConstraintAndShareExpiryIndex() {
        Integer statusConstraints = jdbcTemplate.queryForObject("""
                select count(*)
                from information_schema.table_constraints
                where lower(table_name) = 'policy_jobs'
                  and lower(constraint_name) = 'chk_policy_jobs_status'
                """, Integer.class);
        Integer expiryIndexes = jdbcTemplate.queryForObject("""
                select count(*)
                from information_schema.indexes
                where lower(table_name) = 'share_links'
                  and lower(index_name) = 'idx_share_links_expires_at'
                """, Integer.class);
        Integer cleanupUpdatedIndexes = jdbcTemplate.queryForObject("""
                select count(*)
                from information_schema.indexes
                where lower(table_name) = 'policy_jobs'
                  and lower(index_name) = 'idx_policy_jobs_status_updated_at'
                """, Integer.class);
        Integer cleanupCreatedIndexes = jdbcTemplate.queryForObject("""
                select count(*)
                from information_schema.indexes
                where lower(table_name) = 'policy_jobs'
                  and lower(index_name) = 'idx_policy_jobs_status_created_at'
                """, Integer.class);

        assertThat(statusConstraints).isEqualTo(1);
        assertThat(expiryIndexes).isEqualTo(1);
        assertThat(cleanupUpdatedIndexes).isEqualTo(1);
        assertThat(cleanupCreatedIndexes).isEqualTo(1);
    }
}
