package com.bookbridge.dto;

import com.bookbridge.model.BookStatus;

import jakarta.validation.constraints.NotNull;

public record BookStatusRequest(@NotNull BookStatus status) {
}
