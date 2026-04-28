package com.policyinsight.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.policyinsight.ai.AiAnalyzer;
import com.policyinsight.ai.GeminiAnalyzer;
import com.policyinsight.ai.MockAiAnalyzer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Configuration
public class AiAnalyzerConfig {

    @Bean
    @ConditionalOnProperty(prefix = "app.ai", name = "provider", havingValue = "gemini")
    public AiAnalyzer geminiAnalyzer(GeminiProperties properties, ObjectMapper objectMapper) {
        if (!StringUtils.hasText(properties.apiKey())) {
            throw new IllegalStateException("GEMINI_API_KEY is required when APP_AI_PROVIDER=gemini.");
        }
        return new GeminiAnalyzer(properties, objectMapper);
    }

    @Bean
    @ConditionalOnExpression("'${app.ai.provider:mock}' != 'gemini'")
    public AiAnalyzer mockAiAnalyzer() {
        return new MockAiAnalyzer();
    }
}
