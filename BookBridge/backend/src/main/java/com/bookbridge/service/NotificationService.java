package com.bookbridge.service;

import java.time.Instant;
import java.util.List;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import com.bookbridge.model.MessageDocument;
import com.bookbridge.model.NotificationDocument;
import com.bookbridge.repository.NotificationRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final MongoTemplate mongoTemplate;

    public NotificationService(NotificationRepository notificationRepository, MongoTemplate mongoTemplate) {
        this.notificationRepository = notificationRepository;
        this.mongoTemplate = mongoTemplate;
    }

    public List<NotificationDocument> forUser(String userId) {
        return notificationRepository.findTop100ByUserIdOrderByCreatedAtDesc(userId);
    }

    public List<NotificationDocument> unread(String userId) {
        return notificationRepository.findTop100ByUserIdAndReadFalseOrderByCreatedAtDesc(userId);
    }

    public NotificationDocument markRead(String userId, String notificationId) {
        NotificationDocument notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found."));
        if (!notification.userId().equals(userId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "You can only manage your own notifications.");
        }
        if (notification.read()) {
            return notification;
        }
        NotificationDocument updated = new NotificationDocument(
                notification.id(), notification.userId(), notification.type(), notification.message(),
                notification.relatedEntityId(), true, notification.scheduledFor(), notification.createdAt());
        return notificationRepository.save(updated);
    }

    public int markAllRead(String userId) {
        return (int) mongoTemplate.updateMulti(
                Query.query(Criteria.where("userId").is(userId).and("read").is(false)),
                new Update().set("read", true),
                NotificationDocument.class).getModifiedCount();
    }

    public NotificationDocument create(
            String userId,
            String type,
            String message,
            String relatedEntityId,
            String deduplicationKey) {
        String notificationId = deduplicationKey == null ? null : "event:" + deduplicationKey + ":" + userId;
        if (notificationId != null && notificationRepository.existsById(notificationId)) {
            return notificationRepository.findById(notificationId).orElseThrow();
        }
        try {
            return notificationRepository.save(new NotificationDocument(
                notificationId,
                userId,
                type,
                message,
                relatedEntityId,
                false,
                null,
                    java.time.Instant.now()));
        } catch (org.springframework.dao.DuplicateKeyException exception) {
            return notificationRepository.findById(notificationId).orElseThrow(() -> exception);
        }
    }

    public void notifyWishlist(String isbn, String title, String newlyAvailableBookId, Instant availableAt) {
        if (isbn == null || isbn.isBlank()) {
            return;
        }
        List<com.bookbridge.model.BookDocument> matches = mongoTemplate.find(
                Query.query(Criteria.where("isbn").is(isbn).and("status").is(com.bookbridge.model.BookStatus.AVAILABLE)),
                com.bookbridge.model.BookDocument.class);
        List<String> matchingBookIds = matches.stream()
                .map(com.bookbridge.model.BookDocument::id)
                .distinct()
                .toList();
        if (!matchingBookIds.contains(newlyAvailableBookId)) {
            matchingBookIds = new java.util.ArrayList<>(matchingBookIds);
            matchingBookIds.add(newlyAvailableBookId);
        }
        List<com.bookbridge.model.WishlistDocument> wishlists = mongoTemplate.find(
                Query.query(Criteria.where("bookIds").in(matchingBookIds)),
                com.bookbridge.model.WishlistDocument.class);
        for (com.bookbridge.model.WishlistDocument wishlist : wishlists) {
            create(
                    wishlist.userId(),
                    "WISHLIST_AVAILABLE",
                    "A book matching your saved " + title + " is available again.",
                    newlyAvailableBookId,
                    "wishlist:" + newlyAvailableBookId + ":" + availableAt);
        }
    }
}
