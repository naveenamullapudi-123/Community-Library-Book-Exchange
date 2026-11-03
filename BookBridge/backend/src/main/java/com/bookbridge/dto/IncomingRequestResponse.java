package com.bookbridge.dto;

import java.time.Instant;
import java.time.LocalDate;

public record IncomingRequestResponse(
        String id,
        String bookId,
        String bookTitle,
        PublicUserResponse borrower,
        LocalDate desiredStartDate,
        Integer loanDurationDays,
        String personalMessage,
        String status,
        Instant createdAt) {
}
