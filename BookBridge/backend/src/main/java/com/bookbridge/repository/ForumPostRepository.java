package com.bookbridge.repository;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.bookbridge.model.ForumPostDocument;

public interface ForumPostRepository extends MongoRepository<ForumPostDocument, String> {

    List<ForumPostDocument> findTop100ByOrderByCreatedAtDesc();
}
