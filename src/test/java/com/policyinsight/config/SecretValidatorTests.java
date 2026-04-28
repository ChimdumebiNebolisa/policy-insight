package com.policyinsight.config;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.mock.env.MockEnvironment;

class SecretValidatorTests {

    @Test
    void rejectsDefaultSecretWhenGeminiIsEnabled() {
        SecretValidator validator = new SecretValidator(new MockEnvironment(), "dev-only-change-me", "gemini");

        assertThatThrownBy(() -> validator.run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("APP_TOKEN_SECRET");
    }

    @Test
    void rejectsShortSecretOnRailway() {
        MockEnvironment environment = new MockEnvironment().withProperty("RAILWAY_ENVIRONMENT", "production");
        SecretValidator validator = new SecretValidator(environment, "short", "mock");

        assertThatThrownBy(() -> validator.run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("APP_TOKEN_SECRET");
    }
}
