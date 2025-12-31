package com.policyinsight.util;

import javax.annotation.Nonnull;
import java.util.Objects;

/**
 * Utility class for enforcing non-null contracts in a way that JDT null analysis can understand.
 * These methods serve as proof boundaries: JDT treats the return value as @Nonnull
 * after these methods execute successfully.
 */
public final class NonNulls {

    private NonNulls() {
        // Utility class
    }

    /**
     * Ensures that a value is non-null, throwing an IllegalStateException if it is null.
     * The return type is annotated @Nonnull so JDT can use this as a proof boundary.
     *
     * @param value The value to check (may be null)
     * @param message Error message if value is null
     * @param <T> Type of the value
     * @return The non-null value
     * @throws IllegalStateException if value is null
     */
    @Nonnull
    public static <T> T nn(T value, String message) {
        return Objects.requireNonNull(value, message);
    }

    /**
     * Ensures that a string is non-null and non-blank, throwing an IllegalStateException if it is null or blank.
     * The return type is annotated @Nonnull so JDT can use this as a proof boundary.
     *
     * @param value The string value to check (may be null or blank)
     * @param message Error message if value is null or blank
     * @return The non-null, non-blank string
     * @throws IllegalStateException if value is null or blank
     */
    @Nonnull
    public static String requireNonBlank(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(message);
        }
        return value;
    }
}

