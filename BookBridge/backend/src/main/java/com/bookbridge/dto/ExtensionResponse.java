package com.bookbridge.dto;

import java.time.Instant;

public record ExtensionResponse(
        String id,
        String loanId,
        String bookId,
        String bookTitle,
        String borrowerId,
        String borrowerName,
        Integer additionalDays,
        String message,
        String status,
        String responseMessage,
        Instant createdAt) {
}
