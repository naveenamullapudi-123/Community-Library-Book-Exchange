package com.bookbridge.model;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import jakarta.validation.constraints.NotBlank;

@Document(collection = "notifications")
public record NotificationDocument(
        @Id String id,
        @NotBlank @Indexed String userId,
        @NotBlank String type,
        @NotBlank String message,
        String relatedEntityId,
        boolean read,
        Instant scheduledFor,
        Instant createdAt) {
}
