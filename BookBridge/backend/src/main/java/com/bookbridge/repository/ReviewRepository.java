package com.bookbridge.repository;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.bookbridge.model.ReviewDocument;

public interface ReviewRepository extends MongoRepository<ReviewDocument, String> {

    boolean existsByLoanIdAndReviewerId(String loanId, String reviewerId);

    List<ReviewDocument> findByLoanIdOrderByCreatedAtAsc(String loanId);

    List<ReviewDocument> findByReviewedUserIdOrderByCreatedAtDesc(String reviewedUserId);

    List<ReviewDocument> findTop20ByReviewedUserIdOrderByCreatedAtDesc(String reviewedUserId);
}
