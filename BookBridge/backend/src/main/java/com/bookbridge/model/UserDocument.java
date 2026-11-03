package com.bookbridge.model;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@Document(collection = "users")
public record UserDocument(
        @Id String id,
        @NotBlank String displayName,
        @NotBlank @Email @Indexed(unique = true) String email,
        @NotBlank String passwordHash,
        String cityOrPostalCode,
        Instant createdAt) {
}
