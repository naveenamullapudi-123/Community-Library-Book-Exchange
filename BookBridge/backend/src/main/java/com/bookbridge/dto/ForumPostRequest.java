package com.bookbridge.dto;

import java.time.Instant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ForumPostRequest(
        @NotBlank @Size(max = 40) String category,
        @NotBlank @Size(max = 160) String title,
        @NotBlank @Size(max = 10000) String content,
        Instant eventDate) {
}
