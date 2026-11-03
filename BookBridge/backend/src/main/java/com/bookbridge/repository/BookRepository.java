package com.bookbridge.repository;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.bookbridge.model.BookDocument;

public interface BookRepository extends MongoRepository<BookDocument, String> {

    List<BookDocument> findByOwnerIdOrderByCreatedAtDesc(String ownerId);

    List<BookDocument> findByOwnerIdAndStatusOrderByCreatedAtDesc(String ownerId, String status);

    List<BookDocument> findByIsbnAndStatus(String isbn, com.bookbridge.model.BookStatus status);
}
