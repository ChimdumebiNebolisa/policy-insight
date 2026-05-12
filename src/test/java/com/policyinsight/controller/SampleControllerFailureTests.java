package com.policyinsight.controller;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.policyinsight.ai.AiAnalyzer;
import com.policyinsight.model.JobStatus;
import com.policyinsight.repository.PolicyJobRepository;
import com.policyinsight.repository.ReportRepository;
import com.policyinsight.service.SampleReportService;
import jakarta.servlet.http.Cookie;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = {
        "app.ai.provider=gemini",
        "app.gemini.api-key=invalid-test-key"
})
@AutoConfigureMockMvc
class SampleControllerFailureTests {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    AiAnalyzer aiAnalyzer;

    @Autowired
    PolicyJobRepository policyJobRepository;

    @Autowired
    ReportRepository reportRepository;

    @Test
    void sampleUsesDeterministicReportAndDoesNotCallConfiguredGeminiAnalyzer() throws Exception {
        resetSampleJob();

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
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Cedar Ridge Data Solutions")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Source 1")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Gemini API request failed"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("mock"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("fallback"))));
        verifyNoInteractions(aiAnalyzer);

        var sampleJob = policyJobRepository.findByDemoKey(SampleReportService.SAMPLE_DEMO_KEY).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(sampleJob.getStatus()).isEqualTo(JobStatus.COMPLETED);
        org.assertj.core.api.Assertions.assertThat(reportRepository.findByJobId(sampleJob.getId())).isPresent();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "vendor-agreement",
            "privacy-policy",
            "employment-policy",
            "campus-student-policy"
    })
    void namedSamplesStayDeterministicAndGeminiFree(String sampleKey) throws Exception {
        mockMvc.perform(get("/sample/" + sampleKey))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/report/*"));
        verifyNoInteractions(aiAnalyzer);
    }

    private void resetSampleJob() {
        policyJobRepository.findByDemoKey(SampleReportService.SAMPLE_DEMO_KEY).ifPresent(policyJobRepository::delete);
        policyJobRepository.flush();
    }
}
