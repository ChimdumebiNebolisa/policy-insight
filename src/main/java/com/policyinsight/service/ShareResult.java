package com.policyinsight.service;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

public record ShareResult(String token, String url, Instant expiresAt) {

    public String expiresAtDisplay() {
        if (expiresAt == null) {
            return "";
        }
        return DateTimeFormatter.ofPattern("MMM d, yyyy")
                .withZone(ZoneOffset.UTC)
                .format(expiresAt);
    }
}
