package com.policyinsight.api;

import com.policyinsight.security.TokenService;
import com.policyinsight.shared.model.PolicyJob;
import com.policyinsight.shared.model.ShareLink;
import com.policyinsight.shared.repository.PolicyJobRepository;
import com.policyinsight.shared.repository.ShareLinkRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for share link revocation.
 * Tests revocation endpoint, revoked link validation, and token requirement.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class ShareLinkRevocationTest {

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
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PolicyJobRepository policyJobRepository;

    @Autowired
    private ShareLinkRepository shareLinkRepository;

    @Autowired
    private TokenService tokenService;

    private UUID testJobId;
    private String testToken;
    private String testTokenHmac;
    private ShareLink testShareLink;

    @BeforeEach
    void setUp() {
        testJobId = UUID.randomUUID();
        testToken = tokenService.generateToken();
        testTokenHmac = tokenService.computeHmac(testToken);

        // Create a test job with token
        PolicyJob job = new PolicyJob(testJobId);
        job.setStatus("SUCCESS");
        job.setPdfFilename("test.pdf");
        job.setAccessTokenHmac(testTokenHmac);
        policyJobRepository.save(job);

        // Create a test share link
        testShareLink = new ShareLink(testJobId);
        shareLinkRepository.save(testShareLink);
    }

    @Test
    void testRevokeShareLinkRequiresToken() throws Exception {
        // When: Request revocation without token
        // Then: Should return 401 Unauthorized
        mockMvc.perform(post("/api/documents/{id}/share/revoke", testJobId))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
    }

    @Test
    void testRevokeShareLinkWithValidToken() throws Exception {
        // When: Request revocation with valid token
        mockMvc.perform(post("/api/documents/{id}/share/revoke", testJobId)
                        .header("X-Job-Token", testToken)
                        .header("Origin", "http://localhost:8080"))
                .andExpect(status().isOk());

        // Then: Share link should be revoked
        ShareLink revokedLink = shareLinkRepository.findByShareToken(testShareLink.getShareToken())
                .orElseThrow();
        assertThat(revokedLink.isRevoked()).isTrue();
        assertThat(revokedLink.getRevokedAt()).isNotNull();
    }

    @Test
    void testRevokedShareLinkCannotBeAccessed() {
        // Given: Revoked share link
        testShareLink.revoke();
        shareLinkRepository.save(testShareLink);

        // When: Validating revoked link
        ShareLink validated = shareLinkRepository.findByShareToken(testShareLink.getShareToken())
                .orElse(null);

        // Then: Should be revoked
        assertThat(validated).isNotNull();
        assertThat(validated.isRevoked()).isTrue();
    }

    @Test
    void testRevokeNonExistentShareLink() throws Exception {
        // Given: Job without share link
        UUID jobWithoutLink = UUID.randomUUID();
        PolicyJob job = new PolicyJob(jobWithoutLink);
        job.setStatus("SUCCESS");
        job.setAccessTokenHmac(tokenService.computeHmac(tokenService.generateToken()));
        policyJobRepository.save(job);

        String token = tokenService.generateToken();
        job.setAccessTokenHmac(tokenService.computeHmac(token));
        policyJobRepository.save(job);

        // When: Request revocation for job without share link
        mockMvc.perform(post("/api/documents/{id}/share/revoke", jobWithoutLink)
                        .header("X-Job-Token", token)
                        .header("Origin", "http://localhost:8080"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").value("No active share link found for this document"));
    }
}

