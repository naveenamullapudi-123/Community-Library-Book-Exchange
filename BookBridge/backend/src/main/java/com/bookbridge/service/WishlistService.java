package com.bookbridge.service;

import java.time.Instant;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import com.bookbridge.dto.BookResponse;
import com.bookbridge.dto.WishlistResponse;
import com.bookbridge.model.BookDocument;
import com.bookbridge.model.BookStatus;
import com.bookbridge.model.WishlistDocument;
import com.bookbridge.repository.BookRepository;
import com.bookbridge.repository.WishlistRepository;

@Service
public class WishlistService {

    private final WishlistRepository wishlistRepository;
    private final BookRepository bookRepository;
    private final BookService bookService;
    private final MongoTemplate mongoTemplate;

    public WishlistService(
            WishlistRepository wishlistRepository,
            BookRepository bookRepository,
            BookService bookService,
            MongoTemplate mongoTemplate) {
        this.wishlistRepository = wishlistRepository;
        this.bookRepository = bookRepository;
        this.bookService = bookService;
        this.mongoTemplate = mongoTemplate;
    }

    public WishlistResponse get(String userId) {
        WishlistDocument wishlist = wishlistRepository.findById(userId)
                .orElse(new WishlistDocument(userId, userId, List.of(), Instant.now()));
        return new WishlistResponse(wishlist.bookIds().stream()
                .map(bookId -> bookRepository.findById(bookId).orElse(null))
                .filter(java.util.Objects::nonNull)
                .map(book -> bookService.get(book.id()))
                .toList());
    }

    public WishlistResponse add(String userId, String bookId) {
        BookDocument book = bookRepository.findById(bookId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Book not found."));
        if (book.ownerId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "You cannot wishlist your own book.");
        }
        mongoTemplate.findAndModify(
                Query.query(Criteria.where("_id").is(userId)),
                new Update().setOnInsert("userId", userId)
                        .setOnInsert("createdAt", Instant.now())
                        .addToSet("bookIds", bookId),
                FindAndModifyOptions.options().upsert(true).returnNew(true),
                WishlistDocument.class);
        return get(userId);
    }

    public WishlistResponse remove(String userId, String bookId) {
        mongoTemplate.updateFirst(
                Query.query(Criteria.where("_id").is(userId)),
                new Update().pull("bookIds", bookId),
                WishlistDocument.class);
        return get(userId);
    }
}
