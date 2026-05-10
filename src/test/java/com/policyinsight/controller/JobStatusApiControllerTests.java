package com.policyinsight.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.policyinsight.model.JobStatus;
import com.policyinsight.model.PolicyJob;
import com.policyinsight.model.Report;
import com.policyinsight.repository.PolicyJobRepository;
import com.policyinsight.repository.ReportRepository;
import com.policyinsight.security.TokenService;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class JobStatusApiControllerTests {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    PolicyJobRepository policyJobRepository;

    @Autowired
    ReportRepository reportRepository;

    @Autowired
    TokenService tokenService;

    @BeforeEach
    void cleanDatabase() {
        policyJobRepository.deleteAll();
    }

    @Test
    void returnsProcessingStatusForOwner() throws Exception {
        JobFixture fixture = job(JobStatus.PROCESSING, null);

        mockMvc.perform(get("/api/jobs/" + fixture.job().getId() + "/status").cookie(fixture.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value(fixture.job().getId().toString()))
                .andExpect(jsonPath("$.status").value("PROCESSING"))
                .andExpect(jsonPath("$.reportId").doesNotExist())
                .andExpect(jsonPath("$.message").value("Report is still processing."))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.updatedAt").exists());
    }

    @Test
    void returnsCompletedStatusWithReportIdForOwner() throws Exception {
        JobFixture fixture = job(JobStatus.COMPLETED, null);
        Report report = reportRepository.save(new Report(fixture.job(), "{\"documentOverview\":\"overview\"}"));

        mockMvc.perform(get("/api/jobs/" + fixture.job().getId() + "/status").cookie(fixture.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.reportId").value(report.getId().toString()))
                .andExpect(jsonPath("$.message").value("Report is ready."));
    }

    @Test
    void returnsFailedStatusWithSafeMessageForOwner() throws Exception {
        JobFixture fixture = job(JobStatus.FAILED, "Safe failure message.");

        mockMvc.perform(get("/api/jobs/" + fixture.job().getId() + "/status").cookie(fixture.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.message").value("Safe failure message."));
    }

    @Test
    void statusApiRequiresOwnerCookie() throws Exception {
        JobFixture fixture = job(JobStatus.PROCESSING, null);

        mockMvc.perform(get("/api/jobs/" + fixture.job().getId() + "/status"))
                .andExpect(status().isForbidden());
    }

    @Test
    void statusApiReturnsNotFoundForMissingJob() throws Exception {
        mockMvc.perform(get("/api/jobs/" + UUID.randomUUID() + "/status"))
                .andExpect(status().isNotFound());
    }

    private JobFixture job(JobStatus status, String safeErrorMessage) {
        String ownerToken = tokenService.generateToken();
        PolicyJob job = new PolicyJob(tokenService.hashToken(ownerToken), Instant.now().plusSeconds(3600));
        job.setStatus(status);
        job.setSafeErrorMessage(safeErrorMessage);
        PolicyJob saved = policyJobRepository.save(job);
        Cookie cookie = new Cookie(DocumentController.ownerCookieName(saved.getId().toString()), ownerToken);
        return new JobFixture(saved, cookie);
    }

    private record JobFixture(PolicyJob job, Cookie cookie) {
    }
}
