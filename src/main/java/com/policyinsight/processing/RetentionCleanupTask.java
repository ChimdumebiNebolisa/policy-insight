package com.policyinsight.processing;

import com.policyinsight.shared.repository.DocumentChunkRepository;
import com.policyinsight.shared.repository.PolicyJobRepository;
import com.policyinsight.shared.repository.QaInteractionRepository;
import com.policyinsight.shared.repository.ReportRepository;
import com.policyinsight.shared.repository.ShareLinkRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Scheduled task to clean up old jobs, chunks, reports, and Q&A interactions.
 * Runs alongside the job reaper to maintain data retention policy.
 * Only loads when policyinsight.worker.enabled=true.
 */
@Service
@ConditionalOnProperty(prefix = "policyinsight.worker", name = "enabled", havingValue = "true")
public class RetentionCleanupTask {

    private static final Logger logger = LoggerFactory.getLogger(RetentionCleanupTask.class);

    private final PolicyJobRepository policyJobRepository;
    private final DocumentChunkRepository documentChunkRepository;
    private final ReportRepository reportRepository;
    private final QaInteractionRepository qaInteractionRepository;
    private final ShareLinkRepository shareLinkRepository;
    private final int retentionDays;

    public RetentionCleanupTask(
            PolicyJobRepository policyJobRepository,
            DocumentChunkRepository documentChunkRepository,
            ReportRepository reportRepository,
            QaInteractionRepository qaInteractionRepository,
            ShareLinkRepository shareLinkRepository,
            @Value("${app.retention.days:30}") int retentionDays) {
        this.policyJobRepository = policyJobRepository;
        this.documentChunkRepository = documentChunkRepository;
        this.reportRepository = reportRepository;
        this.qaInteractionRepository = qaInteractionRepository;
        this.shareLinkRepository = shareLinkRepository;
        this.retentionDays = retentionDays;
        logger.info("RetentionCleanupTask initialized: retentionDays={}", retentionDays);
    }

    /**
     * Runs every hour to clean up data older than retention period.
     * Deletes jobs, chunks, reports, Q&A interactions, and expired share links.
     */
    @Scheduled(fixedDelayString = "3600000") // Every hour (3600000 ms)
    @Transactional
    public void cleanupOldData() {
        Instant cutoffDate = Instant.now().minus(retentionDays, java.time.temporal.ChronoUnit.DAYS);
        logger.info("Starting retention cleanup: deleting data older than {} days (cutoff: {})",
                retentionDays, cutoffDate);

        try {
            // Delete old jobs (cascades to chunks, reports, Q&A interactions via foreign keys)
            // Note: We need to delete in order due to foreign key constraints
            int deletedChunks = documentChunkRepository.deleteByJobUuidIn(
                    policyJobRepository.findJobUuidsOlderThan(cutoffDate));
            logger.info("Deleted {} chunks for old jobs", deletedChunks);

            int deletedReports = reportRepository.deleteByJobUuidIn(
                    policyJobRepository.findJobUuidsOlderThan(cutoffDate));
            logger.info("Deleted {} reports for old jobs", deletedReports);

            int deletedQaInteractions = qaInteractionRepository.deleteByJobUuidIn(
                    policyJobRepository.findJobUuidsOlderThan(cutoffDate));
            logger.info("Deleted {} Q&A interactions for old jobs", deletedQaInteractions);

            int deletedShareLinks = shareLinkRepository.deleteByJobUuidIn(
                    policyJobRepository.findJobUuidsOlderThan(cutoffDate));
            logger.info("Deleted {} share links for old jobs", deletedShareLinks);

            int deletedJobs = policyJobRepository.deleteByCreatedAtBefore(cutoffDate);
            logger.info("Deleted {} jobs older than {} days", deletedJobs, retentionDays);

            logger.info("Retention cleanup completed successfully");
        } catch (Exception e) {
            logger.error("Error during retention cleanup", e);
        }
    }
}

