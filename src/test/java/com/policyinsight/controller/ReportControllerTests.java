package com.policyinsight.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.policyinsight.TestOwnerCookie;
import com.policyinsight.TestPdfFactory;
import com.policyinsight.repository.ReportRepository;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
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
    void reportContainsTechnicalDetails() throws Exception {
        UploadFixture fixture = upload("10.1.0.1");

        mockMvc.perform(get("/report/" + fixture.reportId()).cookie(fixture.ownerCookie()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Technical details")))
                .andExpect(content().string(containsString("sections extracted")))
                .andExpect(content().string(containsString("Report generated")));
    }

    @Test
    void ownerCanExportMarkdown() throws Exception {
        UploadFixture fixture = upload("10.1.0.2");

        mockMvc.perform(get("/report/" + fixture.reportId() + "/export.md").cookie(fixture.ownerCookie()))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.parseMediaType("text/markdown")))
                .andExpect(content().string(containsString("# PolicyInsight Report")))
                .andExpect(content().string(containsString("## Summary")))
                .andExpect(content().string(containsString("## Key Obligations")));
    }

    @Test
    void exportRequiresOwnerCookie() throws Exception {
        UploadFixture fixture = upload("10.1.0.3");

        mockMvc.perform(get("/report/" + fixture.reportId() + "/export.md"))
                .andExpect(status().isForbidden());
    }

    @Test
    void citationChipsLinkToMatchingSourceCards() throws Exception {
        UploadFixture fixture = upload("10.1.0.4");

        String body = mockMvc.perform(get("/report/" + fixture.reportId()).cookie(fixture.ownerCookie()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("href=\"#source-")))
                .andExpect(content().string(containsString("id=\"source-")))
                .andReturn().getResponse().getContentAsString();

        int chipCount = countOccurrences(body, "href=\"#source-");
        int cardCount = countOccurrences(body, "id=\"source-");
        assertThat(chipCount).isGreaterThan(0);
        assertThat(cardCount).isGreaterThan(0);
    }

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
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Policy analysis report")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Report sections")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Review closely")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Obligation")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Source evidence")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Matching excerpts from the document text cited in this report.")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("href=\"#source-")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Section 1")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Ask a question")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Grounded Q&amp;A")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("chunk"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("mock"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("fallback"))));
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
        Cookie ownerCookie = TestOwnerCookie.findOwnerCookie(result);
        UUID jobId = TestOwnerCookie.jobIdFromOwnerCookie(ownerCookie);
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

    private record UploadFixture(java.util.UUID reportId, Cookie ownerCookie) {
    }
}
