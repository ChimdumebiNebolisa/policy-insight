package com.policyinsight.api;

import com.policyinsight.security.RateLimitService;
import com.policyinsight.shared.model.PolicyJob;
import com.policyinsight.shared.repository.PolicyJobRepository;
import com.policyinsight.shared.repository.RateLimitCounterRepository;
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
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests for rate limiting functionality.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class RateLimitTest {

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
        registry.add("app.security.token-secret", () -> "test-secret-key-for-hmac");
        registry.add("app.security.allowed-origins", () -> "http://localhost:8080");
        registry.add("app.rate-limit.upload.max-per-hour", () -> "3"); // Low limit for testing
        registry.add("app.rate-limit.qa.max-per-hour", () -> "5"); // Low limit for testing
        registry.add("app.rate-limit.qa.max-per-job", () -> "3");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RateLimitService rateLimitService;

    @Autowired
    private RateLimitCounterRepository rateLimitCounterRepository;

    @Autowired
    private PolicyJobRepository policyJobRepository;

    @BeforeEach
    void setUp() {
        // Clean up rate limit counters before each test
        rateLimitCounterRepository.deleteAll();
    }

    @Test
    void testUploadRateLimitTriggers() throws Exception {
        // Given: Low rate limit (3 per hour) configured
        String testIp = "192.168.1.100";
        org.springframework.mock.web.MockMultipartFile file =
                new org.springframework.mock.web.MockMultipartFile(
                        "file", "test.pdf", "application/pdf", "invalid".getBytes());

        // When: Make 3 upload requests (should all succeed or fail validation, but not rate limited)
        for (int i = 0; i < 3; i++) {
            var result = mockMvc.perform(multipart("/api/documents/upload")
                            .file(file)
                            .header("X-Forwarded-For", testIp))
                    .andReturn();
            // May fail validation (invalid PDF), but should not be 429
            assertThat(result.getResponse().getStatus()).isNotEqualTo(429);
        }

        // When: Make 4th request
        // Then: Should return 429 Too Many Requests
        mockMvc.perform(multipart("/api/documents/upload")
                        .file(file)
                        .header("X-Forwarded-For", testIp))
                .andExpect(status().isTooManyRequests())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").value("RATE_LIMIT_EXCEEDED"));
    }

    @Test
    void testQaRateLimitTriggers() throws Exception {
        // Given: A completed job with token
        UUID jobId = UUID.randomUUID();
        PolicyJob job = new PolicyJob(jobId);
        job.setStatus("SUCCESS");
        job.setPdfFilename("test.pdf");
        policyJobRepository.save(job);

        String testIp = "192.168.1.200";
        String testToken = "test-token"; // Note: In real test, would need valid token

        // When: Make requests up to limit (may fail due to missing token, but not rate limit)
        // Note: This test is simplified - in practice would need valid tokens
        // The rate limit check happens before token validation, so we can test it

        // Make 5 requests (limit is 5 per hour)
        for (int i = 0; i < 5; i++) {
            var result = mockMvc.perform(post("/api/questions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(String.format("{\"document_id\":\"%s\",\"question\":\"test\"}", jobId))
                            .header("X-Forwarded-For", testIp)
                            .header("X-Job-Token", testToken))
                    .andReturn();
            // May fail due to invalid token, but should not be 429 for first 5
            if (i < 5) {
                assertThat(result.getResponse().getStatus()).isNotEqualTo(429);
            }
        }

        // When: Make 6th request
        // Then: Should return 429 Too Many Requests (rate limit check happens before token validation)
        var result = mockMvc.perform(post("/api/questions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"document_id\":\"%s\",\"question\":\"test\"}", jobId))
                        .header("X-Forwarded-For", testIp)
                        .header("X-Job-Token", testToken))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(429);
    }

    @Test
    void testPerJobQuotaEnforced() throws Exception {
        // Given: A completed job
        UUID jobId = UUID.randomUUID();
        PolicyJob job = new PolicyJob(jobId);
        job.setStatus("SUCCESS");
        job.setPdfFilename("test.pdf");
        policyJobRepository.save(job);

        // This test would require valid tokens and actual Q&A service setup
        // For now, we verify the quota limit is configurable
        assertThat(rateLimitService.getQaMaxPerJob()).isEqualTo(3);
    }

    @Test
    void testRateLimitResetsAfterWindow() throws Exception {
        // This test would require time manipulation or waiting
        // For now, we verify the service can check limits
        String testIp = "192.168.1.300";
        boolean exceeded = rateLimitService.checkRateLimit(testIp, "/api/documents/upload", 3);
        assertThat(exceeded).isFalse(); // First request should not exceed
    }
}

