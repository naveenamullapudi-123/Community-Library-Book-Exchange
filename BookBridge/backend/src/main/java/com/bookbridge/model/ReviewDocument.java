package com.bookbridge.model;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

@Document(collection = "reviews")
@CompoundIndex(name = "review_loan_reviewer_unique", def = "{'loanId': 1, 'reviewerId': 1}", unique = true)
public record ReviewDocument(
        @Id String id,
        @NotBlank @Indexed String loanId,
        @NotBlank String reviewerId,
        @NotBlank @Indexed String reviewedUserId,
        @Min(1) @Max(5) Integer reliability,
        @Min(1) @Max(5) Integer communication,
        @Min(1) @Max(5) Integer careOfBooks,
        String comment,
        Instant createdAt) {
}
