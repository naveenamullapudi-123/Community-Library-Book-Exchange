package com.bookbridge.dto;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record BorrowRequestCreateRequest(
        @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desiredStartDate,
        @NotNull @Min(1) @Max(365) Integer loanDurationDays,
        @Size(max = 1000) String personalMessage) {
}
