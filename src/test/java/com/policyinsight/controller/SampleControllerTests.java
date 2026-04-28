package com.policyinsight.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.policyinsight.repository.ReportRepository;
import jakarta.servlet.http.Cookie;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class SampleControllerTests {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ReportRepository reportRepository;

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
                .andExpect(status().isOk());
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
}
