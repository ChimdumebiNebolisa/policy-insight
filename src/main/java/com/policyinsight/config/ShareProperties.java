package com.policyinsight.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.share")
public record ShareProperties(long ttlDays) {
}
