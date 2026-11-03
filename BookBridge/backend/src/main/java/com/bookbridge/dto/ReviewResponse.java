package com.bookbridge.dto;

import java.time.Instant;

public record ReviewResponse(
        String id,
        String loanId,
        String reviewerId,
        String reviewerName,
        String reviewedUserId,
        String reviewedUserName,
        int reliability,
        int communication,
        int careOfBooks,
        double averageRating,
        String comment,
        Instant createdAt) {
}
