package com.bookbridge.model;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import jakarta.validation.constraints.NotBlank;

@Document(collection = "forumPosts")
public record ForumPostDocument(
        @Id String id,
        @NotBlank String authorId,
        @NotBlank String category,
        @NotBlank String title,
        @NotBlank String content,
        Instant eventDate,
        Instant createdAt,
        Instant updatedAt) {
}
