package com.policyinsight.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.policyinsight.TestPdfFactory;
import com.policyinsight.repository.ReportRepository;
import com.policyinsight.repository.DocumentChunkRepository;
import jakarta.servlet.http.Cookie;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
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

    @Test
    void rejectsInvalidFile() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "bad.txt", "text/plain", "bad".getBytes());

        mockMvc.perform(multipart("/upload").file(file))
                .andExpect(status().isBadRequest());
    }

    @Test
    void uploadsPdfAndCreatesChunks() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "policy.pdf",
                "application/pdf",
                TestPdfFactory.pdfWithText("This policy requires written notice before termination.")
        );

        Cookie[] cookies = mockMvc.perform(multipart("/upload").file(file))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getCookies();

        assertThat(Arrays.stream(cookies).map(Cookie::getName))
                .anyMatch(name -> name.startsWith("PI_OWNER_"));
        assertThat(documentChunkRepository.count()).isEqualTo(1);
        assertThat(reportRepository.count()).isEqualTo(1);
    }
}
