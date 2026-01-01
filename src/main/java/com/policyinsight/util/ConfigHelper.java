package com.policyinsight.util;

import org.springframework.core.env.Environment;

/**
 * Utility class for safely reading environment variables and configuration properties.
 * Provides fail-fast behavior for required values and safe defaults for optional ones.
 */
public final class ConfigHelper {

    private ConfigHelper() {
        // Utility class
    }

    /**
     * Reads a required environment variable or configuration property.
     * Throws IllegalStateException if the value is missing or blank.
     *
     * @param env The Spring Environment
     * @param key The property key (supports both ${key} and ${KEY} formats)
     * @return The non-null, non-blank property value
     * @throws IllegalStateException if the property is missing or blank
     */
    public static String requiredProperty(Environment env, String key) {
        String value = env.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    String.format("Required configuration property '%s' is missing or blank", key));
        }
        return value;
    }

    /**
     * Reads an optional environment variable or configuration property with a default value.
     *
     * @param env The Spring Environment
     * @param key The property key
     * @param defaultValue The default value to use if the property is missing or blank
     * @return The property value or defaultValue if missing/blank
     */
    public static String optionalProperty(Environment env, String key, String defaultValue) {
        String value = env.getProperty(key);
        if (value == null || value.isBlank()) {
            return defaultValue != null ? defaultValue : "";
        }
        return value;
    }

    /**
     * Reads a required environment variable from System.getenv().
     * Throws IllegalStateException if the value is missing or blank.
     *
     * @param key The environment variable key
     * @return The non-null, non-blank environment variable value
     * @throws IllegalStateException if the environment variable is missing or blank
     */
    public static String requiredEnv(String key) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    String.format("Required environment variable '%s' is missing or blank", key));
        }
        return value;
    }

    /**
     * Reads an optional environment variable from System.getenv() with a default value.
     *
     * @param key The environment variable key
     * @param defaultValue The default value to use if the variable is missing or blank
     * @return The environment variable value or defaultValue if missing/blank
     */
    public static String optionalEnv(String key, String defaultValue) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            return defaultValue != null ? defaultValue : "";
        }
        return value;
    }
}

