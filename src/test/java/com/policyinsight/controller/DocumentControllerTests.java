package com.policyinsight.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.policyinsight.TestPdfFactory;
import com.policyinsight.config.PasteProperties;
import com.policyinsight.model.JobStatus;
import com.policyinsight.model.PolicyJob;
import com.policyinsight.repository.DocumentChunkRepository;
import com.policyinsight.repository.PolicyJobRepository;
import com.policyinsight.repository.ReportRepository;
import jakarta.servlet.http.Cookie;
import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class DocumentControllerTests {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    DocumentChunkRepository documentChunkRepository;

    @Autowired
    ReportRepository reportRepository;

    @Autowired
    PolicyJobRepository policyJobRepository;

    @Autowired
    PasteProperties pasteProperties;

    @Test
    void rejectsInvalidFile() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "bad.txt", "text/plain", "bad".getBytes());

        mockMvc.perform(multipart("/upload").file(file))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsBlankPaste() throws Exception {
        mockMvc.perform(post("/paste")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("text", "   \n\t  ")
                        .header("HX-Request", "true"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("Paste text cannot be empty")));
    }

    @Test
    void rejectsTooLargePaste() throws Exception {
        String tooLong = "x".repeat(pasteProperties.maxChars() + 1);
        mockMvc.perform(post("/paste")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("text", tooLong)
                        .header("HX-Request", "true"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("Pasted text is too long")));
    }

    @Test
    void pasteReturnsAfterExtractionAndAsyncAnalysisCompletes() throws Exception {
        String body = """
                Section 1. This policy requires written notice before termination.
                Section 2. Either party may terminate for material breach with thirty days cure.
                Section 3. Confidential information must be protected using reasonable safeguards.
                """;

        var result = mockMvc.perform(post("/paste")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("text", body))
                .andExpect(status().isOk())
                .andReturn();
        Cookie[] cookies = result.getResponse().getCookies();

        assertThat(Arrays.stream(cookies).map(Cookie::getName))
                .anyMatch(name -> name.startsWith("PI_OWNER_"));
        assertThat(documentChunkRepository.count()).isGreaterThanOrEqualTo(1);
        assertThat(result.getResponse().getContentAsString())
                .contains("Text accepted")
                .contains("source section(s) extracted")
                .contains("Building AI report")
                .contains("hx-target=\"#job-status\"")
                .contains("hx-swap=\"innerHTML\"");
        assertThat(countOccurrences(result.getResponse().getContentAsString(), "class=\"status-card\"")).isEqualTo(1);

        PolicyJob job = policyJobRepository.findAll().getLast();
        assertThat(job.getStatus()).isIn(
                JobStatus.TEXT_EXTRACTED,
                JobStatus.BUILDING_AI_REPORT,
                JobStatus.VALIDATING_CITATIONS,
                JobStatus.COMPLETED
        );

        waitForCompleted(job.getId());
        mockMvc.perform(get("/status/" + job.getId()).cookie(cookies))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Your cited report is ready")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("hx-trigger"))));
        assertThat(reportRepository.findByJobId(job.getId())).isPresent();
    }

    @Test
    void uploadReturnsAfterExtractionAndAsyncAnalysisCompletes() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "policy.pdf",
                "application/pdf",
                TestPdfFactory.pdfWithText("This policy requires written notice before termination.")
        );

        var result = mockMvc.perform(multipart("/upload").file(file))
                .andExpect(status().isOk())
                .andReturn();
        Cookie[] cookies = result.getResponse().getCookies();

        assertThat(Arrays.stream(cookies).map(Cookie::getName))
                .anyMatch(name -> name.startsWith("PI_OWNER_"));
        assertThat(documentChunkRepository.count()).isGreaterThanOrEqualTo(1);
        assertThat(result.getResponse().getContentAsString())
                .contains("Upload accepted")
                .contains("source section(s) extracted")
                .contains("Building AI report")
                .contains("hx-target=\"#job-status\"")
                .contains("hx-swap=\"innerHTML\"");
        assertThat(countOccurrences(result.getResponse().getContentAsString(), "class=\"status-card\"")).isEqualTo(1);

        PolicyJob job = policyJobRepository.findAll().getLast();
        assertThat(job.getStatus()).isIn(
                JobStatus.TEXT_EXTRACTED,
                JobStatus.BUILDING_AI_REPORT,
                JobStatus.VALIDATING_CITATIONS,
                JobStatus.COMPLETED
        );

        waitForCompleted(job.getId());
        mockMvc.perform(get("/status/" + job.getId()).cookie(cookies))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Your cited report is ready")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("hx-trigger"))));
        assertThat(reportRepository.findByJobId(job.getId())).isPresent();
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

    private void waitForCompleted(UUID jobId) throws InterruptedException {
        for (int i = 0; i < 40; i++) {
            PolicyJob job = policyJobRepository.findById(jobId).orElseThrow();
            if (job.getStatus() == JobStatus.COMPLETED) {
                return;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("Timed out waiting for async report completion.");
    }
}
