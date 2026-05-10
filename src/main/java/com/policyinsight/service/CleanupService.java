package com.policyinsight.service;

import com.policyinsight.config.CleanupProperties;
import com.policyinsight.model.JobStatus;
import com.policyinsight.model.PolicyJob;
import com.policyinsight.repository.PolicyJobRepository;
import com.policyinsight.repository.ShareLinkRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CleanupService {

    private static final Logger log = LoggerFactory.getLogger(CleanupService.class);
    private static final String STALE_JOB_MESSAGE = "Report generation timed out. Please upload the document again.";
    private static final List<JobStatus> IN_PROGRESS_STATUSES = List.of(
            JobStatus.UPLOADED,
            JobStatus.TEXT_EXTRACTED,
            JobStatus.BUILDING_AI_REPORT,
            JobStatus.VALIDATING_CITATIONS,
            JobStatus.PROCESSING
    );

    private final CleanupProperties properties;
    private final PolicyJobRepository policyJobRepository;
    private final ShareLinkRepository shareLinkRepository;
    private final Clock clock;

    @Autowired
    public CleanupService(
            CleanupProperties properties,
            PolicyJobRepository policyJobRepository,
            ShareLinkRepository shareLinkRepository
    ) {
        this(properties, policyJobRepository, shareLinkRepository, Clock.systemUTC());
    }

    CleanupService(
            CleanupProperties properties,
            PolicyJobRepository policyJobRepository,
            ShareLinkRepository shareLinkRepository,
            Clock clock
    ) {
        this.properties = properties;
        this.policyJobRepository = policyJobRepository;
        this.shareLinkRepository = shareLinkRepository;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${app.cleanup.fixed-delay-ms:3600000}")
    public void runScheduledCleanup() {
        CleanupResult result = cleanup();
        if (properties.enabled()) {
            log.info(
                    "Cleanup completed expiredShareLinksDeleted={} staleProcessingJobsMarkedFailed={} failedJobsDeleted={} completedJobsDeleted={}",
                    result.expiredShareLinksDeleted(),
                    result.staleProcessingJobsMarkedFailed(),
                    result.failedJobsDeleted(),
                    result.completedJobsDeleted()
            );
        }
    }

    @Transactional
    public CleanupResult cleanup() {
        if (!properties.enabled()) {
            return new CleanupResult(0, 0, 0, 0);
        }

        Instant now = Instant.now(clock);
        int expiredShareLinksDeleted = shareLinkRepository.deleteByExpiresAtBefore(
                now.minus(properties.retentionExpiredShareDays(), ChronoUnit.DAYS)
        );

        List<PolicyJob> oldFailedJobs = policyJobRepository.findByDemoKeyIsNullAndStatusAndUpdatedAtBefore(
                JobStatus.FAILED,
                now.minus(properties.retentionFailedDays(), ChronoUnit.DAYS)
        );
        policyJobRepository.deleteAll(oldFailedJobs);

        List<PolicyJob> staleProcessingJobs = policyJobRepository
                .findByDemoKeyIsNullAndStatusInAndUpdatedAtBefore(
                        IN_PROGRESS_STATUSES,
                        now.minus(properties.jobStaleMinutes(), ChronoUnit.MINUTES)
                );
        for (PolicyJob job : staleProcessingJobs) {
            job.setStatus(JobStatus.FAILED);
            job.setSafeErrorMessage(STALE_JOB_MESSAGE);
        }
        policyJobRepository.saveAll(staleProcessingJobs);

        List<PolicyJob> oldCompletedJobs = policyJobRepository.findByDemoKeyIsNullAndStatusAndCreatedAtBefore(
                JobStatus.COMPLETED,
                now.minus(properties.retentionCompletedDays(), ChronoUnit.DAYS)
        );
        policyJobRepository.deleteAll(oldCompletedJobs);

        return new CleanupResult(
                expiredShareLinksDeleted,
                staleProcessingJobs.size(),
                oldFailedJobs.size(),
                oldCompletedJobs.size()
        );
    }
}
