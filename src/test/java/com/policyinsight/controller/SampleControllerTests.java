package com.policyinsight.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.policyinsight.ai.AiAnalyzer;
import com.policyinsight.ai.dto.RiskReport;
import com.policyinsight.model.JobStatus;
import com.policyinsight.model.PolicyJob;
import com.policyinsight.repository.DocumentChunkRepository;
import com.policyinsight.repository.PolicyJobRepository;
import com.policyinsight.repository.ReportRepository;
import com.policyinsight.service.PdfTextExtractor;
import com.policyinsight.service.SampleReportService;
import jakarta.servlet.http.Cookie;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
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

    @Autowired
    DocumentChunkRepository documentChunkRepository;

    @Autowired
    PdfTextExtractor pdfTextExtractor;

    @Autowired
    ObjectMapper objectMapper;

    @MockBean
    AiAnalyzer aiAnalyzer;

    @Test
    void samplePdfIsBundledAsClasspathResource() throws Exception {
        assertThat(Files.exists(Path.of("src/main/resources/samples/fictional_business_agreement.pdf"))).isTrue();
        ClassPathResource resource = new ClassPathResource("samples/fictional_business_agreement.pdf");

        assertThat(resource.exists()).isTrue();
        assertThat(Thread.currentThread().getContextClassLoader().getResource("samples/fictional_business_agreement.pdf"))
                .isNotNull();
        assertThat(resource.getContentAsByteArray()).startsWith("%PDF-".getBytes());
    }

    @Test
    void samplePdfTextContainsExpectedAgreementTerms() throws Exception {
        ClassPathResource resource = new ClassPathResource("samples/fictional_business_agreement.pdf");

        String text = pdfTextExtractor.extractText(resource.getContentAsByteArray());

        assertThat(text)
                .contains("USD $8,000")
                .contains("fifteen (15) days")
                .contains("ten (10) business days")
                .contains("State of Columbia");
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
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Cedar Ridge Data Solutions, LLC will provide data operations support")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Review focus areas")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Payment terms")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Acceptance window")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Data handling")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Liability cap")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Termination rights")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Scope exclusions")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Why it matters")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Cited evidence")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Short excerpts from sources cited by the report.")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Fees")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Liability")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("href=\"#source-")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Source 1")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Q&amp;A is available for uploaded documents.")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Ask a question"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Grounded Q&amp;A"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("chunk"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("mock"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("fallback"))));
        verifyNoInteractions(aiAnalyzer);
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
    void sampleReportCitesMultipleDistinctSources() throws Exception {
        resetSampleJob();
        mockMvc.perform(get("/sample"))
                .andExpect(status().is3xxRedirection());

        PolicyJob sampleJob = policyJobRepository.findByDemoKey(SampleReportService.SAMPLE_DEMO_KEY).orElseThrow();
        RiskReport report = objectMapper.readValue(
                reportRepository.findByJobId(sampleJob.getId()).orElseThrow().getContent(),
                RiskReport.class
        );
        Set<UUID> citedIds = Stream.of(
                        report.summaryBullets(),
                        report.obligations(),
                        report.restrictions(),
                        report.terminationTriggers(),
                        report.riskTaxonomy()
                )
                .flatMap(java.util.List::stream)
                .flatMap(claim -> claim.chunkIds().stream())
                .collect(java.util.stream.Collectors.toSet());

        UUID firstSourceId = documentChunkRepository.findByJobIdOrderByChunkIndex(sampleJob.getId()).getFirst().getId();
        assertThat(citedIds).hasSizeGreaterThan(1);
        assertThat(citedIds).anyMatch(id -> !id.equals(firstSourceId));
        verifyNoInteractions(aiAnalyzer);
    }

    @Test
    void sampleQaIsBlockedIfPostedDirectly() throws Exception {
        SampleFixture fixture = openSample();

        mockMvc.perform(post("/qa/" + fixture.reportId())
                        .cookie(fixture.ownerCookie())
                        .param("question", "Can I ask about the sample?"))
                .andExpect(status().isForbidden())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Q&amp;A is available for uploaded documents.")));
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
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("chunk"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("mock"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("fallback"))));
    }

    private void resetSampleJob() {
        policyJobRepository.findByDemoKey(SampleReportService.SAMPLE_DEMO_KEY).ifPresent(policyJobRepository::delete);
        policyJobRepository.flush();
    }

    private SampleFixture openSample() throws Exception {
        MvcResult result = mockMvc.perform(get("/sample"))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        Cookie ownerCookie = Arrays.stream(result.getResponse().getCookies())
                .filter(cookie -> cookie.getName().startsWith("PI_OWNER_"))
                .findFirst()
                .orElseThrow();
        UUID reportId = UUID.fromString(result.getResponse().getRedirectedUrl().substring("/report/".length()));
        return new SampleFixture(reportId, ownerCookie);
    }

    private record SampleFixture(UUID reportId, Cookie ownerCookie) {
    }
}
