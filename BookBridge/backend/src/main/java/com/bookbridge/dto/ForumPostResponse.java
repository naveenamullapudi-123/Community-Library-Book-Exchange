package com.bookbridge.dto;

import java.time.Instant;

public record ForumPostResponse(
        String id,
        String authorId,
        String authorName,
        String category,
        String title,
        String content,
        Instant eventDate,
        Instant createdAt,
        Instant updatedAt) {
}
