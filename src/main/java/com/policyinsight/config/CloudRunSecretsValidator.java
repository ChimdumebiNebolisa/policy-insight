package com.policyinsight.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
@Profile("cloudrun")
public class CloudRunSecretsValidator {

    private static final Logger logger = LoggerFactory.getLogger(CloudRunSecretsValidator.class);
    private static final String DEFAULT_TOKEN_SECRET = "change-me-in-production";

    private final Environment environment;

    public CloudRunSecretsValidator(Environment environment) {
        this.environment = environment;
    }

    @PostConstruct
    public void validateSecrets() {
        String tokenSecret = environment.getProperty("app.security.token-secret");
        String dbPassword = environment.getProperty("spring.datasource.password");

        if (dbPassword == null || dbPassword.trim().isEmpty()) {
            throw new IllegalStateException("DB_PASSWORD is required in cloudrun profile (spring.datasource.password is empty)");
        }

        if (tokenSecret == null || tokenSecret.trim().isEmpty() || DEFAULT_TOKEN_SECRET.equals(tokenSecret)) {
            throw new IllegalStateException("APP_TOKEN_SECRET must be set to a non-default value in cloudrun profile");
        }

        logger.info("Cloud Run secrets validation passed.");
    }
}
