package com.policyinsight.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.policyinsight.TestPdfFactory;
import com.policyinsight.model.Report;
import com.policyinsight.model.ShareLink;
import com.policyinsight.repository.PolicyJobRepository;
import com.policyinsight.repository.ReportRepository;
import com.policyinsight.repository.ShareLinkRepository;
import com.policyinsight.security.TokenService;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
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
class ShareControllerTests {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ReportRepository reportRepository;

    @Autowired
    ShareLinkRepository shareLinkRepository;

    @Autowired
    TokenService tokenService;

    @Autowired
    PolicyJobRepository policyJobRepository;

    @Test
    void revokeShareLinkMakesItInvalid() throws Exception {
        UploadFixture fixture = upload("10.2.0.1");
        String token = tokenService.generateToken();
        Report report = reportRepository.findById(fixture.reportId()).orElseThrow();
        shareLinkRepository.save(new ShareLink(report, tokenService.hashToken(token), Instant.now().plusSeconds(3600)));

        mockMvc.perform(get("/shared/" + token)).andExpect(status().isOk());

        mockMvc.perform(post("/share/" + fixture.reportId() + "/revoke").cookie(fixture.ownerCookie()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Link revoked")));

        mockMvc.perform(get("/shared/" + token)).andExpect(status().isNotFound());
    }

    @Test
    void revokeRequiresOwnerCookie() throws Exception {
        UploadFixture fixture = upload("10.2.0.2");

        mockMvc.perform(post("/share/" + fixture.reportId() + "/revoke"))
                .andExpect(status().isForbidden());
    }

    @Test
    void regenerateShareLinkInvalidatesOldAndCreatesNew() throws Exception {
        UploadFixture fixture = upload("10.2.0.3");
        String oldToken = tokenService.generateToken();
        Report report = reportRepository.findById(fixture.reportId()).orElseThrow();
        shareLinkRepository.save(new ShareLink(report, tokenService.hashToken(oldToken), Instant.now().plusSeconds(3600)));

        mockMvc.perform(get("/shared/" + oldToken)).andExpect(status().isOk());

        mockMvc.perform(post("/share/" + fixture.reportId() + "/regenerate").cookie(fixture.ownerCookie()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/shared/")));

        mockMvc.perform(get("/shared/" + oldToken)).andExpect(status().isNotFound());
        assertThat(shareLinkRepository.countByReport_Id(fixture.reportId())).isEqualTo(1);
    }

    @Test
    void regenerateRequiresOwnerCookie() throws Exception {
        UploadFixture fixture = upload("10.2.0.4");

        mockMvc.perform(post("/share/" + fixture.reportId() + "/regenerate"))
                .andExpect(status().isForbidden());
    }

    @Test
    void shareResultShowsExpiresAt() throws Exception {
        UploadFixture fixture = upload("10.2.0.5");

        mockMvc.perform(post("/share/" + fixture.reportId()).cookie(fixture.ownerCookie()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Expires")));
    }

    @Test
    void createsShareLinkForOwner() throws Exception {
        UploadFixture fixture = upload();

        MvcResult result = mockMvc.perform(post("/share/" + fixture.reportId()).cookie(fixture.ownerCookie()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/shared/")))
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).contains("Shared report link created");
        assertThat(shareLinkRepository.countByReport_Id(fixture.reportId())).isEqualTo(1);
    }

    @Test
    void sharedReportAccessWorks() throws Exception {
        UploadFixture fixture = upload();
        String token = tokenService.generateToken();
        Report report = reportRepository.findById(fixture.reportId()).orElseThrow();
        shareLinkRepository.save(new ShareLink(report, tokenService.hashToken(token), Instant.now().plusSeconds(3600)));

        mockMvc.perform(get("/shared/" + token))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Shared read-only report")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Source evidence")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("chunk"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("mock"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("fallback"))));
    }

    @Test
    void invalidSharedLinkReturnsNotFound() throws Exception {
        mockMvc.perform(get("/shared/" + UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    void expiredSharedLinkReturnsNotFound() throws Exception {
        UploadFixture fixture = upload();
        String token = tokenService.generateToken();
        Report report = reportRepository.findById(fixture.reportId()).orElseThrow();
        shareLinkRepository.save(new ShareLink(report, tokenService.hashToken(token), Instant.now().minusSeconds(1)));

        mockMvc.perform(get("/shared/" + token))
                .andExpect(status().isNotFound());
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
