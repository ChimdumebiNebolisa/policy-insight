package com.policyinsight.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.policyinsight.TestPdfFactory;
import com.policyinsight.ai.AiAnalyzer;
import com.policyinsight.ai.AiAnalyzerException;
import com.policyinsight.ai.GeminiAnalyzer;
import com.policyinsight.ai.dto.CitedClaim;
import com.policyinsight.ai.dto.RiskReport;
import com.policyinsight.model.JobStatus;
import com.policyinsight.model.PolicyJob;
import com.policyinsight.repository.DocumentChunkRepository;
import com.policyinsight.repository.PolicyJobRepository;
import com.policyinsight.repository.ReportRepository;
import java.util.List;
import jakarta.servlet.http.Cookie;
import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class UploadFailureFallbackTests {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    PolicyJobRepository policyJobRepository;

    @Autowired
    DocumentChunkRepository documentChunkRepository;

    @Autowired
    ReportRepository reportRepository;

    @MockBean
    AiAnalyzer aiAnalyzer;

    @Test
    void retryTriggersNewAnalysisAfterFailure() throws Exception {
        when(aiAnalyzer.generateReport(anyList()))
                .thenThrow(new AiAnalyzerException("first call fails"))
                .thenReturn(successReport());

        MvcResult upload = mockMvc.perform(multipart("/upload").file(pdf()))
                .andExpect(status().isOk())
                .andReturn();
        Cookie ownerCookie = ownerCookie(upload);
        UUID jobId = jobIdFromOwnerCookie(ownerCookie);
        waitForStatus(jobId, JobStatus.FAILED);
        PolicyJob job = policyJobRepository.findById(jobId).orElseThrow();

        mockMvc.perform(get("/status/" + job.getId()).cookie(ownerCookie))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Retry AI analysis")));

        mockMvc.perform(post("/retry/" + job.getId()))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/retry/" + job.getId()).cookie(ownerCookie))
                .andExpect(status().isOk());

        waitForStatus(job.getId(), JobStatus.COMPLETED);
        assertThat(reportRepository.findByJobId(job.getId())).isPresent();
    }

    @Test
    void retryDeniedIfJobIsNotFailed() throws Exception {
        when(aiAnalyzer.generateReport(anyList())).thenReturn(successReport());

        MvcResult upload = mockMvc.perform(multipart("/upload").file(pdf()))
                .andExpect(status().isOk())
                .andReturn();
        Cookie ownerCookie = ownerCookie(upload);
        UUID jobId = jobIdFromOwnerCookie(ownerCookie);
        waitForStatus(jobId, JobStatus.COMPLETED);
        PolicyJob job = policyJobRepository.findById(jobId).orElseThrow();

        mockMvc.perform(post("/retry/" + job.getId()).cookie(ownerCookie))
                .andExpect(status().isBadRequest());
    }

    @Test
    void uploadFailureKeepsExtractedSourcesAndShowsSafeFallbackOption() throws Exception {
        when(aiAnalyzer.generateReport(anyList()))
                .thenThrow(new AiAnalyzerException("provider failed with secret-token"));

        MvcResult upload = mockMvc.perform(multipart("/upload").file(pdf()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("source section(s) extracted")))
                .andReturn();
        Cookie ownerCookie = ownerCookie(upload);
        UUID jobId = jobIdFromOwnerCookie(ownerCookie);
        waitForStatus(jobId, JobStatus.FAILED);
        PolicyJob job = policyJobRepository.findById(jobId).orElseThrow();

        assertThat(job.getStatus()).isEqualTo(JobStatus.FAILED);
        assertThat(job.getSafeErrorMessage()).isEqualTo(GeminiAnalyzer.SAFE_ANALYSIS_FAILURE_MESSAGE);
        assertThat(job.getSafeErrorMessage()).doesNotContain("secret-token");
        assertThat(documentChunkRepository.findByJobIdOrderByChunkIndex(job.getId())).isNotEmpty();
        assertThat(reportRepository.findByJobId(job.getId())).isEmpty();

        mockMvc.perform(get("/status/" + job.getId()).cookie(ownerCookie))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(GeminiAnalyzer.SAFE_ANALYSIS_FAILURE_MESSAGE)))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Generate demo-style report from extracted text")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Demo fallback. Not live AI analysis.")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("every 2s"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret-token"))));

        mockMvc.perform(post("/fallback/" + job.getId()).cookie(ownerCookie))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Your cited report is ready")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("View report")));

        assertThat(reportRepository.findByJobId(job.getId())).isPresent();
    }

    @Test
    void processingStatusPollsOneStableContainerWithoutNestedCards() throws Exception {
        when(aiAnalyzer.generateReport(anyList()))
                .thenAnswer(invocation -> {
                    Thread.sleep(1000);
                    throw new AiAnalyzerException("slow provider failure");
                });

        MvcResult upload = mockMvc.perform(multipart("/upload").file(pdf()))
                .andExpect(status().isOk())
                .andReturn();
        Cookie ownerCookie = ownerCookie(upload);
        UUID jobId = jobIdFromOwnerCookie(ownerCookie);

        MvcResult statusResult = mockMvc.perform(get("/status/" + jobId).cookie(ownerCookie))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("hx-target=\"#job-status\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("hx-swap=\"innerHTML\"")))
                .andReturn();

        String body = statusResult.getResponse().getContentAsString();
        assertThat(countOccurrences(body, "class=\"status-card\"")).isEqualTo(1);
        assertThat(countOccurrences(body, "hx-get=\"/status/")).isEqualTo(1);
    }

    @Test
    void fallbackReportIsClearlyLabeledWhenOpened() throws Exception {
        when(aiAnalyzer.generateReport(anyList()))
                .thenThrow(new AiAnalyzerException(GeminiAnalyzer.SAFE_ANALYSIS_FAILURE_MESSAGE));

        MvcResult upload = mockMvc.perform(multipart("/upload").file(pdf()))
                .andExpect(status().isOk())
                .andReturn();
        Cookie ownerCookie = ownerCookie(upload);
        UUID jobId = jobIdFromOwnerCookie(ownerCookie);
        waitForStatus(jobId, JobStatus.FAILED);

        mockMvc.perform(post("/fallback/" + jobId).cookie(ownerCookie))
                .andExpect(status().isOk());

        mockMvc.perform(get("/report/" + reportRepository.findByJobId(jobId).orElseThrow().getId()).cookie(ownerCookie))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Demo fallback. Not live AI analysis.")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Ask a question")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Source evidence")));
    }

    private static RiskReport successReport() {
        CitedClaim claim = new CitedClaim("Test claim.", List.of(), false);
        return new RiskReport("Test overview.", List.of(claim), List.of(claim), List.of(), List.of(), List.of(claim));
    }

    private MockMultipartFile pdf() throws Exception {
        return new MockMultipartFile(
                "file",
                "policy.pdf",
                "application/pdf",
                TestPdfFactory.pdfWithText("This agreement requires written notice before termination. Payment is due within fifteen days.")
        );
    }

    private Cookie ownerCookie(MvcResult result) {
        return Arrays.stream(result.getResponse().getCookies())
                .filter(cookie -> cookie.getName().startsWith("PI_OWNER_"))
                .findFirst()
                .orElseThrow();
    }

    /**
     * Resolves the job id from the owner cookie name ({@code PI_OWNER_} + UUID without dashes).
     * Avoids {@code findAll().getLast()}, which is undefined order and can pick another job when the
     * in-memory DB already contains rows from other tests.
     */
    private static UUID jobIdFromOwnerCookie(Cookie ownerCookie) {
        String name = ownerCookie.getName();
        String prefix = "PI_OWNER_";
        if (!name.startsWith(prefix)) {
            throw new IllegalStateException("Expected owner cookie name to start with " + prefix + ", got: " + name);
        }
        String hex = name.substring(prefix.length());
        if (hex.length() != 32) {
            throw new IllegalStateException("Expected 32 hex chars in owner cookie name, got length " + hex.length());
        }
        String uuid = hex.substring(0, 8)
                + "-"
                + hex.substring(8, 12)
                + "-"
                + hex.substring(12, 16)
                + "-"
                + hex.substring(16, 20)
                + "-"
                + hex.substring(20, 32);
        return UUID.fromString(uuid);
    }

    private int countOccurrences(String text, String needle) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
    }

    private void waitForStatus(UUID jobId, JobStatus expectedStatus) throws InterruptedException {
        for (int i = 0; i < 40; i++) {
            PolicyJob job = policyJobRepository.findById(jobId).orElseThrow();
            if (job.getStatus() == expectedStatus) {
                return;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("Timed out waiting for status " + expectedStatus);
    }
}
