package com.policyinsight.processing;

import com.policyinsight.shared.model.DocumentChunk;
import com.policyinsight.shared.model.PolicyJob;
import com.policyinsight.shared.repository.DocumentChunkRepository;
import com.policyinsight.shared.repository.PolicyJobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for idempotent chunk writes.
 * Verifies that retrying chunk insertion (e.g., after a job retry) does not create duplicates.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class IdempotentChunkTest {

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
        registry.add("policyinsight.worker.enabled", () -> "true");
    }

    @Autowired
    private PolicyJobRepository policyJobRepository;

    @Autowired
    private DocumentChunkRepository documentChunkRepository;

    private UUID testJobId;

    @BeforeEach
    void setUp() {
        // Clean up test data
        documentChunkRepository.deleteAll();
        policyJobRepository.deleteAll();

        // Create a test job
        testJobId = UUID.randomUUID();
        PolicyJob job = new PolicyJob(testJobId);
        job.setStatus("PROCESSING");
        job.setPdfFilename("test.pdf");
        policyJobRepository.save(job);
    }

    @Test
    @Transactional
    void testChunkInsertionIsIdempotent() {
        // Given: Insert chunks for a job
        DocumentChunk chunk1 = new DocumentChunk(testJobId);
        chunk1.setChunkIndex(0);
        chunk1.setText("First chunk text");
        chunk1.setPageNumber(1);
        documentChunkRepository.save(chunk1);

        DocumentChunk chunk2 = new DocumentChunk(testJobId);
        chunk2.setChunkIndex(1);
        chunk2.setText("Second chunk text");
        chunk2.setPageNumber(1);
        documentChunkRepository.save(chunk2);

        // Verify initial state
        long initialCount = documentChunkRepository.countByJobUuid(testJobId);
        assertThat(initialCount).isEqualTo(2);

        // When: Delete existing chunks and re-insert (simulating retry)
        documentChunkRepository.deleteByJobUuid(testJobId);

        // Re-insert chunks with same job_uuid and chunk_index
        DocumentChunk chunk1Retry = new DocumentChunk(testJobId);
        chunk1Retry.setChunkIndex(0);
        chunk1Retry.setText("First chunk text (retry)");
        chunk1Retry.setPageNumber(1);
        documentChunkRepository.save(chunk1Retry);

        DocumentChunk chunk2Retry = new DocumentChunk(testJobId);
        chunk2Retry.setChunkIndex(1);
        chunk2Retry.setText("Second chunk text (retry)");
        chunk2Retry.setPageNumber(1);
        documentChunkRepository.save(chunk2Retry);

        // Then: Should have exactly 2 chunks (no duplicates)
        long finalCount = documentChunkRepository.countByJobUuid(testJobId);
        assertThat(finalCount).isEqualTo(2);

        // Verify chunk indices are unique
        var chunks = documentChunkRepository.findByJobUuidOrderByChunkIndex(testJobId);
        assertThat(chunks).hasSize(2);
        assertThat(chunks.get(0).getChunkIndex()).isEqualTo(0);
        assertThat(chunks.get(1).getChunkIndex()).isEqualTo(1);
    }

    @Test
    @Transactional
    void testUniqueConstraintPreventsDuplicateChunks() {
        // Given: Insert a chunk
        DocumentChunk chunk1 = new DocumentChunk(testJobId);
        chunk1.setChunkIndex(0);
        chunk1.setText("First chunk");
        chunk1.setPageNumber(1);
        documentChunkRepository.save(chunk1);
        documentChunkRepository.flush(); // Ensure it's persisted

        // When: Try to insert another chunk with same job_uuid and chunk_index
        DocumentChunk chunk2 = new DocumentChunk(testJobId);
        chunk2.setChunkIndex(0); // Same chunk_index
        chunk2.setText("Duplicate chunk");
        chunk2.setPageNumber(1);

        // Then: Should fail due to unique constraint
        assertThatThrownBy(() -> {
            documentChunkRepository.save(chunk2);
            documentChunkRepository.flush(); // Force constraint check
        })
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class)
                .hasMessageContaining("uk_document_chunks_job_uuid_chunk_index");

        // Verify only one chunk exists
        long count = documentChunkRepository.countByJobUuid(testJobId);
        assertThat(count).isEqualTo(1);
    }
}

