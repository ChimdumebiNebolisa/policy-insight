package com.policyinsight.processing;

import com.policyinsight.shared.model.PolicyJob;
import com.policyinsight.shared.repository.PolicyJobRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.List;
import java.util.UUID;

import jakarta.persistence.EntityManager;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(JobClaimService.class)
@Testcontainers
class JobClaimServiceTest {

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

    @Autowired
    private JobClaimService jobClaimService;

    @Autowired
    private EntityManager entityManager;

    @Test
    void findAndClaimPendingJobs_shouldClaimWithinTransaction() {
        PolicyJob job = new PolicyJob(UUID.randomUUID());
        job.setStatus("PENDING");
        job.setPdfFilename("test.pdf");
        policyJobRepository.save(job);

        List<PolicyJob> claimed = jobClaimService.findAndClaimPendingJobs(5);

        entityManager.flush();
        entityManager.clear();

        assertThat(claimed).hasSize(1);
        PolicyJob updated = policyJobRepository.findByJobUuid(job.getJobUuid()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo("PROCESSING");
        assertThat(updated.getLeaseExpiresAt()).isNotNull();
    }
}
