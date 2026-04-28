package com.policyinsight.controller;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.policyinsight.ai.AiAnalyzer;
import com.policyinsight.ai.AiAnalyzerException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class SampleControllerFailureTests {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    AiAnalyzer aiAnalyzer;

    @Test
    void sampleGenerationFailureRendersStyledErrorPage() throws Exception {
        when(aiAnalyzer.generateReport(anyList())).thenThrow(new AiAnalyzerException("Gemini API request failed."));

        mockMvc.perform(get("/sample"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Sample report is temporarily unavailable")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("The fictional sample report could not be generated")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Gemini API request failed"))));
    }
}
