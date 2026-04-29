package com.policyinsight.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.policyinsight.repository.PolicyJobRepository;
import com.policyinsight.service.SampleReportService;
import jakarta.servlet.http.Cookie;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = {
        "app.ai.provider=gemini",
        "app.gemini.api-key="
})
@AutoConfigureMockMvc
class SampleControllerMissingGeminiKeyTests {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    PolicyJobRepository policyJobRepository;

    @Test
    void sampleWorksWhenGeminiKeyIsMissing() throws Exception {
        policyJobRepository.findByDemoKey(SampleReportService.SAMPLE_DEMO_KEY).ifPresent(policyJobRepository::delete);
        policyJobRepository.flush();

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
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Cedar Ridge Data Solutions")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("GEMINI_API_KEY"))));
    }
}
