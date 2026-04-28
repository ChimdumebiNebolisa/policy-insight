package com.policyinsight.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.policyinsight.model.JobStatus;
import com.policyinsight.model.PolicyJob;
import com.policyinsight.repository.PolicyJobRepository;
import com.policyinsight.repository.ReportRepository;
import com.policyinsight.service.SampleReportService;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class SampleControllerTests {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ReportRepository reportRepository;

    @Autowired
    PolicyJobRepository policyJobRepository;

    @Test
    void samplePdfIsBundledAsClasspathResource() throws Exception {
        ClassPathResource resource = new ClassPathResource("samples/fictional_business_agreement.pdf");

        assertThat(resource.exists()).isTrue();
        assertThat(Thread.currentThread().getContextClassLoader().getResource("samples/fictional_business_agreement.pdf"))
                .isNotNull();
        assertThat(resource.getContentAsByteArray()).startsWith("%PDF-".getBytes());
    }

    @Test
    void sampleRouteCreatesOwnerCookieAndRedirectsToReport() throws Exception {
        MvcResult result = mockMvc.perform(get("/sample"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/report/*"))
                .andReturn();

        Cookie ownerCookie = Arrays.stream(result.getResponse().getCookies())
                .filter(cookie -> cookie.getName().startsWith("PI_OWNER_"))
                .findFirst()
                .orElseThrow();

        mockMvc.perform(get(result.getResponse().getRedirectedUrl()).cookie(ownerCookie))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Fictional sample. Demonstration only.")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Source 1")));
    }

    @Test
    void sampleRouteReusesExistingSampleReport() throws Exception {
        mockMvc.perform(get("/sample"))
                .andExpect(status().is3xxRedirection());
        long reportsAfterFirstClick = reportRepository.count();

        mockMvc.perform(get("/sample"))
                .andExpect(status().is3xxRedirection());

        assertThat(reportRepository.count()).isEqualTo(reportsAfterFirstClick);
    }

    @Test
    void sampleRouteRepairsExistingDemoJobWithoutReport() throws Exception {
        resetSampleJob();
        PolicyJob brokenJob = new PolicyJob("broken-owner-token-hash", Instant.now().plusSeconds(3600));
        brokenJob.setDemoKey(SampleReportService.SAMPLE_DEMO_KEY);
        brokenJob.setStatus(JobStatus.COMPLETED);
        policyJobRepository.saveAndFlush(brokenJob);

        mockMvc.perform(get("/sample"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/report/*"));

        PolicyJob repairedJob = policyJobRepository.findByDemoKey(SampleReportService.SAMPLE_DEMO_KEY).orElseThrow();
        assertThat(repairedJob.getId()).isNotEqualTo(brokenJob.getId());
        assertThat(repairedJob.getStatus()).isEqualTo(JobStatus.COMPLETED);
        assertThat(reportRepository.findByJobId(repairedJob.getId())).isPresent();
    }

    @Test
    void landingPageShowsSharpValuePropositionAndSampleCta() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("<h1 id=\"page-title\">PolicyInsight</h1>")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Review policy and agreement PDFs with AI-generated reports linked to source text.")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Analyze your document")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Analyze PDF")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Open sample report")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Fictional sample. Demonstration only.")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("hero-grid"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("preview-panel"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("feature-grid"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("chunk 01"))));
    }

    private void resetSampleJob() {
        policyJobRepository.findByDemoKey(SampleReportService.SAMPLE_DEMO_KEY).ifPresent(policyJobRepository::delete);
        policyJobRepository.flush();
    }
}
