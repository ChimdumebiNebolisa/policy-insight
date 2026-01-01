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
     * Enable scheduling only when the worker is enabled.
     * This prevents unnecessary scheduling overhead when running web-only instances.
     */
    @EnableScheduling
    @ConditionalOnProperty(prefix = "policyinsight.worker", name = "enabled", havingValue = "true")
    static class SchedulingConfiguration {
    }
}

