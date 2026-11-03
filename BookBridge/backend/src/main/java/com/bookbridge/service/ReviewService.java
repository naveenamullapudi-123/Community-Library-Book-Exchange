package com.bookbridge.service;

import java.time.Instant;
import java.util.List;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.bookbridge.dto.ReviewRequest;
import com.bookbridge.dto.ReviewResponse;
import com.bookbridge.dto.TrustScoreResponse;
import com.bookbridge.model.LoanDocument;
import com.bookbridge.model.ReviewDocument;
import com.bookbridge.model.UserDocument;
import com.bookbridge.repository.LoanRepository;
import com.bookbridge.repository.ReviewRepository;
import com.bookbridge.repository.UserRepository;

@Service
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final LoanRepository loanRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    public ReviewService(
            ReviewRepository reviewRepository,
            LoanRepository loanRepository,
            UserRepository userRepository,
            NotificationService notificationService) {
        this.reviewRepository = reviewRepository;
        this.loanRepository = loanRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
    }

    public ReviewResponse create(String reviewerId, String loanId, ReviewRequest request) {
        LoanDocument loan = loanRepository.findById(loanId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Loan not found."));
        if (!BorrowService.RETURNED.equals(loan.status())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Reviews are available after a loan is returned.");
        }
        String reviewedUserId;
        if (loan.borrowerId().equals(reviewerId)) {
            reviewedUserId = loan.ownerId();
        } else if (loan.ownerId().equals(reviewerId)) {
            reviewedUserId = loan.borrowerId();
        } else {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the loan participants can leave a review.");
        }
        if (reviewRepository.existsByLoanIdAndReviewerId(loanId, reviewerId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "You have already reviewed this loan.");
        }
        ReviewDocument review = new ReviewDocument(
                null,
                loanId,
                reviewerId,
                reviewedUserId,
                request.reliability(),
                request.communication(),
                request.careOfBooks(),
                clean(request.comment()),
                Instant.now());
        try {
            ReviewDocument saved = reviewRepository.save(review);
            notificationService.create(
                    reviewedUserId,
                    "NEW_REVIEW",
                    "You received a new exchange review.",
                    saved.id(),
                    "review:" + saved.id());
            return toResponse(saved);
        } catch (DuplicateKeyException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "You have already reviewed this loan.");
        }
    }

    public List<ReviewResponse> receivedReviews(String userId) {
        return reviewRepository.findTop20ByReviewedUserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    public List<ReviewResponse> reviewsForLoan(String userId, String loanId) {
        LoanDocument loan = loanRepository.findById(loanId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Loan not found."));
        if (!BorrowService.RETURNED.equals(loan.status())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Reviews are available after a loan is returned.");
        }
        if (!loan.borrowerId().equals(userId) && !loan.ownerId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the loan participants can view its reviews.");
        }
        return reviewRepository.findByLoanIdOrderByCreatedAtAsc(loanId).stream()
                .map(this::toResponse)
                .toList();
    }

    public TrustScoreResponse trustScore(String userId) {
        List<ReviewDocument> reviews = reviewRepository.findByReviewedUserIdOrderByCreatedAtDesc(userId);
        if (reviews.isEmpty()) {
            return new TrustScoreResponse(0.0, 0);
        }
        double average = reviews.stream()
                .mapToInt(review -> review.reliability() + review.communication() + review.careOfBooks())
                .average()
                .orElse(0.0) / 3.0;
        return new TrustScoreResponse(Math.round(average * 100.0) / 100.0, reviews.size());
    }

    private ReviewResponse toResponse(ReviewDocument review) {
        UserDocument reviewer = findUser(review.reviewerId());
        UserDocument reviewedUser = findUser(review.reviewedUserId());
        double average = (review.reliability() + review.communication() + review.careOfBooks()) / 3.0;
        return new ReviewResponse(
                review.id(),
                review.loanId(),
                reviewer.id(),
                reviewer.displayName(),
                reviewedUser.id(),
                reviewedUser.displayName(),
                review.reliability(),
                review.communication(),
                review.careOfBooks(),
                Math.round(average * 100.0) / 100.0,
                review.comment(),
                review.createdAt());
    }

    private UserDocument findUser(String id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "User not found."));
    }

    private String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
