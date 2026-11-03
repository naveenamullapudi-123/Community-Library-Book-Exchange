package com.bookbridge.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MessageCreateRequest(
        @NotBlank String recipientId,
        @NotBlank @Size(max = 4000) String content) {
}
