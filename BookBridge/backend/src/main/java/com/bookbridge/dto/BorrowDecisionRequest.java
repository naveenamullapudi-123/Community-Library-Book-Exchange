package com.bookbridge.dto;

import java.time.LocalDate;

import com.bookbridge.model.ExchangeMethod;

import jakarta.validation.constraints.Size;

public record BorrowDecisionRequest(
        Decision decision,
        @Size(max = 1000) String responseMessage,
        ExchangeMethod exchangeMethod,
        LocalDate handoverDate,
        @Size(max = 300) String handoverLocation) {

    public enum Decision {
        APPROVE,
        REJECT
    }
}
