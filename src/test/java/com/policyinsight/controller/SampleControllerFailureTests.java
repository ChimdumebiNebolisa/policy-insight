package com.policyinsight.controller;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.policyinsight.ai.AiAnalyzer;
import com.policyinsight.ai.AiAnalyzerException;
import com.policyinsight.model.JobStatus;
import com.policyinsight.repository.PolicyJobRepository;
import com.policyinsight.repository.ReportRepository;
import com.policyinsight.service.SampleReportService;
import jakarta.servlet.http.Cookie;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = {
        "app.ai.provider=gemini",
        "app.gemini.api-key=fake-gemini-key"
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
    void sampleGenerationFailureFallsBackToReliableDemoReport() throws Exception {
        resetSampleJob();
        when(aiAnalyzer.generateReport(anyList())).thenThrow(new AiAnalyzerException("Gemini API request failed."));

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
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Mock overview")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Source 1")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Gemini API request failed"))));

        var sampleJob = policyJobRepository.findByDemoKey(SampleReportService.SAMPLE_DEMO_KEY).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(sampleJob.getStatus()).isEqualTo(JobStatus.COMPLETED);
        org.assertj.core.api.Assertions.assertThat(reportRepository.findByJobId(sampleJob.getId())).isPresent();
    }

    private void resetSampleJob() {
        policyJobRepository.findByDemoKey(SampleReportService.SAMPLE_DEMO_KEY).ifPresent(policyJobRepository::delete);
        policyJobRepository.flush();
    }
}
