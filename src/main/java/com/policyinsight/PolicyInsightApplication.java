package com.policyinsight;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class PolicyInsightApplication {

    public static void main(String[] args) {
        SpringApplication.run(PolicyInsightApplication.class, args);
    }
}

