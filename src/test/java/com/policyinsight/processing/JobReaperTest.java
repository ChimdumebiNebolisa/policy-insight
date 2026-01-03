package com.policyinsight.processing;

import com.policyinsight.shared.model.PolicyJob;
import com.policyinsight.shared.repository.PolicyJobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for job reaper service that recovers stuck PROCESSING jobs.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class JobReaperTest {

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:15-alpine")
            .withDatabaseName("policyinsight_test")
            .withUsername("postgres")
            .withPassword("postgres");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("app.job.lease-duration-minutes", () -> "30");
        registry.add("app.job.max-attempts", () -> "3");
        registry.add("policyinsight.worker.enabled", () -> "true");
    }

    @Autowired
    private PolicyJobRepository policyJobRepository;

    @Autowired
    private JobReaperService jobReaperService;

    @BeforeEach
    void setUp() {
        // Clean up test data
        policyJobRepository.deleteAll();
    }

    @Test
    void testReaperResetsStaleJobWithAttemptsBelowMax() {
        // Given: A PROCESSING job with expired lease and attempt_count < max
        UUID jobId = UUID.randomUUID();
        PolicyJob job = new PolicyJob(jobId);
        job.setStatus("PROCESSING");
        job.setPdfFilename("test.pdf");
        job.setLeaseExpiresAt(Instant.now().minus(1, java.time.temporal.ChronoUnit.HOURS)); // Expired
        job.setAttemptCount(1); // Below max (3)
        policyJobRepository.save(job);

        // When: Reaper runs
        jobReaperService.reapStaleJobs();

        // Then: Job should be reset to PENDING
        PolicyJob updatedJob = policyJobRepository.findByJobUuid(jobId).orElseThrow();
        assertThat(updatedJob.getStatus()).isEqualTo("PENDING");
        assertThat(updatedJob.getLeaseExpiresAt()).isNull();
        assertThat(updatedJob.getLastErrorCode()).isNull();
    }

    @Test
    void testReaperMarksStaleJobAsFailedWhenMaxAttemptsReached() {
        // Given: A PROCESSING job with expired lease and attempt_count >= max
        UUID jobId = UUID.randomUUID();
        PolicyJob job = new PolicyJob(jobId);
        job.setStatus("PROCESSING");
        job.setPdfFilename("test.pdf");
        job.setLeaseExpiresAt(Instant.now().minus(1, java.time.temporal.ChronoUnit.HOURS)); // Expired
        job.setAttemptCount(3); // At max
        policyJobRepository.save(job);

        // When: Reaper runs
        jobReaperService.reapStaleJobs();

        // Then: Job should be marked as FAILED
        PolicyJob updatedJob = policyJobRepository.findByJobUuid(jobId).orElseThrow();
        assertThat(updatedJob.getStatus()).isEqualTo("FAILED");
        assertThat(updatedJob.getLastErrorCode()).isEqualTo("LEASE_EXPIRED_MAX_ATTEMPTS");
        assertThat(updatedJob.getErrorMessage()).isNotNull();
        assertThat(updatedJob.getCompletedAt()).isNotNull();
    }

    @Test
    void testReaperIgnoresJobsWithValidLease() {
        // Given: A PROCESSING job with valid (non-expired) lease
        UUID jobId = UUID.randomUUID();
        PolicyJob job = new PolicyJob(jobId);
        job.setStatus("PROCESSING");
        job.setPdfFilename("test.pdf");
        job.setLeaseExpiresAt(Instant.now().plus(1, java.time.temporal.ChronoUnit.HOURS)); // Not expired
        job.setAttemptCount(1);
        policyJobRepository.save(job);

        // When: Reaper runs
        jobReaperService.reapStaleJobs();

        // Then: Job should remain PROCESSING
        PolicyJob updatedJob = policyJobRepository.findByJobUuid(jobId).orElseThrow();
        assertThat(updatedJob.getStatus()).isEqualTo("PROCESSING");
        assertThat(updatedJob.getLeaseExpiresAt()).isNotNull();
    }
}

