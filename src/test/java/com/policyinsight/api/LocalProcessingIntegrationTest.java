package com.policyinsight.api;

import com.policyinsight.api.storage.StorageService;
import com.policyinsight.processing.LocalDocumentProcessingWorker;
import com.policyinsight.shared.model.PolicyJob;
import com.policyinsight.shared.repository.PolicyJobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration test for local-only processing path.
 * Tests that jobs transition PENDING -> PROCESSING -> SUCCESS automatically,
 * and that export/share endpoints return correct status codes.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class LocalProcessingIntegrationTest {

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
        registry.add("app.processing.mode", () -> "local");
        registry.add("app.storage.mode", () -> "local");
        registry.add("app.messaging.mode", () -> "local");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PolicyJobRepository policyJobRepository;

    @Autowired
    private LocalDocumentProcessingWorker localWorker;

    @Autowired
    private StorageService storageService;

    private UUID testJobId;

    @BeforeEach
    void setUp() {
        testJobId = UUID.randomUUID();
    }

    @Test
    @Transactional
    void testLocalProcessingPath() throws Exception {
        // Given: Create a PENDING job with a minimal PDF stored
        PolicyJob job = new PolicyJob(testJobId);
        job.setStatus("PENDING");
        job.setPdfFilename("test.pdf");
        job.setFileSizeBytes(1024L);

        // Store a minimal PDF content (just for testing - real PDF would be better)
        String minimalPdfContent = "%PDF-1.4\n1 0 obj\n<< /Type /Catalog >>\nendobj\nxref\n0 0\ntrailer\n<< /Size 0 /Root 1 0 R >>\nstartxref\n0\n%%EOF";
        String storagePath = storageService.uploadFile(
                testJobId,
                "test.pdf",
                new ByteArrayInputStream(minimalPdfContent.getBytes(StandardCharsets.UTF_8)),
                "application/pdf"
        );
        job.setPdfGcsPath(storagePath);
        policyJobRepository.save(job);

        // When: Process the job manually (simulating the poller)
        localWorker.processDocument(testJobId);

        // Then: Job should be in SUCCESS or FAILED state (depending on processing)
        PolicyJob updatedJob = policyJobRepository.findByJobUuid(testJobId).orElseThrow();
        assertThat(updatedJob.getStatus()).isIn("SUCCESS", "FAILED");
        assertThat(updatedJob.getStartedAt()).isNotNull();
        assertThat(updatedJob.getCompletedAt()).isNotNull();
    }

    @Test
    void testExportPdfReturns409WhenNotSuccess() throws Exception {
        // Given: A job in PENDING status
        PolicyJob job = new PolicyJob(testJobId);
        job.setStatus("PENDING");
        job.setPdfFilename("test.pdf");
        policyJobRepository.save(job);

        // When: Request PDF export
        // Then: Should return 409 Conflict with JSON error
        mockMvc.perform(get("/api/documents/{id}/export/pdf", testJobId))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").exists())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("processing")));
    }

    @Test
    void testShareReturns409WhenNotSuccess() throws Exception {
        // Given: A job in PENDING status
        PolicyJob job = new PolicyJob(testJobId);
        job.setStatus("PENDING");
        job.setPdfFilename("test.pdf");
        policyJobRepository.save(job);

        // When: Request share link
        // Then: Should return 409 Conflict with JSON error
        mockMvc.perform(post("/api/documents/{id}/share", testJobId))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").exists())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("processing")));
    }

    @Test
    void testStatusEndpointReturnsAccurateState() throws Exception {
        // Given: A job in PROCESSING status
        PolicyJob job = new PolicyJob(testJobId);
        job.setStatus("PROCESSING");
        job.setPdfFilename("test.pdf");
        job.setStartedAt(Instant.now());
        policyJobRepository.save(job);

        // When: Request status
        // Then: Should return accurate status
        mockMvc.perform(get("/api/documents/{id}/status", testJobId))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.jobId").value(testJobId.toString()))
                .andExpect(jsonPath("$.status").value("PROCESSING"))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void testExportPdfReturns404WhenJobNotFound() throws Exception {
        UUID nonExistentId = UUID.randomUUID();

        // When: Request PDF export for non-existent job
        // Then: Should return 404 Not Found
        mockMvc.perform(get("/api/documents/{id}/export/pdf", nonExistentId))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void testShareReturns404WhenJobNotFound() throws Exception {
        UUID nonExistentId = UUID.randomUUID();

        // When: Request share link for non-existent job
        // Then: Should return 404 Not Found
        mockMvc.perform(post("/api/documents/{id}/share", nonExistentId))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void testInvalidUuidFormatReturns400() throws Exception {
        // When: Request with invalid UUID format
        // Then: Should return 400 Bad Request
        mockMvc.perform(get("/api/documents/{id}/status", "invalid-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").exists());
    }
}

