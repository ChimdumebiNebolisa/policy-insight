package com.policyinsight;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
public class PolicyInsightApplication {

    public static void main(String[] args) {
        SpringApplication.run(PolicyInsightApplication.class, args);
    }

    /**
     * Enable scheduling only when local processing mode is enabled.
     * This prevents unnecessary scheduling overhead when using GCP Pub/Sub worker.
     */
    @EnableScheduling
    @ConditionalOnProperty(name = "app.processing.mode", havingValue = "local", matchIfMissing = true)
    static class SchedulingConfiguration {
    }
}

