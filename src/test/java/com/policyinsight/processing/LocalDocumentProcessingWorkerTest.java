package com.policyinsight.processing;

import com.policyinsight.shared.model.PolicyJob;
import com.policyinsight.shared.repository.PolicyJobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test for LocalDocumentProcessingWorker job claiming functionality.
 * Tests the repository method and the claim pattern used by the local worker.
 * Uses Testcontainers PostgreSQL for actual database testing.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Testcontainers
class LocalDocumentProcessingWorkerTest {

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
    }

    @Autowired
    private PolicyJobRepository policyJobRepository;

    private UUID jobUuid1;
    private UUID jobUuid2;

    @BeforeEach
    void setUp() {
        jobUuid1 = UUID.randomUUID();
        jobUuid2 = UUID.randomUUID();
    }

    @Test
    void testFindOldestPendingJobs() {
        // Given: Create multiple PENDING jobs
        PolicyJob job1 = new PolicyJob(jobUuid1);
        job1.setStatus("PENDING");
        job1.setPdfFilename("test1.pdf");
        policyJobRepository.save(job1);

        // Wait a bit to ensure different timestamps
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        PolicyJob job2 = new PolicyJob(jobUuid2);
        job2.setStatus("PENDING");
        job2.setPdfFilename("test2.pdf");
        policyJobRepository.save(job2);

        // When: Find oldest pending jobs
        List<PolicyJob> pendingJobs = policyJobRepository.findOldestPendingJobs(10);

        // Then: Should return jobs in order (oldest first)
        assertThat(pendingJobs).hasSize(2);
        assertThat(pendingJobs.get(0).getJobUuid()).isEqualTo(jobUuid1);
        assertThat(pendingJobs.get(1).getJobUuid()).isEqualTo(jobUuid2);
    }

    @Test
    @Transactional
    void testClaimJobPattern() {
        // Given: A PENDING job
        PolicyJob job = new PolicyJob(jobUuid1);
        job.setStatus("PENDING");
        job.setPdfFilename("test.pdf");
        policyJobRepository.save(job);

        // When: Claim the job using the same pattern as LocalDocumentProcessingWorker
        Optional<PolicyJob> jobOpt = policyJobRepository.findByJobUuid(jobUuid1);
        assertThat(jobOpt).isPresent();
        PolicyJob currentJob = jobOpt.get();

        // Only claim if still PENDING
        boolean canClaim = "PENDING".equals(currentJob.getStatus());
        if (canClaim) {
            currentJob.setStatus("PROCESSING");
            currentJob.setStartedAt(Instant.now());
            policyJobRepository.save(currentJob);
        }

        // Then: Should successfully claim
        assertThat(canClaim).isTrue();
        PolicyJob updatedJob = policyJobRepository.findByJobUuid(jobUuid1).orElseThrow();
        assertThat(updatedJob.getStatus()).isEqualTo("PROCESSING");
        assertThat(updatedJob.getStartedAt()).isNotNull();
    }

    @Test
    @Transactional
    void testClaimJobAlreadyProcessing() {
        // Given: A job already in PROCESSING status
        PolicyJob job = new PolicyJob(jobUuid1);
        job.setStatus("PROCESSING");
        job.setPdfFilename("test.pdf");
        policyJobRepository.save(job);

        // When: Try to claim the job using the same pattern
        Optional<PolicyJob> jobOpt = policyJobRepository.findByJobUuid(jobUuid1);
        assertThat(jobOpt).isPresent();
        PolicyJob currentJob = jobOpt.get();

        // Only claim if still PENDING
        boolean canClaim = "PENDING".equals(currentJob.getStatus());

        // Then: Should not claim (already processing)
        assertThat(canClaim).isFalse();
    }

    @Test
    void testClaimJobOnlyPending() {
        // Given: Multiple jobs with different statuses
        PolicyJob pendingJob = new PolicyJob(jobUuid1);
        pendingJob.setStatus("PENDING");
        pendingJob.setPdfFilename("pending.pdf");
        policyJobRepository.save(pendingJob);

        PolicyJob processingJob = new PolicyJob(jobUuid2);
        processingJob.setStatus("PROCESSING");
        processingJob.setPdfFilename("processing.pdf");
        policyJobRepository.save(processingJob);

        // When: Find oldest pending jobs
        List<PolicyJob> pendingJobs = policyJobRepository.findOldestPendingJobs(10);

        // Then: Should only return PENDING jobs
        assertThat(pendingJobs).hasSize(1);
        assertThat(pendingJobs.get(0).getStatus()).isEqualTo("PENDING");
        assertThat(pendingJobs.get(0).getJobUuid()).isEqualTo(jobUuid1);
    }
}

