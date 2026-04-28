package com.policyinsight.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.policyinsight.TestPdfFactory;
import com.policyinsight.repository.ReportRepository;
import jakarta.servlet.http.Cookie;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class ReportControllerTests {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ReportRepository reportRepository;

    @Test
    void reportRequiresOwnerCookie() throws Exception {
        UploadFixture fixture = upload();

        mockMvc.perform(get("/report/" + fixture.reportId()))
                .andExpect(status().isForbidden());
    }

    @Test
    void reportRendersWithOwnerCookieAndEscapesOutput() throws Exception {
        UploadFixture fixture = upload();

        mockMvc.perform(get("/report/" + fixture.reportId()).cookie(fixture.ownerCookie()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Risk report")));
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
        assertThat(reportRepository.count()).isGreaterThan(0);
        return new UploadFixture(reportRepository.findAll().getLast().getId(), ownerCookie);
    }

    private record UploadFixture(java.util.UUID reportId, Cookie ownerCookie) {
    }
}
