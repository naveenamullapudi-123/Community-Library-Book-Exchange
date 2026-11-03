package com.bookbridge.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class TokenService {

    private static final Duration TOKEN_LIFETIME = Duration.ofHours(24);
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final byte[] signingKey;

    public TokenService(@Value("${bookbridge.token-secret:}") String configuredSecret) {
        if (configuredSecret == null || configuredSecret.isBlank()) {
            signingKey = new byte[32];
            new SecureRandom().nextBytes(signingKey);
        } else {
            if (configuredSecret.getBytes(StandardCharsets.UTF_8).length < 32) {
                throw new IllegalArgumentException("BOOKBRIDGE_TOKEN_SECRET must be at least 32 bytes.");
            }
            signingKey = configuredSecret.getBytes(StandardCharsets.UTF_8);
        }
    }

    public IssuedToken issue(String userId) {
        Instant expiresAt = Instant.now().plus(TOKEN_LIFETIME);
        String encodedUserId = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(userId.getBytes(StandardCharsets.UTF_8));
        String payload = encodedUserId + "." + expiresAt.getEpochSecond();
        return new IssuedToken(payload + "." + sign(payload), expiresAt);
    }

    public String verify(String token) {
        if (token == null) {
            return null;
        }

        String[] parts = token.split("\\.", -1);
        if (parts.length != 3 || parts[0].isBlank()) {
            return null;
        }

        String payload = parts[0] + "." + parts[1];
        if (!MessageDigest.isEqual(sign(payload).getBytes(StandardCharsets.US_ASCII),
                parts[2].getBytes(StandardCharsets.US_ASCII))) {
            return null;
        }

        try {
            if (Instant.ofEpochSecond(Long.parseLong(parts[1])).isBefore(Instant.now())) {
                return null;
            }
            return new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private String sign(String payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(signingKey, HMAC_ALGORITHM));
            byte[] signature = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(signature);
        } catch (java.security.GeneralSecurityException exception) {
            throw new IllegalStateException("Could not sign an authentication token.", exception);
        }
    }

    public record IssuedToken(String value, Instant expiresAt) {
    }
}
