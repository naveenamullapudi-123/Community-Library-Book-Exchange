package com.bookbridge.model;

import java.time.Instant;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import jakarta.validation.constraints.NotBlank;

@Document(collection = "wishlists")
public record WishlistDocument(
        @Id String id,
        @NotBlank String userId,
        List<String> bookIds,
        Instant createdAt) {
}
