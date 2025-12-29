package com.policyinsight.shared.repository;

import com.policyinsight.shared.model.PolicyJob;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for PolicyJobRepository.
 * Uses Testcontainers PostgreSQL for actual PostgreSQL type validation.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Testcontainers
class PolicyJobRepositoryTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
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
    private PolicyJobRepository repository;

    private UUID testJobUuid;

    @BeforeEach
    void setUp() {
        testJobUuid = UUID.randomUUID();
    }

    @Test
    void testSaveAndFindByJobUuid() {
        // Given
        PolicyJob job = new PolicyJob(testJobUuid);
        job.setStatus("PENDING");
        job.setPdfFilename("test.pdf");

        // When
        PolicyJob saved = repository.save(job);
        Optional<PolicyJob> found = repository.findByJobUuid(testJobUuid);

        // Then
        assertThat(saved.getId()).isNotNull();
        assertThat(found).isPresent();
        assertThat(found.get().getJobUuid()).isEqualTo(testJobUuid);
        assertThat(found.get().getStatus()).isEqualTo("PENDING");
        assertThat(found.get().getPdfFilename()).isEqualTo("test.pdf");
    }

    @Test
    void testExistsByJobUuid() {
        // Given
        PolicyJob job = new PolicyJob(testJobUuid);
        repository.save(job);

        // When
        boolean exists = repository.existsByJobUuid(testJobUuid);
        boolean notExists = repository.existsByJobUuid(UUID.randomUUID());

        // Then
        assertThat(exists).isTrue();
        assertThat(notExists).isFalse();
    }

    @Test
    void testFindByStatusOrderByCreatedAtDesc() {
        // Given
        PolicyJob job1 = new PolicyJob(UUID.randomUUID());
        job1.setStatus("PENDING");
        repository.save(job1);

        PolicyJob job2 = new PolicyJob(UUID.randomUUID());
        job2.setStatus("PROCESSING");
        repository.save(job2);

        PolicyJob job3 = new PolicyJob(UUID.randomUUID());
        job3.setStatus("PENDING");
        repository.save(job3);

        // When
        var pendingJobs = repository.findByStatusOrderByCreatedAtDesc("PENDING");

        // Then
        assertThat(pendingJobs).hasSize(2);
        assertThat(pendingJobs.get(0).getCreatedAt())
            .isAfterOrEqualTo(pendingJobs.get(1).getCreatedAt());
    }
}

