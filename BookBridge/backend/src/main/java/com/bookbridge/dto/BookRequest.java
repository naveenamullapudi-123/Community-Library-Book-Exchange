package com.bookbridge.dto;

import com.bookbridge.model.BookCondition;
import com.bookbridge.model.BookStatus;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record BookRequest(
        @NotBlank @Size(max = 20) String isbn,
        @NotBlank @Size(max = 200) String title,
        @NotBlank @Size(max = 160) String author,
        @Size(max = 80) String genre,
        @Size(max = 40) String language,
        @Size(max = 5000) String description,
        @Size(max = 160) String publisher,
        @Size(max = 1000) String coverImageUrl,
        @NotNull BookCondition condition,
        BookStatus status,
        @Positive @Max(365) Integer loanDurationDays,
        @Size(max = 500) String pickupPreferences,
        @Size(max = 120) String cityOrPostalCode) {
}
