package com.bookbridge.model;

import java.time.Instant;
import java.time.LocalDate;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

@Document(collection = "borrowRequests")
public record BorrowRequestDocument(
        @Id String id,
        @NotBlank String bookId,
        @NotBlank String ownerId,
        @NotBlank String borrowerId,
        LocalDate desiredStartDate,
        @Positive Integer loanDurationDays,
        String personalMessage,
        String status,
        String responseMessage,
        Instant createdAt) {
}
