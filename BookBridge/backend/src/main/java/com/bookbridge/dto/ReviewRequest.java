package com.bookbridge.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ReviewRequest(
        @NotNull @Min(1) @Max(5) Integer reliability,
        @NotNull @Min(1) @Max(5) Integer communication,
        @NotNull @Min(1) @Max(5) Integer careOfBooks,
        @Size(max = 2000) String comment) {
}
