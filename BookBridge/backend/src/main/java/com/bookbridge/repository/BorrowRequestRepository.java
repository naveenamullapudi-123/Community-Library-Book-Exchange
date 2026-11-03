package com.bookbridge.repository;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.bookbridge.model.BorrowRequestDocument;

public interface BorrowRequestRepository extends MongoRepository<BorrowRequestDocument, String> {

    boolean existsByBookIdAndStatus(String bookId, String status);

    List<BorrowRequestDocument> findByOwnerIdAndStatusOrderByCreatedAtDesc(String ownerId, String status);

    List<BorrowRequestDocument> findByBorrowerIdOrderByCreatedAtDesc(String borrowerId);
}
