package com.policyinsight.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class SecretValidator implements ApplicationRunner {

    private static final String DEV_SECRET = "dev-only-change-me";

    private final Environment environment;
    private final String tokenSecret;
    private final String aiProvider;

    public SecretValidator(
            Environment environment,
            @Value("${app.token-secret}") String tokenSecret,
            @Value("${app.ai.provider:mock}") String aiProvider
    ) {
        this.environment = environment;
        this.tokenSecret = tokenSecret;
        this.aiProvider = aiProvider;
    }

    @Override
    public void run(ApplicationArguments args) {
        boolean railway = environment.getProperty("RAILWAY_ENVIRONMENT") != null;
        boolean liveAi = "gemini".equalsIgnoreCase(aiProvider);
        if ((railway || liveAi) && (DEV_SECRET.equals(tokenSecret) || tokenSecret.length() < 32)) {
            throw new IllegalStateException("APP_TOKEN_SECRET must be set to a long random value.");
        }
    }
}
