package com.bookbridge.repository;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.bookbridge.model.WishlistDocument;

public interface WishlistRepository extends MongoRepository<WishlistDocument, String> {
}
