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
import com.policyinsight.model.JobStatus;
import com.policyinsight.model.PolicyJob;
import com.policyinsight.repository.DocumentChunkRepository;
import com.policyinsight.repository.PolicyJobRepository;
import com.policyinsight.repository.ReportRepository;
import jakarta.servlet.http.Cookie;
import java.util.Arrays;
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
    void uploadFailureKeepsExtractedSourcesAndShowsSafeFallbackOption() throws Exception {
        when(aiAnalyzer.generateReport(anyList()))
                .thenThrow(new AiAnalyzerException("provider failed with secret-token"));

        MvcResult upload = mockMvc.perform(multipart("/upload").file(pdf()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("source section(s) extracted")))
                .andReturn();
        Cookie ownerCookie = ownerCookie(upload);
        PolicyJob job = policyJobRepository.findAll().getLast();

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
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret-token"))));

        mockMvc.perform(post("/fallback/" + job.getId()).cookie(ownerCookie))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Your cited report is ready")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("View report")));

        assertThat(reportRepository.findByJobId(job.getId())).isPresent();
    }

    @Test
    void fallbackReportIsClearlyLabeledWhenOpened() throws Exception {
        when(aiAnalyzer.generateReport(anyList()))
                .thenThrow(new AiAnalyzerException(GeminiAnalyzer.SAFE_ANALYSIS_FAILURE_MESSAGE));

        MvcResult upload = mockMvc.perform(multipart("/upload").file(pdf()))
                .andExpect(status().isOk())
                .andReturn();
        Cookie ownerCookie = ownerCookie(upload);
        PolicyJob job = policyJobRepository.findAll().getLast();

        mockMvc.perform(post("/fallback/" + job.getId()).cookie(ownerCookie))
                .andExpect(status().isOk());

        mockMvc.perform(get("/report/" + reportRepository.findByJobId(job.getId()).orElseThrow().getId()).cookie(ownerCookie))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Demo fallback. Not live AI analysis.")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Ask a question")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Cited evidence")));
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
}
