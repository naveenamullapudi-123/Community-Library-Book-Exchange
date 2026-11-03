package com.bookbridge.dto;

import java.time.Instant;
import java.time.LocalDate;

public record BorrowRequestResponse(
        String id,
        String bookId,
        String bookTitle,
        String ownerId,
        String borrowerId,
        LocalDate desiredStartDate,
        Integer loanDurationDays,
        String personalMessage,
        String status,
        String responseMessage,
        Instant createdAt) {
}
