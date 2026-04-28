package com.policyinsight.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.mock.env.MockEnvironment;

class DatabaseUrlEnvironmentPostProcessorTests {

    private final DatabaseUrlEnvironmentPostProcessor processor = new DatabaseUrlEnvironmentPostProcessor();

    @Test
    void springDatasourceUrlWinsOverDatabaseUrl() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("DATABASE_URL", "postgresql://railway:secret@example.com:5432/railway")
                .withProperty("SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost:5432/policyinsight");

        processor.postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty("SPRING_DATASOURCE_URL"))
                .isEqualTo("jdbc:postgresql://localhost:5432/policyinsight");
        assertThat(environment.getPropertySources().contains("databaseUrl")).isFalse();
    }

    @Test
    void railwayDatabaseUrlIsConvertedToSpringDatasourceProperties() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("DATABASE_URL", "postgresql://railway:secret@example.com/railway");

        processor.postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty("spring.datasource.url"))
                .isEqualTo("jdbc:postgresql://example.com:5432/railway");
        assertThat(environment.getProperty("spring.datasource.username")).isEqualTo("railway");
        assertThat(environment.getProperty("spring.datasource.password")).isEqualTo("secret");
    }
}
