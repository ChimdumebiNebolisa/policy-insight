package com.policyinsight.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.policyinsight.TestPdfFactory;
import com.policyinsight.repository.PolicyJobRepository;
import com.policyinsight.repository.QaInteractionRepository;
import com.policyinsight.repository.ReportRepository;
import jakarta.servlet.http.Cookie;
import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class QaControllerTests {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ReportRepository reportRepository;

    @Autowired
    QaInteractionRepository qaInteractionRepository;

    @Autowired
    PolicyJobRepository policyJobRepository;

    @Test
    void qaHistoryRendersOnReportPage() throws Exception {
        UploadFixture fixture = upload("10.3.0.1");

        mockMvc.perform(post("/qa/" + fixture.reportId())
                        .cookie(fixture.ownerCookie())
                        .param("question", "What are the key contract terms?"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/report/" + fixture.reportId()).cookie(fixture.ownerCookie()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("What are the key contract terms?")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("qa-history-item")));
    }

    @Test
    void answersAndSavesGroundedQuestion() throws Exception {
        UploadFixture fixture = upload();
        long before = qaInteractionRepository.count();

        mockMvc.perform(post("/qa/" + fixture.reportId())
                        .cookie(fixture.ownerCookie())
                        .param("question", "What should I review?"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Answer based on saved document source text")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Source 1")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("chunk"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("mock"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("fallback"))));

        assertThat(qaInteractionRepository.count()).isEqualTo(before + 1);
    }

    @Test
    void qaRequiresOwnerCookie() throws Exception {
        UploadFixture fixture = upload();

        mockMvc.perform(post("/qa/" + fixture.reportId()).param("question", "What changed?"))
                .andExpect(status().isForbidden());
    }

    @Test
    void rateLimitsQaByIp() throws Exception {
        UploadFixture fixture = upload();

        for (int i = 0; i < 10; i++) {
            mockMvc.perform(post("/qa/" + fixture.reportId())
                            .cookie(fixture.ownerCookie())
                            .with(request -> {
                                request.setRemoteAddr("203.0.113.7");
                                return request;
                            })
                            .param("question", "Question " + i))
                    .andExpect(status().isOk());
        }

        mockMvc.perform(post("/qa/" + fixture.reportId())
                        .cookie(fixture.ownerCookie())
                        .with(request -> {
                            request.setRemoteAddr("203.0.113.7");
                            return request;
                        })
                        .param("question", "One too many"))
                .andExpect(status().isTooManyRequests());
    }

    private UploadFixture upload() throws Exception {
        return upload("127.0.0.1");
    }

    private UploadFixture upload(String remoteAddr) throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "policy.pdf",
                "application/pdf",
                TestPdfFactory.pdfWithText("This policy requires written notice before termination.")
        );
        MvcResult result = mockMvc.perform(multipart("/upload").file(file)
                        .with(request -> {
                            request.setRemoteAddr(remoteAddr);
                            return request;
                        }))
                .andExpect(status().isOk())
                .andReturn();
        Cookie ownerCookie = Arrays.stream(result.getResponse().getCookies())
                .filter(cookie -> cookie.getName().startsWith("PI_OWNER_"))
                .findFirst()
                .orElseThrow();
        UUID jobId = policyJobRepository.findAll().getLast().getId();
        return new UploadFixture(waitForReport(jobId), ownerCookie);
    }

    private UUID waitForReport(UUID jobId) throws InterruptedException {
        for (int i = 0; i < 40; i++) {
            var report = reportRepository.findByJobId(jobId);
            if (report.isPresent()) {
                return report.get().getId();
            }
            Thread.sleep(100);
        }
        throw new AssertionError("Timed out waiting for async report.");
    }

    private record UploadFixture(UUID reportId, Cookie ownerCookie) {
    }
}
