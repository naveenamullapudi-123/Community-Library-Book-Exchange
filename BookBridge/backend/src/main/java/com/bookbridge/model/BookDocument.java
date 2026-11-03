package com.bookbridge.model;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import jakarta.validation.constraints.NotBlank;

@Document(collection = "books")
public record BookDocument(
        @Id String id,
        @NotBlank @Indexed String isbn,
        @NotBlank String title,
        @NotBlank String author,
        String genre,
        String language,
        String description,
        String publisher,
        String coverImageUrl,
        BookCondition condition,
        BookStatus status,
        @NotBlank String ownerId,
        Integer loanDurationDays,
        String pickupPreferences,
        String cityOrPostalCode,
        Instant createdAt,
        Instant updatedAt) {
}
