package com.bookbridge.dto;

import java.time.Instant;

import com.bookbridge.model.BookCondition;
import com.bookbridge.model.BookStatus;

public record BookResponse(
        String id,
        String isbn,
        String title,
        String author,
        String genre,
        String language,
        String description,
        String publisher,
        String coverImageUrl,
        BookCondition condition,
        BookStatus status,
        PublicUserResponse owner,
        Integer loanDurationDays,
        String pickupPreferences,
        Instant createdAt,
        Instant updatedAt) {
}
