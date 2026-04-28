package com.policyinsight.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.policyinsight.ai.AiAnalyzer;
import com.policyinsight.ai.GeminiAnalyzer;
import com.policyinsight.ai.MockAiAnalyzer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class AiAnalyzerConfigTests {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class))
            .withUserConfiguration(TestPropertiesConfig.class, AiAnalyzerConfig.class)
            .withPropertyValues(
                    "app.gemini.model=gemini-1.5-flash",
                    "app.gemini.timeout-seconds=30"
            );

    @Test
    void mockProviderStartsWithMockAnalyzerAndNoGeminiKey() {
        contextRunner
                .withPropertyValues("app.ai.provider=mock")
                .run(context -> {
                    assertThat(context).hasSingleBean(AiAnalyzer.class);
                    assertThat(context.getBean(AiAnalyzer.class)).isInstanceOf(MockAiAnalyzer.class);
                });
    }

    @Test
    void geminiProviderStartsWithGeminiAnalyzerWhenKeyIsPresent() {
        contextRunner
                .withPropertyValues(
                        "app.ai.provider=gemini",
                        "app.gemini.api-key=fake-gemini-key"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(AiAnalyzer.class);
                    assertThat(context.getBean(AiAnalyzer.class)).isInstanceOf(GeminiAnalyzer.class);
                });
    }

    @Test
    void geminiProviderFailsClearlyWhenKeyIsMissing() {
        contextRunner
                .withPropertyValues("app.ai.provider=gemini")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasMessageContaining("GEMINI_API_KEY");
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(GeminiProperties.class)
    static class TestPropertiesConfig {
    }
}
