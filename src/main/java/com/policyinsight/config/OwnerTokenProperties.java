package com.policyinsight.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.owner-token")
public record OwnerTokenProperties(long ttlMinutes) {
}
