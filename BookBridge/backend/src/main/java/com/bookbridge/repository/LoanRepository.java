package com.bookbridge.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.bookbridge.model.LoanDocument;

public interface LoanRepository extends MongoRepository<LoanDocument, String> {

    boolean existsByBookIdAndStatus(String bookId, String status);

    List<LoanDocument> findByBorrowerIdAndStatusOrderByDueDateAsc(String borrowerId, String status);

    List<LoanDocument> findByOwnerIdAndStatusOrderByDueDateAsc(String ownerId, String status);

    List<LoanDocument> findByBorrowerIdAndStatusOrderByStartDateDesc(String borrowerId, String status);

    List<LoanDocument> findByOwnerIdAndStatusOrderByStartDateDesc(String ownerId, String status);

    List<LoanDocument> findByStatusAndDueDateIn(String status, List<LocalDate> dueDates);

    List<LoanDocument> findByBorrowerIdOrderByStartDateDesc(String borrowerId);

    List<LoanDocument> findByOwnerIdOrderByStartDateDesc(String ownerId);
}
