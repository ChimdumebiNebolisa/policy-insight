package com.policyinsight.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.policyinsight.config.CleanupProperties;
import com.policyinsight.model.DocumentChunk;
import com.policyinsight.model.JobStatus;
import com.policyinsight.model.PolicyJob;
import com.policyinsight.model.QaInteraction;
import com.policyinsight.model.Report;
import com.policyinsight.model.ShareLink;
import com.policyinsight.repository.DocumentChunkRepository;
import com.policyinsight.repository.PolicyJobRepository;
import com.policyinsight.repository.QaInteractionRepository;
import com.policyinsight.repository.ReportRepository;
import com.policyinsight.repository.ShareLinkRepository;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties = "app.cleanup.enabled=false")
class CleanupServiceTests {

    private static final Instant NOW = Instant.parse("2026-05-10T12:00:00Z");

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

    @Autowired
    PlatformTransactionManager transactionManager;

    @BeforeEach
    void cleanDatabase() {
        shareLinkRepository.deleteAll();
        policyJobRepository.deleteAll();
    }

    @Test
    void deletesExpiredShareLinksOnlyAfterRetentionWindow() {
        Report report = completedReport();
        ShareLink oldExpired = shareLinkRepository.save(new ShareLink(report, "old-expired", NOW.minusSeconds(60)));
        ShareLink freshExpired = shareLinkRepository.save(new ShareLink(report, "fresh-expired", NOW.plusSeconds(60)));

        CleanupResult result = cleanup(true, 0);

        assertThat(result.expiredShareLinksDeleted()).isEqualTo(1);
        assertThat(shareLinkRepository.findById(oldExpired.getId())).isEmpty();
        assertThat(shareLinkRepository.findById(freshExpired.getId())).isPresent();
        assertThat(reportRepository.findById(report.getId())).isPresent();
    }

    @Test
    void deletesOldFailedJobsThroughCascadeButPreservesDemoJobs() {
        PolicyJob failedJob = job(JobStatus.FAILED, null);
        Report failedReport = reportRepository.save(new Report(failedJob, "{\"documentOverview\":\"old\"}"));
        documentChunkRepository.save(new DocumentChunk(failedJob, 0, "old chunk"));
        qaInteractionRepository.save(new QaInteraction(failedReport, "old question", "{\"answer\":\"old\"}"));
        setJobTimestamps(failedJob, NOW.minus(10, java.time.temporal.ChronoUnit.DAYS));

        PolicyJob demoJob = job(JobStatus.FAILED, "fictional-demo");
        setJobTimestamps(demoJob, NOW.minus(10, java.time.temporal.ChronoUnit.DAYS));

        CleanupResult result = cleanup(true, 0);

        assertThat(result.failedJobsDeleted()).isEqualTo(1);
        assertThat(policyJobRepository.findById(failedJob.getId())).isEmpty();
        assertThat(reportRepository.findById(failedReport.getId())).isEmpty();
        assertThat(documentChunkRepository.findByJobIdOrderByChunkIndex(failedJob.getId())).isEmpty();
        assertThat(qaInteractionRepository.findByReportIdOrderByCreatedAtAsc(failedReport.getId())).isEmpty();
        assertThat(policyJobRepository.findById(demoJob.getId())).isPresent();
    }

    @Test
    void deletesOldCompletedJobsThroughCascade() {
        Report report = completedReport();
        documentChunkRepository.save(new DocumentChunk(report.getJob(), 0, "old completed chunk"));
        qaInteractionRepository.save(new QaInteraction(report, "question", "{\"answer\":\"answer\"}"));
        setJobTimestamps(report.getJob(), NOW.minus(45, java.time.temporal.ChronoUnit.DAYS));

        CleanupResult result = cleanup(true, 0);

        assertThat(result.completedJobsDeleted()).isEqualTo(1);
        assertThat(policyJobRepository.findById(report.getJob().getId())).isEmpty();
        assertThat(reportRepository.findById(report.getId())).isEmpty();
        assertThat(documentChunkRepository.findByJobIdOrderByChunkIndex(report.getJob().getId())).isEmpty();
        assertThat(qaInteractionRepository.findByReportIdOrderByCreatedAtAsc(report.getId())).isEmpty();
    }

    @Test
    void marksStaleProcessingJobsFailedWithoutDeletingThem() {
        PolicyJob processingJob = job(JobStatus.PROCESSING, null);
        setJobTimestamps(processingJob, NOW.minus(2, java.time.temporal.ChronoUnit.HOURS));

        CleanupResult result = cleanup(true, 0);

        PolicyJob updated = policyJobRepository.findById(processingJob.getId()).orElseThrow();
        assertThat(result.staleProcessingJobsMarkedFailed()).isEqualTo(1);
        assertThat(updated.getStatus()).isEqualTo(JobStatus.FAILED);
        assertThat(updated.getSafeErrorMessage()).contains("timed out");
    }

    @Test
    void disabledCleanupDoesNoWork() {
        Report report = completedReport();
        ShareLink expired = shareLinkRepository.save(new ShareLink(report, "expired", NOW.minusSeconds(60)));
        setJobTimestamps(report.getJob(), NOW.minus(45, java.time.temporal.ChronoUnit.DAYS));

        CleanupResult result = cleanup(false, 0);

        assertThat(result).isEqualTo(new CleanupResult(0, 0, 0, 0));
        assertThat(policyJobRepository.findById(report.getJob().getId())).isPresent();
        assertThat(shareLinkRepository.findById(expired.getId())).isPresent();
    }

    private CleanupService cleanupService(boolean enabled, long expiredShareRetentionDays) {
        CleanupProperties properties = new CleanupProperties(enabled, 30, 7, expiredShareRetentionDays, 30, 3600000);
        return new CleanupService(
                properties,
                policyJobRepository,
                shareLinkRepository,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    private CleanupResult cleanup(boolean enabled, long expiredShareRetentionDays) {
        return new TransactionTemplate(transactionManager)
                .execute(status -> cleanupService(enabled, expiredShareRetentionDays).cleanup());
    }

    private Report completedReport() {
        PolicyJob job = job(JobStatus.COMPLETED, null);
        return reportRepository.save(new Report(job, "{\"documentOverview\":\"overview\"}"));
    }

    private PolicyJob job(JobStatus status, String demoKey) {
        PolicyJob job = new PolicyJob("owner-hash", NOW.plusSeconds(3600));
        job.setStatus(status);
        job.setDemoKey(demoKey);
        return policyJobRepository.saveAndFlush(job);
    }

    private void setJobTimestamps(PolicyJob job, Instant instant) {
        jdbcTemplate.update(
                "update policy_jobs set created_at = ?, updated_at = ? where id = ?",
                Timestamp.from(instant),
                Timestamp.from(instant),
                job.getId()
        );
    }
}
