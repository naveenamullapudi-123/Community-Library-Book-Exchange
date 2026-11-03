package com.bookbridge.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.bookbridge.dto.ReviewRequest;
import com.bookbridge.dto.ReviewResponse;
import com.bookbridge.service.ReviewService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/loans/{loanId}/reviews")
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ReviewResponse create(
            @AuthenticationPrincipal String reviewerId,
            @PathVariable String loanId,
            @Valid @RequestBody ReviewRequest request) {
        return reviewService.create(reviewerId, loanId, request);
    }

    @GetMapping
    public List<ReviewResponse> reviewsForLoan(
            @AuthenticationPrincipal String userId,
            @PathVariable String loanId) {
        return reviewService.reviewsForLoan(userId, loanId);
    }

    @GetMapping("/users/{userId}")
    public List<ReviewResponse> receivedReviews(@PathVariable String userId) {
        return reviewService.receivedReviews(userId);
    }
}
