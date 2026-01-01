package com.policyinsight.api;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.Collections;

/**
 * Verifies OIDC ID tokens sent by Google Pub/Sub push subscriptions.
 *
 * In production (cloudrun profile), verifies tokens using Google's GoogleIdTokenVerifier.
 * In test profile, verification can be disabled via pubsub.push.verification.enabled=false.
 */
@Component
public class PubSubTokenVerifier {

    private static final Logger logger = LoggerFactory.getLogger(PubSubTokenVerifier.class);

    private final String expectedAudience;
    private final String expectedEmail;
    private final boolean verificationEnabled;
    private GoogleIdTokenVerifier tokenVerifier;

    public PubSubTokenVerifier(
            @Value("${pubsub.push.expected-audience:}") String expectedAudience,
            @Value("${pubsub.push.expected-email:}") String expectedEmail,
            @Value("${pubsub.push.verification.enabled:true}") boolean verificationEnabled) {
        this.expectedAudience = expectedAudience;
        this.expectedEmail = expectedEmail;
        this.verificationEnabled = verificationEnabled;
    }

    @PostConstruct
    public void initialize() {
        if (!verificationEnabled) {
            logger.warn("Pub/Sub token verification is DISABLED - should only be used in test profile");
            return;
        }

        if (expectedAudience == null || expectedAudience.isEmpty()) {
            logger.warn("pubsub.push.expected-audience is not set - token verification may fail");
        }

        if (expectedEmail == null || expectedEmail.isEmpty()) {
            logger.warn("pubsub.push.expected-email is not set - token verification may fail");
        }

        try {
            this.tokenVerifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), new GsonFactory())
                    .setAudience(Collections.singletonList(expectedAudience))
                    .build();
            logger.info("Pub/Sub token verifier initialized (audience: {}, email: {})", expectedAudience, expectedEmail);
        } catch (Exception e) {
            logger.error("Failed to initialize Pub/Sub token verifier", e);
            throw new RuntimeException("Failed to initialize Pub/Sub token verifier", e);
        }
    }

    /**
     * Verifies an OIDC ID token from the Authorization header.
     *
     * Enforces Pub/Sub push requirements:
     * - Authorization header must be present and in "Bearer <token>" format
     * - Token signature must be valid
     * - email claim must match configured push auth service account email
     * - email_verified claim must be true
     * - aud claim must match configured audience (if set)
     *
     * @param authorizationHeader The Authorization header value (should be "Bearer <token>")
     * @return true if token is valid, false otherwise
     */
    public boolean verifyToken(String authorizationHeader) {
        if (!verificationEnabled) {
            logger.debug("Token verification disabled, accepting token");
            return true;
        }

        // Reject missing/blank Authorization header
        if (authorizationHeader == null || authorizationHeader.trim().isEmpty()) {
            logger.warn("Pub/Sub push message rejected: missing Authorization header");
            return false;
        }

        if (!authorizationHeader.startsWith("Bearer ")) {
            logger.warn("Pub/Sub push message rejected: Authorization header must start with 'Bearer '");
            return false;
        }

        String token = authorizationHeader.substring(7).trim(); // Remove "Bearer " prefix
        if (token.isEmpty()) {
            logger.warn("Pub/Sub push message rejected: empty token after 'Bearer '");
            return false;
        }

        try {
            GoogleIdToken idToken = tokenVerifier.verify(token);
            if (idToken == null) {
                logger.warn("Pub/Sub push message rejected: token signature verification failed");
                return false;
            }

            GoogleIdToken.Payload payload = idToken.getPayload();
            String email = payload.getEmail();
            Boolean emailVerified = payload.getEmailVerified();
            String audience = (String) payload.get("aud");

            // Verify email matches expected service account
            if (expectedEmail != null && !expectedEmail.isEmpty()) {
                if (email == null || !expectedEmail.equals(email)) {
                    logger.warn("Pub/Sub push message rejected: token email {} does not match expected email {}", email, expectedEmail);
                    return false;
                }
            } else {
                logger.warn("pubsub.push.expected-email is not configured - cannot verify email claim");
                return false;
            }

            // Verify email_verified claim
            if (emailVerified == null || !emailVerified) {
                logger.warn("Pub/Sub push message rejected: email_verified claim is missing or false (email: {})", email);
                return false;
            }

            // Verify audience claim (if configured)
            if (expectedAudience != null && !expectedAudience.isEmpty()) {
                if (audience == null || !expectedAudience.equals(audience)) {
                    logger.warn("Pub/Sub push message rejected: token audience {} does not match expected audience {}", audience, expectedAudience);
                    return false;
                }
            }

            logger.debug("Token verified successfully (email: {}, email_verified: {}, aud: {})", email, emailVerified, audience);
            return true;
        } catch (Exception e) {
            logger.warn("Pub/Sub push message rejected: token verification exception: {}", e.getMessage());
            return false;
        }
    }
}


