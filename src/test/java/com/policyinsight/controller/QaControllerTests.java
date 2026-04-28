package com.policyinsight.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.policyinsight.TestPdfFactory;
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

    @Test
    void answersAndSavesGroundedQuestion() throws Exception {
        UploadFixture fixture = upload();
        long before = qaInteractionRepository.count();

        mockMvc.perform(post("/qa/" + fixture.reportId())
                        .cookie(fixture.ownerCookie())
                        .param("question", "What should I review?"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Mock answer")));

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
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "policy.pdf",
                "application/pdf",
                TestPdfFactory.pdfWithText("This policy requires written notice before termination.")
        );
        MvcResult result = mockMvc.perform(multipart("/upload").file(file))
                .andExpect(status().isOk())
                .andReturn();
        Cookie ownerCookie = Arrays.stream(result.getResponse().getCookies())
                .filter(cookie -> cookie.getName().startsWith("PI_OWNER_"))
                .findFirst()
                .orElseThrow();
        return new UploadFixture(reportRepository.findAll().getLast().getId(), ownerCookie);
    }

    private record UploadFixture(UUID reportId, Cookie ownerCookie) {
    }
}
