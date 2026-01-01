package com.policyinsight.util;

import javax.annotation.Nonnull;

/**
 * Utility class for safe string handling to prevent null pointer exceptions
 * when passing strings to APIs that require @Nonnull parameters.
 */
public final class Strings {

    private Strings() {
        // Utility class
    }

    /**
     * Returns a safe non-null string for use with @Nonnull APIs.
     * If the input is null, returns an empty string.
     * If the input is blank, returns "unknown".
     *
     * @param value The string value (may be null)
     * @return A non-null string (empty string or "unknown" for null/blank input)
     */
    @Nonnull
    public static String safe(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return value;
    }

    /**
     * Returns a safe non-null string with a custom default value.
     *
     * @param value The string value (may be null)
     * @param defaultValue The default value to use if value is null or blank
     * @return A non-null string (defaultValue if value is null/blank, otherwise value)
     */
    @Nonnull
    public static String safe(String value, String defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue != null ? defaultValue : "unknown";
        }
        return value;
    }
}

