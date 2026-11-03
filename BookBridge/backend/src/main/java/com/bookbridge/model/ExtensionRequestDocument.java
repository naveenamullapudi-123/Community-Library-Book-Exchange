package com.bookbridge.model;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

@Document(collection = "extensionRequests")
public record ExtensionRequestDocument(
        @Id String id,
        @NotBlank String loanId,
        @NotBlank String bookId,
        @NotBlank String ownerId,
        @NotBlank String borrowerId,
        @Positive Integer additionalDays,
        String message,
        String status,
        String responseMessage,
        Instant createdAt,
        Instant updatedAt) {
}
