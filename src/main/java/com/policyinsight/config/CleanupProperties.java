package com.policyinsight.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.cleanup")
public record CleanupProperties(
        boolean enabled,
        long retentionCompletedDays,
        long retentionFailedDays,
        long retentionExpiredShareDays,
        long jobStaleMinutes,
        long fixedDelayMs
) {
}
