package com.bookbridge.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record ExtensionCreateRequest(
        @Min(1) @Max(365) Integer additionalDays,
        @Size(max = 1000) String message) {
}
