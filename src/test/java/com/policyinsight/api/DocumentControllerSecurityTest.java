package com.policyinsight.api;

import com.policyinsight.security.TokenService;
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
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Security tests for capability-token based access control.
 * Tests token validation, CSRF protection, and public endpoint allowlist.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class DocumentControllerSecurityTest {

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
    private TokenService tokenService;

    private UUID testJobId;
    private String testToken;
    private String testTokenHmac;

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
    }

    @Test
    void testStatusEndpointRequiresToken() throws Exception {
        // When: Request status without token
        // Then: Should return 401 Unauthorized
        mockMvc.perform(get("/api/documents/{id}/status", testJobId))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
    }

    @Test
    void testStatusEndpointAcceptsTokenHeader() throws Exception {
        // When: Request status with X-Job-Token header
        // Then: Should return 200 OK
        mockMvc.perform(get("/api/documents/{id}/status", testJobId)
                        .header("X-Job-Token", testToken))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.jobId").value(testJobId.toString()))
                .andExpect(jsonPath("$.status").value("SUCCESS"));
    }

    @Test
    void testStatusEndpointAcceptsTokenCookie() throws Exception {
        // When: Request status with token cookie
        // Then: Should return 200 OK
        String cookieName = "pi_job_token_" + testJobId.toString();
        mockMvc.perform(get("/api/documents/{id}/status", testJobId)
                        .cookie(new jakarta.servlet.http.Cookie(cookieName, testToken)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.jobId").value(testJobId.toString()));
    }

    @Test
    void testStatusEndpointRejectsInvalidToken() throws Exception {
        // When: Request status with invalid token
        // Then: Should return 401 Unauthorized
        mockMvc.perform(get("/api/documents/{id}/status", testJobId)
                        .header("X-Job-Token", "invalid-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
    }

    @Test
    void testShareGenerationRequiresToken() throws Exception {
        // When: Request share link generation without token
        // Then: Should return 401 Unauthorized
        mockMvc.perform(post("/api/documents/{id}/share", testJobId))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testShareGenerationRequiresCsrfForPost() throws Exception {
        // When: Request share link generation with valid token but missing Origin/Referer
        // Then: Should return 403 Forbidden (CSRF check fails)
        mockMvc.perform(post("/api/documents/{id}/share", testJobId)
                        .header("X-Job-Token", testToken))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    @Test
    void testShareGenerationAcceptsOriginHeader() throws Exception {
        // When: Request share link generation with valid token and Origin header
        // Then: CSRF check should pass (may return 409 if job not ready, but not 403)
        var result = mockMvc.perform(post("/api/documents/{id}/share", testJobId)
                        .header("X-Job-Token", testToken)
                        .header("Origin", "http://localhost:8080"))
                .andReturn();
        // Should not be 403 (CSRF failure) - may be 201, 409, or 401
        assertThat(result.getResponse().getStatus()).isNotEqualTo(403);
    }

    @Test
    void testShareViewRemainsPublic() throws Exception {
        // When: Request share view (via share_token) without job token
        // Then: Should be accessible (public endpoint) - not 401
        UUID shareToken = UUID.randomUUID();
        var result = mockMvc.perform(get("/documents/{id}/share/{token}", testJobId, shareToken))
                .andReturn();
        // Should not be 401 (token required) - may be 404 or error page
        assertThat(result.getResponse().getStatus()).isNotEqualTo(401);
    }

    @Test
    void testUploadEndpointIsPublic() throws Exception {
        // When: Request upload endpoint
        // Then: Should be accessible (public endpoint) - not 401
        org.springframework.mock.web.MockMultipartFile file =
                new org.springframework.mock.web.MockMultipartFile(
                        "file", "test.pdf", "application/pdf", "invalid".getBytes());

        var result = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/api/documents/upload")
                        .file(file))
                .andReturn();
        // Should not be 401 (token required) - may be 400, 202, etc.
        assertThat(result.getResponse().getStatus()).isNotEqualTo(401);
    }

    @Test
    void testHealthEndpointIsPublic() throws Exception {
        // When: Request health endpoint
        // Then: Should be accessible (public endpoint)
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk());
    }

    @Test
    void testUploadReturnsTokenInJson() throws Exception {
        // Given: Valid PDF file
        byte[] pdfBytes = "%PDF-1.4\n".getBytes();
        org.springframework.mock.web.MockMultipartFile file =
                new org.springframework.mock.web.MockMultipartFile(
                        "file", "test.pdf", "application/pdf", pdfBytes);

        // When: Upload document
        var result = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/api/documents/upload")
                        .file(file)
                        .contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().isAccepted())
                .andReturn();

        // Then: Response should contain token
        String responseBody = result.getResponse().getContentAsString();
        assertThat(responseBody).contains("\"token\"");
        assertThat(responseBody).contains("\"jobId\"");
    }

    @Test
    void testUploadSetsCookie() throws Exception {
        // Given: Valid PDF file
        byte[] pdfBytes = "%PDF-1.4\n".getBytes();
        org.springframework.mock.web.MockMultipartFile file =
                new org.springframework.mock.web.MockMultipartFile(
                        "file", "test.pdf", "application/pdf", pdfBytes);

        // When: Upload document with HTMX header
        var result = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/api/documents/upload")
                        .file(file)
                        .header("HX-Request", "true")
                        .contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().isOk()) // HTMX returns 200, not 202
                .andReturn();

        // Then: Response should set cookie
        String setCookieHeader = result.getResponse().getHeader("Set-Cookie");
        assertThat(setCookieHeader).isNotNull();
        assertThat(setCookieHeader).contains("pi_job_token_");
        assertThat(setCookieHeader).contains("HttpOnly");
        assertThat(setCookieHeader).contains("SameSite=Strict");
    }
}

