package com.policyinsight;

import jakarta.servlet.http.Cookie;
import java.util.Arrays;
import java.util.UUID;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Test helpers for PolicyInsight owner cookies set on upload/paste responses.
 * <p>
 * Cookie names are {@code PI_OWNER_} + job UUID without dashes (see {@link com.policyinsight.controller.DocumentController#ownerCookieName}).
 * Resolving the job id from the cookie avoids {@code repository.findAll().getLast()} and similar patterns, which are
 * undefined order and unsafe when the test database already contains rows from other tests.
 */
public final class TestOwnerCookie {

    private static final String PREFIX = "PI_OWNER_";

    private TestOwnerCookie() {}

    public static Cookie findOwnerCookie(MvcResult result) {
        return findOwnerCookie(result.getResponse().getCookies());
    }

    public static Cookie findOwnerCookie(Cookie[] cookies) {
        return Arrays.stream(cookies)
                .filter(c -> c.getName().startsWith(PREFIX))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No " + PREFIX + " cookie in response"));
    }

    public static UUID jobIdFromOwnerCookie(Cookie ownerCookie) {
        String name = ownerCookie.getName();
        if (!name.startsWith(PREFIX)) {
            throw new IllegalStateException("Expected owner cookie name to start with " + PREFIX + ", got: " + name);
        }
        String hex = name.substring(PREFIX.length());
        if (hex.length() != 32) {
            throw new IllegalStateException("Expected 32 hex chars in owner cookie name, got length " + hex.length());
        }
        String uuid = hex.substring(0, 8)
                + "-"
                + hex.substring(8, 12)
                + "-"
                + hex.substring(12, 16)
                + "-"
                + hex.substring(16, 20)
                + "-"
                + hex.substring(20, 32);
        return UUID.fromString(uuid);
    }
}
