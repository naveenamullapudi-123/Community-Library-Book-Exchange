package com.bookbridge.repository;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.bookbridge.model.ExtensionRequestDocument;

public interface ExtensionRequestRepository extends MongoRepository<ExtensionRequestDocument, String> {

    boolean existsByLoanIdAndStatus(String loanId, String status);

    List<ExtensionRequestDocument> findByOwnerIdAndStatusOrderByCreatedAtDesc(String ownerId, String status);
}
