package com.bookbridge.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class TokenServiceTest {

    private final TokenService tokenService =
            new TokenService("test-only-signing-key-that-is-at-least-32-bytes");

    @Test
    void verifiesIssuedTokensAndRejectsTamperedTokens() {
        String token = tokenService.issue("reader-1").value();

        assertEquals("reader-1", tokenService.verify(token));
        assertNull(tokenService.verify(token + "tampered"));
        assertNull(tokenService.verify("not-a-token"));
    }

    @Test
    void rejectsShortConfiguredSigningKey() {
        assertThrows(IllegalArgumentException.class, () -> new TokenService("short"));
    }
}
