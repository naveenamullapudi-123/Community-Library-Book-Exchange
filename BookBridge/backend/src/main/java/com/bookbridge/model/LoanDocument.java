package com.bookbridge.model;

import java.time.Instant;
import java.time.LocalDate;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import jakarta.validation.constraints.NotBlank;

@Document(collection = "loans")
public record LoanDocument(
        @Id String id,
        @NotBlank String requestId,
        @NotBlank String bookId,
        @NotBlank String ownerId,
        @NotBlank String borrowerId,
        LocalDate startDate,
        LocalDate dueDate,
        LocalDate handoverDate,
        String handoverLocation,
        ExchangeMethod exchangeMethod,
        String status,
        Instant returnedAt) {
}
