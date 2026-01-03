package com.policyinsight.processing;

import com.policyinsight.shared.model.PolicyJob;
import com.policyinsight.shared.repository.PolicyJobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Scheduled service that recovers stuck PROCESSING jobs.
 * Finds jobs with expired leases and either resets them to PENDING (if attempts < max)
 * or marks them as FAILED (if attempts >= max).
 */
@Service
@ConditionalOnProperty(prefix = "policyinsight.worker", name = "enabled", havingValue = "true")
public class JobReaperService {

    private static final Logger logger = LoggerFactory.getLogger(JobReaperService.class);

    private final PolicyJobRepository policyJobRepository;

    @Value("${app.job.max-attempts:3}")
    private int maxAttempts;

    public JobReaperService(PolicyJobRepository policyJobRepository) {
        this.policyJobRepository = policyJobRepository;
    }

    /**
     * Runs every minute to find and recover stale PROCESSING jobs.
     */
    @Scheduled(fixedDelayString = "60000") // 1 minute in milliseconds
    @Transactional
    public void reapStaleJobs() {
        try {
            Instant now = Instant.now();
            List<PolicyJob> staleJobs = policyJobRepository.findStaleProcessingJobs(now);

            if (staleJobs.isEmpty()) {
                return; // No stale jobs
            }

            logger.info("Found {} stale PROCESSING job(s) with expired leases", staleJobs.size());

            for (PolicyJob job : staleJobs) {
                int attemptCount = job.getAttemptCount() != null ? job.getAttemptCount() : 0;

                if (attemptCount < maxAttempts) {
                    // Reset to PENDING for retry
                    job.setStatus("PENDING");
                    job.setLeaseExpiresAt(null); // Clear lease
                    job.setLastErrorCode(null); // Clear error code
                    policyJobRepository.save(job);
                    logger.info("Reset stale job {} to PENDING (attempt {}/{})",
                            job.getJobUuid(), attemptCount, maxAttempts);
                } else {
                    // Mark as FAILED - max attempts reached
                    job.setStatus("FAILED");
                    job.setLastErrorCode("LEASE_EXPIRED_MAX_ATTEMPTS");
                    job.setErrorMessage("Job processing lease expired after " + maxAttempts + " attempts");
                    job.setCompletedAt(Instant.now());
                    policyJobRepository.save(job);
                    logger.warn("Marked stale job {} as FAILED (attempts {}/{} exceeded)",
                            job.getJobUuid(), attemptCount, maxAttempts);
                }
            }
        } catch (Exception e) {
            logger.error("Error during job reaping", e);
        }
    }
}

