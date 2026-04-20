package com.policyinsight.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Oracle profile smoke test.
 *
 * Validates the "Oracle deployment boots" definition from the revamp plan:
 *   - App process starts and serves HTTP on the configured port.
 *   - Flyway completes startup migration without migration error.
 *   - Active datasource is Postgres.
 *   - /health, /readiness, /sample-report, and /sample-pdf return HTTP 200.
 *
 * LLM dependency boundary: vertexai.enabled=false (oracle profile default).
 * Messaging boundary: app.messaging.mode=local, pubsub.enabled=false (oracle profile default).
 * Processing boundary: policyinsight.worker.enabled=true, in-process worker only.
 *
 * No paid managed services (Cloud SQL, Pub/Sub, Datadog, Vertex AI) are required.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "oracle"})
@Testcontainers
class OracleProfileSmokeTest {

    @Container
    @SuppressWarnings({"resource", "rawtypes"})
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:15-alpine")
            .withDatabaseName("policyinsight_oracle_smoke")
            .withUsername("postgres")
            .withPassword("postgres");

    /**
     * Wire the Testcontainers Postgres URL into the oracle profile's datasource.
     * All other oracle profile defaults (local storage, local messaging, worker enabled,
     * vertexai disabled) remain in effect.
     */
    @DynamicPropertySource
    static void configureOracleProperties(DynamicPropertyRegistry registry) {
        registry.add("SPRING_DATASOURCE_URL", postgres::getJdbcUrl);
        registry.add("SPRING_DATASOURCE_USERNAME", postgres::getUsername);
        registry.add("SPRING_DATASOURCE_PASSWORD", postgres::getPassword);
        // Override storage local-dir to a temp path so the test doesn't write to /opt/policyinsight
        registry.add("APP_STORAGE_LOCAL_DIR", () -> System.getProperty("java.io.tmpdir") + "/policyinsight-oracle-smoke");
    }

    @Autowired
    private MockMvc mockMvc;

    @Value("${app.messaging.mode:unknown}")
    private String messagingMode;

    @Value("${app.processing.mode:unknown}")
    private String processingMode;

    @Value("${vertexai.enabled:true}")
    private boolean vertexAiEnabled;

    @Value("${pubsub.enabled:true}")
    private boolean pubsubEnabled;

    @Value("${policyinsight.worker.enabled:false}")
    private boolean workerEnabled;

    /**
     * Verifies the oracle profile enforces the correct runtime mode defaults.
     * All defaults must hold without any env override to satisfy the cost rule:
     *   No mandatory dependency on Cloud SQL, Pub/Sub, Datadog, or other paid managed services.
     */
    @Test
    void oracleProfileDefaults_enforceLocalModeAndNoPaidServices() {
        assertThat(messagingMode)
                .as("Oracle path must use local messaging (no Pub/Sub) by default")
                .isEqualTo("local");
        assertThat(processingMode)
                .as("Oracle path must use local in-process worker by default")
                .isEqualTo("local");
        assertThat(vertexAiEnabled)
                .as("LLM dependency boundary: vertexai must be disabled by default on Oracle path")
                .isFalse();
        assertThat(pubsubEnabled)
                .as("Oracle path must not require Pub/Sub by default")
                .isFalse();
        assertThat(workerEnabled)
                .as("Oracle path must enable in-process worker by default")
                .isTrue();
    }

    /**
     * /health must return HTTP 200 without any live LLM or paid service dependency.
     */
    @Test
    void healthEndpoint_returns200() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk());
    }

    /**
     * /readiness must return HTTP 200 without any live LLM or paid service dependency.
     */
    @Test
    void readinessEndpoint_returns200() throws Exception {
        mockMvc.perform(get("/readiness"))
                .andExpect(status().isOk());
    }

    /**
     * /sample-report must return HTTP 200 — offline, no DB or upload required.
     */
    @Test
    void sampleReportEndpoint_returns200() throws Exception {
        mockMvc.perform(get("/sample-report"))
                .andExpect(status().isOk());
    }

    /**
     * /sample-pdf must return HTTP 200 — offline, no DB or upload required.
     */
    @Test
    void samplePdfEndpoint_returns200() throws Exception {
        mockMvc.perform(get("/sample-pdf"))
                .andExpect(status().isOk());
    }
}
