package com.policyinsight.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.policyinsight.TestPdfFactory;
import com.policyinsight.shared.model.DocumentChunk;
import com.policyinsight.shared.model.PolicyJob;
import com.policyinsight.shared.repository.DocumentChunkRepository;
import com.policyinsight.shared.repository.PolicyJobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.junit.jupiter.api.AfterEach;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
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

    // Per-test-run marker to isolate cleanup to this test run only
    private static final String TEST_RUN_ID = "it-" + UUID.randomUUID().toString().substring(0, 8);

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
        registry.add("app.local-worker.poll-ms", () -> "200"); // Faster polling for tests
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PolicyJobRepository policyJobRepository;

    @Autowired
    private DocumentChunkRepository documentChunkRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID testJobId;
    private ObjectMapper objectMapper;
    private Instant testStart;

    @BeforeEach
    void setUp() {
        testJobId = UUID.randomUUID();
        objectMapper = new ObjectMapper();
        testStart = Instant.now();
    }

    @AfterEach
    void tearDown() {
        // Clean up test data in FK-safe order, scoped to this test run only
        // Delete in order: qa_interactions -> share_links -> reports -> document_chunks -> policy_jobs
        // Use both timestamp (created_at >= testStart) AND filename pattern (pdf_filename LIKE TEST_RUN_ID%)
        // to ensure we only delete rows created by this test run, not other tests or parallel runs
        String filenamePattern = TEST_RUN_ID + "%";
        jdbcTemplate.update(
                "DELETE FROM qa_interactions WHERE job_uuid IN " +
                        "(SELECT job_uuid FROM policy_jobs WHERE created_at >= ? AND pdf_filename LIKE ?)",
                java.sql.Timestamp.from(testStart), filenamePattern);
        jdbcTemplate.update(
                "DELETE FROM share_links WHERE job_uuid IN " +
                        "(SELECT job_uuid FROM policy_jobs WHERE created_at >= ? AND pdf_filename LIKE ?)",
                java.sql.Timestamp.from(testStart), filenamePattern);
        jdbcTemplate.update(
                "DELETE FROM reports WHERE job_uuid IN " +
                        "(SELECT job_uuid FROM policy_jobs WHERE created_at >= ? AND pdf_filename LIKE ?)",
                java.sql.Timestamp.from(testStart), filenamePattern);
        jdbcTemplate.update(
                "DELETE FROM document_chunks WHERE job_uuid IN " +
                        "(SELECT job_uuid FROM policy_jobs WHERE created_at >= ? AND pdf_filename LIKE ?)",
                java.sql.Timestamp.from(testStart), filenamePattern);
        jdbcTemplate.update(
                "DELETE FROM policy_jobs WHERE created_at >= ? AND pdf_filename LIKE ?",
                java.sql.Timestamp.from(testStart), filenamePattern);
    }

    @Test
    void testLocalProcessingPathWithValidPdf() throws Exception {
        // Given: Generate a valid minimal PDF with sentinel text
        // Text must be at least 200 chars to meet TextChunkerService.MIN_CHUNK_SIZE_CHARS requirement
        String sentinelText = "POLICYINSIGHT_TEST_SENTINEL";
        String longText = sentinelText + " " +
                "This is a test document for PolicyInsight integration testing. " +
                "The text must be long enough to create at least one chunk. " +
                "The minimum chunk size is 200 characters according to TextChunkerService. " +
                "This paragraph provides additional content to ensure the chunker creates chunks. " +
                "The sentinel text should be extractable and verifiable in the test assertions.";
        byte[] pdfBytes = TestPdfFactory.minimalPdfBytes(longText);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                TEST_RUN_ID + "-valid.pdf",
                "application/pdf",
                pdfBytes
        );

        // When: Upload the PDF via the API endpoint
        MvcResult uploadResult = mockMvc.perform(multipart("/api/documents/upload")
                        .file(file))
                .andExpect(status().isAccepted())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.jobId").exists())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn();

        // Extract jobId from response
        String responseJson = uploadResult.getResponse().getContentAsString();
        @SuppressWarnings("unchecked")
        Map<String, Object> response = objectMapper.readValue(responseJson, Map.class);
        String jobIdString = (String) response.get("jobId");
        UUID jobId = UUID.fromString(jobIdString);

        // Poll status endpoint until SUCCESS (with timeout)
        String finalStatus = pollStatusUntilComplete(jobId, 30);

        // Then: Assert job completed successfully
        PolicyJob finalJob = policyJobRepository.findByJobUuid(jobId).orElseThrow();
        assertThat(finalStatus).isEqualTo("SUCCESS");
        assertThat(finalJob.getStatus()).isEqualTo("SUCCESS");
        assertThat(finalJob.getErrorMessage()).isNull();
        assertThat(finalJob.getStartedAt()).isNotNull();
        assertThat(finalJob.getCompletedAt()).isNotNull();

        // Assert extracted chunks exist
        long chunkCount = documentChunkRepository.countByJobUuid(jobId);
        assertThat(chunkCount).isGreaterThan(0);

        // Assert extracted text contains the sentinel
        java.util.List<DocumentChunk> chunks = documentChunkRepository.findByJobUuidOrderByChunkIndex(jobId);
        String allExtractedText = chunks.stream()
                .map(DocumentChunk::getText)
                .filter(text -> text != null)
                .reduce("", (a, b) -> a + " " + b);
        assertThat(allExtractedText).contains(sentinelText);
    }

    @Test
    void testLocalProcessingPathWithInvalidPdf() throws Exception {
        // Given: Create an invalid PDF (just the PDF header, not a complete file)
        byte[] invalidPdfBytes = "%PDF-".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                TEST_RUN_ID + "-invalid.pdf",
                "application/pdf",
                invalidPdfBytes
        );

        // When: Upload the invalid PDF via the API endpoint
        MvcResult uploadResult = mockMvc.perform(multipart("/api/documents/upload")
                        .file(file))
                .andExpect(status().isAccepted())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.jobId").exists())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn();

        // Extract jobId from response
        String responseJson = uploadResult.getResponse().getContentAsString();
        @SuppressWarnings("unchecked")
        Map<String, Object> response = objectMapper.readValue(responseJson, Map.class);
        String jobIdString = (String) response.get("jobId");
        UUID jobId = UUID.fromString(jobIdString);

        // Poll status endpoint until FAILED (with timeout)
        String finalStatus = pollStatusUntilComplete(jobId, 30);

        // Then: Assert job failed with non-empty error message
        // Note: We only assert FAILED status and non-empty error message, not specific keywords,
        // because PDFBox error messages vary across versions and file types
        PolicyJob finalJob = policyJobRepository.findByJobUuid(jobId).orElseThrow();
        assertThat(finalStatus).isEqualTo("FAILED");
        assertThat(finalJob.getStatus()).isEqualTo("FAILED");
        assertThat(finalJob.getErrorMessage()).isNotNull();
        assertThat(finalJob.getErrorMessage().trim()).isNotEmpty();
        assertThat(finalJob.getCompletedAt()).isNotNull();
    }

    /**
     * Polls the status endpoint until the job reaches a final state (SUCCESS or FAILED).
     *
     * @param jobId the job UUID to poll
     * @param timeoutSeconds maximum time to wait in seconds
     * @return the final status (SUCCESS or FAILED)
     * @throws Exception if polling fails or timeout is exceeded
     */
    private String pollStatusUntilComplete(UUID jobId, int timeoutSeconds) throws Exception {
        long startTime = System.currentTimeMillis();
        long timeoutMs = TimeUnit.SECONDS.toMillis(timeoutSeconds);
        String status = null;

        while (System.currentTimeMillis() - startTime < timeoutMs) {
            MvcResult statusResult = mockMvc.perform(get("/api/documents/{id}/status", jobId))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andReturn();

            String statusJson = statusResult.getResponse().getContentAsString();
            @SuppressWarnings("unchecked")
            Map<String, Object> statusMap = objectMapper.readValue(statusJson, Map.class);
            status = (String) statusMap.get("status");

            if ("SUCCESS".equals(status) || "FAILED".equals(status)) {
                return status;
            }

            // Wait 200ms before next poll (faster for tests)
            Thread.sleep(200);
        }

        throw new AssertionError(
                String.format("Job %s did not reach final state within %d seconds. Last status: %s",
                        jobId, timeoutSeconds, status));
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

