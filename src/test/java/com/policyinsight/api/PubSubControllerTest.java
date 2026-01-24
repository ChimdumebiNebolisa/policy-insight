package com.policyinsight.api;

import com.policyinsight.processing.DocumentJobProcessor;
import com.policyinsight.security.JobTokenInterceptor;
import com.policyinsight.shared.model.PolicyJob;
import com.policyinsight.shared.repository.PolicyJobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web slice test for Pub/Sub push endpoint.
 * Tests token verification, message parsing, and processing dispatch using @WebMvcTest.
 */
@WebMvcTest(PubSubController.class)
class PubSubControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PubSubTokenVerifier tokenVerifier;

    @MockBean
    private DocumentJobProcessor documentJobProcessor;

    @MockBean
    private PolicyJobRepository policyJobRepository;

    @MockBean
    private JobTokenInterceptor jobTokenInterceptor;

    @BeforeEach
    void setUp() {
        reset(documentJobProcessor, tokenVerifier, policyJobRepository);
        // Default: token verification passes
        when(tokenVerifier.verifyToken(any())).thenReturn(true);
        // Default: repository update succeeds (1 row updated = job was PENDING)
        when(policyJobRepository.updateStatusIfPending(any())).thenReturn(1);
        when(policyJobRepository.updateStatusIfPendingWithLease(any(), any())).thenReturn(1);
        // Default: repository find returns a job
        PolicyJob mockJob = new PolicyJob(UUID.randomUUID());
        mockJob.setStatus("PROCESSING");
        when(policyJobRepository.findByJobUuid(any())).thenReturn(Optional.of(mockJob));
    }

    @Test
    void testPubSubPushWithValidMessage() throws Exception {
        // Given: A valid Pub/Sub push message with job_id
        UUID jobId = UUID.randomUUID();
        String payloadJson = String.format("{\"job_id\":\"%s\",\"gcs_path\":\"gs://bucket/jobs/%s/document.pdf\"}", jobId, jobId);
        String base64Data = Base64.getEncoder().encodeToString(payloadJson.getBytes());

        String pubSubMessage = String.format(
            "{\"message\":{\"data\":\"%s\",\"messageId\":\"test-message-id\",\"attributes\":{\"job_id\":\"%s\"},\"publishTime\":\"2025-01-29T10:00:00Z\"},\"subscription\":\"projects/test-project/subscriptions/test-sub\"}",
            base64Data, jobId);

        // When: POST to /internal/pubsub endpoint
        mockMvc.perform(post("/internal/pubsub")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(pubSubMessage))
                .andExpect(status().isNoContent());

        // Then: Verify that documentJobProcessor.processDocument was called with the job ID
        verify(tokenVerifier, times(1)).verifyToken("Bearer test-token");
        verify(documentJobProcessor, times(1)).processDocument(jobId);
        verifyNoMoreInteractions(documentJobProcessor);
    }

    @Test
    void testPubSubPushWithoutAuthorization() throws Exception {
        // Given: Token verifier rejects missing authorization
        when(tokenVerifier.verifyToken(null)).thenReturn(false);
        UUID jobId = UUID.randomUUID();
        String payloadJson = String.format("{\"job_id\":\"%s\"}", jobId);
        String base64Data = Base64.getEncoder().encodeToString(payloadJson.getBytes());

        String pubSubMessage = String.format(
            "{\"message\":{\"data\":\"%s\",\"messageId\":\"test-message-id\"},\"subscription\":\"projects/test-project/subscriptions/test-sub\"}",
            base64Data);

        // When: POST to /internal/pubsub endpoint without Authorization header
        mockMvc.perform(post("/internal/pubsub")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(pubSubMessage))
                .andExpect(status().isUnauthorized()); // Should fail because verification is enabled and token is missing

        // Then: Verify processing was NOT called
        verify(tokenVerifier, times(1)).verifyToken(null);
        verifyNoInteractions(documentJobProcessor);
    }

    @Test
    void testPubSubPushWithInvalidMessage() throws Exception {
        // Given: An invalid Pub/Sub push message (missing message.data)
        String invalidMessage = "{\"message\":{\"messageId\":\"test-message-id\"},\"subscription\":\"projects/test-project/subscriptions/test-sub\"}";

        // When: POST to /internal/pubsub endpoint
        mockMvc.perform(post("/internal/pubsub")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidMessage))
                .andExpect(status().isBadRequest());

        // Then: Verify processing was NOT called
        verify(tokenVerifier, times(1)).verifyToken("Bearer test-token");
        verifyNoInteractions(documentJobProcessor);
    }

    @Test
    void testPubSubPushWithMissingJobId() throws Exception {
        // Given: A Pub/Sub push message without job_id
        String payloadJson = "{\"gcs_path\":\"gs://bucket/jobs/test/document.pdf\"}";
        String base64Data = Base64.getEncoder().encodeToString(payloadJson.getBytes());

        String pubSubMessage = String.format(
            "{\"message\":{\"data\":\"%s\",\"messageId\":\"test-message-id\"},\"subscription\":\"projects/test-project/subscriptions/test-sub\"}",
            base64Data);

        // When: POST to /internal/pubsub endpoint
        mockMvc.perform(post("/internal/pubsub")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(pubSubMessage))
                .andExpect(status().isBadRequest());

        // Then: Verify processing was NOT called
        verify(tokenVerifier, times(1)).verifyToken("Bearer test-token");
        verifyNoInteractions(documentJobProcessor);
    }

    @Test
    void testPubSubPushWithJobIdInAttributes() throws Exception {
        // Given: A Pub/Sub push message with job_id in attributes (not in payload)
        UUID jobId = UUID.randomUUID();
        String payloadJson = "{\"gcs_path\":\"gs://bucket/jobs/test/document.pdf\"}";
        String base64Data = Base64.getEncoder().encodeToString(payloadJson.getBytes());

        String pubSubMessage = String.format(
            "{\"message\":{\"data\":\"%s\",\"messageId\":\"test-message-id\",\"attributes\":{\"job_id\":\"%s\"}},\"subscription\":\"projects/test-project/subscriptions/test-sub\"}",
            base64Data, jobId);

        // When: POST to /internal/pubsub endpoint
        mockMvc.perform(post("/internal/pubsub")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(pubSubMessage))
                .andExpect(status().isNoContent());

        // Then: Verify processing was called with job_id from attributes
        verify(tokenVerifier, times(1)).verifyToken("Bearer test-token");
        verify(documentJobProcessor, times(1)).processDocument(jobId);
    }

    @Test
    void testPubSubPushWithInvalidToken() throws Exception {
        // Given: Token verification fails
        when(tokenVerifier.verifyToken("Bearer invalid-token")).thenReturn(false);
        UUID jobId = UUID.randomUUID();
        String payloadJson = String.format("{\"job_id\":\"%s\"}", jobId);
        String base64Data = Base64.getEncoder().encodeToString(payloadJson.getBytes());

        String pubSubMessage = String.format(
            "{\"message\":{\"data\":\"%s\",\"messageId\":\"test-message-id\"},\"subscription\":\"projects/test-project/subscriptions/test-sub\"}",
            base64Data);

        // When: POST to /internal/pubsub endpoint with invalid token
        mockMvc.perform(post("/internal/pubsub")
                        .header("Authorization", "Bearer invalid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(pubSubMessage))
                .andExpect(status().isUnauthorized()); // Should fail because token verification failed

        // Then: Verify processing was NOT called
        verify(tokenVerifier, times(1)).verifyToken("Bearer invalid-token");
        verifyNoInteractions(documentJobProcessor);
    }


    @Test
    void testPubSubPushWithInvalidBase64Data() throws Exception {
        // Given: A Pub/Sub push message with invalid base64 data
        String pubSubMessage = "{\"message\":{\"data\":\"not-valid-base64!!!\",\"messageId\":\"test-message-id\"},\"subscription\":\"projects/test-project/subscriptions/test-sub\"}";

        // When: POST to /internal/pubsub endpoint
        mockMvc.perform(post("/internal/pubsub")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(pubSubMessage))
                .andExpect(status().isBadRequest()); // Base64 decode failure causes 400 (malformed payload)

        // Then: Verify processing was NOT called
        verify(tokenVerifier, times(1)).verifyToken("Bearer test-token");
        verifyNoInteractions(documentJobProcessor);
    }

    @Test
    void testPubSubPushWithInvalidJsonPayload() throws Exception {
        // Given: A Pub/Sub push message with invalid JSON in payload
        String payloadJson = "not valid json";
        String base64Data = Base64.getEncoder().encodeToString(payloadJson.getBytes());

        String pubSubMessage = String.format(
            "{\"message\":{\"data\":\"%s\",\"messageId\":\"test-message-id\"},\"subscription\":\"projects/test-project/subscriptions/test-sub\"}",
            base64Data);

        // When: POST to /internal/pubsub endpoint
        mockMvc.perform(post("/internal/pubsub")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(pubSubMessage))
                .andExpect(status().isBadRequest()); // Invalid JSON in payload causes 400

        // Then: Verify processing was NOT called
        verify(tokenVerifier, times(1)).verifyToken("Bearer test-token");
        verifyNoInteractions(documentJobProcessor);
    }

    @Test
    void testPubSubPushWithInvalidJobIdFormat() throws Exception {
        // Given: A Pub/Sub push message with invalid job_id format (not a UUID)
        String payloadJson = "{\"job_id\":\"not-a-uuid\"}";
        String base64Data = Base64.getEncoder().encodeToString(payloadJson.getBytes());

        String pubSubMessage = String.format(
            "{\"message\":{\"data\":\"%s\",\"messageId\":\"test-message-id\"},\"subscription\":\"projects/test-project/subscriptions/test-sub\"}",
            base64Data);

        // When: POST to /internal/pubsub endpoint
        mockMvc.perform(post("/internal/pubsub")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(pubSubMessage))
                .andExpect(status().isBadRequest()); // Invalid UUID format causes 400

        // Then: Verify processing was NOT called
        verify(tokenVerifier, times(1)).verifyToken("Bearer test-token");
        verifyNoInteractions(documentJobProcessor);
    }
}

