package com.bookbridge.dto;

import java.time.Instant;
import java.time.LocalDate;

import com.bookbridge.model.ExchangeMethod;

public record LoanResponse(
        String id,
        String requestId,
        String bookId,
        String bookTitle,
        String ownerId,
        String ownerName,
        String borrowerId,
        String borrowerName,
        LocalDate startDate,
        LocalDate dueDate,
        LocalDate handoverDate,
        String handoverLocation,
        ExchangeMethod exchangeMethod,
        String status,
        Instant returnedAt) {
}
