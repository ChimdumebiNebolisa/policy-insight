package com.policyinsight.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Service for generating and validating capability tokens.
 * Tokens are 32 random bytes encoded as base64url (without padding).
 * HMAC-SHA256 is used for storage (never store raw tokens).
 */
@Service
public class TokenService {

    private static final Logger logger = LoggerFactory.getLogger(TokenService.class);
    private static final int TOKEN_BYTES = 32;
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final String tokenSecret;
    private final SecureRandom secureRandom;

    public TokenService(@Value("${app.security.token-secret:change-me-in-production}") String tokenSecret) {
        this.tokenSecret = tokenSecret;
        this.secureRandom = new SecureRandom();

        if ("change-me-in-production".equals(tokenSecret)) {
            logger.warn("Using default token secret! Set APP_TOKEN_SECRET environment variable in production.");
        }
    }

    /**
     * Generates a random 32-byte token and returns it as base64url string (without padding).
     * @return base64url-encoded token string
     */
    public String generateToken() {
        byte[] tokenBytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(tokenBytes);
        // Use base64url encoding without padding
        return Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
    }

    /**
     * Computes HMAC-SHA256 of the token using the app secret as key.
     * Returns the HMAC as a base64 string (for storage in database).
     * @param rawToken the raw token string
     * @return base64-encoded HMAC digest
     */
    public String computeHmac(String rawToken) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            SecretKeySpec secretKeySpec = new SecretKeySpec(
                    tokenSecret.getBytes(StandardCharsets.UTF_8),
                    HMAC_ALGORITHM
            );
            mac.init(secretKeySpec);
            byte[] hmacBytes = mac.doFinal(rawToken.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hmacBytes);
        } catch (Exception e) {
            logger.error("Failed to compute HMAC for token", e);
            throw new RuntimeException("Failed to compute token HMAC", e);
        }
    }

    /**
     * Verifies a token against a stored HMAC using constant-time comparison.
     * @param rawToken the raw token to verify
     * @param storedHmac the stored HMAC (base64-encoded)
     * @return true if token matches, false otherwise
     */
    public boolean verifyToken(String rawToken, String storedHmac) {
        if (rawToken == null || storedHmac == null) {
            return false;
        }

        String computedHmac = computeHmac(rawToken);
        return constantTimeEquals(computedHmac, storedHmac);
    }

    /**
     * Constant-time string comparison to prevent timing attacks.
     * Uses MessageDigest.isEqual() which performs constant-time comparison.
     * @param a first string
     * @param b second string
     * @return true if strings are equal, false otherwise
     */
    public boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        if (a.length() != b.length()) {
            return false;
        }
        // Use MessageDigest.isEqual for constant-time comparison
        return MessageDigest.isEqual(
                a.getBytes(StandardCharsets.UTF_8),
                b.getBytes(StandardCharsets.UTF_8)
        );
    }
}

