package com.policyinsight.processing;

import com.policyinsight.shared.model.PolicyJob;
import com.policyinsight.shared.repository.PolicyJobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Claims pending jobs inside a single transaction.
 * This is a separate bean to avoid @Transactional self-invocation.
 */
@Service
public class JobClaimService {

    private static final Logger logger = LoggerFactory.getLogger(JobClaimService.class);

    private final PolicyJobRepository policyJobRepository;

    @Value("${app.job.lease-duration-minutes:30}")
    private int leaseDurationMinutes;

    public JobClaimService(PolicyJobRepository policyJobRepository) {
        this.policyJobRepository = policyJobRepository;
    }

    /**
     * Finds and claims pending jobs atomically within a single transaction.
     */
    @Transactional
    public List<PolicyJob> findAndClaimPendingJobs(int batchSize) {
        List<PolicyJob> pendingJobs = policyJobRepository.findOldestPendingJobsForUpdate(batchSize);
        if (pendingJobs.isEmpty()) {
            return List.of();
        }

        logger.debug("Found {} pending job(s) to claim", pendingJobs.size());

        return pendingJobs.stream()
                .filter(this::claimJobInternal)
                .toList();
    }

    private boolean claimJobInternal(PolicyJob job) {
        Instant leaseExpiresAt = Instant.now().plus(leaseDurationMinutes, ChronoUnit.MINUTES);
        int updatedRows = policyJobRepository.updateStatusIfPendingWithLease(
                job.getJobUuid(),
                leaseExpiresAt
        );
        if (updatedRows == 0) {
            logger.debug("Could not claim job {} (already claimed or not PENDING)",
                    job.getJobUuid());
            return false;
        }

        logger.debug("Successfully claimed job: {} with lease expiring at {}",
                job.getJobUuid(), leaseExpiresAt);
        return true;
    }
}
