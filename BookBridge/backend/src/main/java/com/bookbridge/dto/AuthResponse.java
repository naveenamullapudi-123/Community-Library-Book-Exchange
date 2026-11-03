package com.bookbridge.dto;

import java.time.Instant;

public record AuthResponse(String token, Instant expiresAt, PublicUserResponse user) {
}
