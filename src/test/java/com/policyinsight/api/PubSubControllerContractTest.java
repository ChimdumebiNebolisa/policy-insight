package com.policyinsight.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.policyinsight.processing.DocumentJobProcessor;
import com.policyinsight.shared.model.PolicyJob;
import com.policyinsight.shared.repository.PolicyJobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contract test for Pub/Sub push endpoint /internal/pubsub.
 *
 * Validates that the endpoint correctly handles the Pub/Sub push message envelope format
 * and enforces idempotency through atomic status transitions.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class PubSubControllerContractTest {

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
        // Disable OIDC verification in test profile
        registry.add("pubsub.push.verification.enabled", () -> "false");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PolicyJobRepository policyJobRepository;

    @MockBean
    private DocumentJobProcessor documentJobProcessor;

    private UUID testJobId;
    private String testRequestId;
    private String testGcsPath;

    @BeforeEach
    void setUp() {
        testJobId = UUID.randomUUID();
        testRequestId = UUID.randomUUID().toString();
        testGcsPath = "gs://bucket/jobs/" + testJobId + "/document.pdf";
    }

    /**
     * Test that a valid Pub/Sub push envelope returns 2xx and invokes processor exactly once.
     */
    @Test
    void testValidPubSubPushEnvelope() throws Exception {
        // Create a PENDING job in database
        PolicyJob job = new PolicyJob(testJobId);
        job.setStatus("PENDING");
        job.setPdfGcsPath(testGcsPath);
        policyJobRepository.save(job);

        // Build Pub/Sub push envelope
        String payloadJson = String.format("{\"job_id\":\"%s\",\"gcs_path\":\"%s\"}", testJobId, testGcsPath);
        String base64Data = Base64.getEncoder().encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8));

        String pubsubMessageId = "test-message-id-" + UUID.randomUUID();
        String publishTime = "2025-12-31T12:00:00Z";
        String subscription = "projects/policy-insight/subscriptions/policyinsight-analysis-sub";

        String requestBody = String.format(
                "{\n" +
                "  \"message\": {\n" +
                "    \"data\": \"%s\",\n" +
                "    \"messageId\": \"%s\",\n" +
                "    \"publishTime\": \"%s\",\n" +
                "    \"attributes\": {\n" +
                "      \"job_id\": \"%s\",\n" +
                "      \"request_id\": \"%s\",\n" +
                "      \"action\": \"ANALYZE\"\n" +
                "    }\n" +
                "  },\n" +
                "  \"subscription\": \"%s\"\n" +
                "}",
                base64Data, pubsubMessageId, publishTime, testJobId, testRequestId, subscription);

        // Send POST request
        mockMvc.perform(post("/internal/pubsub")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isNoContent());

        // Verify processor was invoked exactly once
        verify(documentJobProcessor, times(1)).processDocument(testJobId);
    }

    /**
     * Test that invalid UUID format returns 4xx (not 5xx).
     */
    @Test
    void testInvalidUuidFormatReturns4xx() throws Exception {
        String invalidJobId = "not-a-uuid";
        String payloadJson = String.format("{\"job_id\":\"%s\",\"gcs_path\":\"%s\"}", invalidJobId, testGcsPath);
        String base64Data = Base64.getEncoder().encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8));

        String requestBody = String.format(
                "{\n" +
                "  \"message\": {\n" +
                "    \"data\": \"%s\",\n" +
                "    \"messageId\": \"test-message-id\",\n" +
                "    \"attributes\": {\n" +
                "      \"job_id\": \"%s\"\n" +
                "    }\n" +
                "  }\n" +
                "}",
                base64Data, invalidJobId);

        mockMvc.perform(post("/internal/pubsub")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest());

        // Verify processor was never invoked
        verify(documentJobProcessor, never()).processDocument(any());
    }

    /**
     * Test that missing required fields returns 4xx.
     */
    @Test
    void testMissingRequiredFieldsReturns4xx() throws Exception {
        // Missing job_id in both attributes and payload
        String payloadJson = "{\"gcs_path\":\"" + testGcsPath + "\"}";
        String base64Data = Base64.getEncoder().encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8));

        String requestBody = String.format(
                "{\n" +
                "  \"message\": {\n" +
                "    \"data\": \"%s\",\n" +
                "    \"messageId\": \"test-message-id\"\n" +
                "  }\n" +
                "}",
                base64Data);

        mockMvc.perform(post("/internal/pubsub")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest());

        // Verify processor was never invoked
        verify(documentJobProcessor, never()).processDocument(any());
    }

    /**
     * Test that duplicate pushes for the same job are idempotent (processor not invoked twice).
     */
    @Test
    void testDuplicatePushIsIdempotent() throws Exception {
        // Create a job that's already PROCESSING
        PolicyJob job = new PolicyJob(testJobId);
        job.setStatus("PROCESSING");
        job.setPdfGcsPath(testGcsPath);
        policyJobRepository.save(job);

        String payloadJson = String.format("{\"job_id\":\"%s\",\"gcs_path\":\"%s\"}", testJobId, testGcsPath);
        String base64Data = Base64.getEncoder().encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8));

        String requestBody = String.format(
                "{\n" +
                "  \"message\": {\n" +
                "    \"data\": \"%s\",\n" +
                "    \"messageId\": \"test-message-id\",\n" +
                "    \"attributes\": {\n" +
                "      \"job_id\": \"%s\",\n" +
                "      \"request_id\": \"%s\"\n" +
                "    }\n" +
                "  }\n" +
                "}",
                base64Data, testJobId, testRequestId);

        // First push should be skipped (job not PENDING)
        mockMvc.perform(post("/internal/pubsub")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isNoContent());

        // Verify processor was never invoked (job was not PENDING)
        verify(documentJobProcessor, never()).processDocument(any());
    }

    /**
     * Test that invalid base64 data returns 4xx.
     */
    @Test
    void testInvalidBase64DataReturns4xx() throws Exception {
        String requestBody = String.format(
                "{\n" +
                "  \"message\": {\n" +
                "    \"data\": \"not-valid-base64!!!\",\n" +
                "    \"messageId\": \"test-message-id\",\n" +
                "    \"attributes\": {\n" +
                "      \"job_id\": \"%s\"\n" +
                "    }\n" +
                "  }\n" +
                "}",
                testJobId);

        mockMvc.perform(post("/internal/pubsub")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest());

        // Verify processor was never invoked
        verify(documentJobProcessor, never()).processDocument(any());
    }

    /**
     * Test that missing message field returns 4xx.
     */
    @Test
    void testMissingMessageFieldReturns4xx() throws Exception {
        String requestBody = "{\"subscription\":\"projects/test/subscriptions/test-sub\"}";

        mockMvc.perform(post("/internal/pubsub")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest());

        // Verify processor was never invoked
        verify(documentJobProcessor, never()).processDocument(any());
    }

    /**
     * Test that processing exceptions return 5xx (so Pub/Sub will retry).
     */
    @Test
    void testProcessingExceptionReturns5xx() throws Exception {
        // Create a PENDING job in database
        PolicyJob job = new PolicyJob(testJobId);
        job.setStatus("PENDING");
        job.setPdfGcsPath(testGcsPath);
        policyJobRepository.save(job);

        // Build Pub/Sub push envelope
        String payloadJson = String.format("{\"job_id\":\"%s\",\"gcs_path\":\"%s\"}", testJobId, testGcsPath);
        String base64Data = Base64.getEncoder().encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8));

        String pubsubMessageId = "test-message-id-" + UUID.randomUUID();
        String requestBody = String.format(
                "{\n" +
                "  \"message\": {\n" +
                "    \"data\": \"%s\",\n" +
                "    \"messageId\": \"%s\",\n" +
                "    \"attributes\": {\n" +
                "      \"job_id\": \"%s\",\n" +
                "      \"request_id\": \"%s\"\n" +
                "    }\n" +
                "  }\n" +
                "}",
                base64Data, pubsubMessageId, testJobId, testRequestId);

        // Mock processor to throw exception
        doThrow(new RuntimeException("Processing failed: DB connection error"))
                .when(documentJobProcessor).processDocument(testJobId);

        // Send POST request - should return 500 so Pub/Sub retries
        mockMvc.perform(post("/internal/pubsub")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isInternalServerError());

        // Verify processor was invoked (job status was updated to PROCESSING before exception)
        verify(documentJobProcessor, times(1)).processDocument(testJobId);
    }
}

