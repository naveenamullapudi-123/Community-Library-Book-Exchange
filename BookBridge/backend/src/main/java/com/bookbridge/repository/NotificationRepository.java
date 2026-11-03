package com.bookbridge.repository;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.bookbridge.model.NotificationDocument;

public interface NotificationRepository extends MongoRepository<NotificationDocument, String> {

    java.util.List<NotificationDocument> findTop100ByUserIdOrderByCreatedAtDesc(String userId);

    java.util.List<NotificationDocument> findTop100ByUserIdAndReadFalseOrderByCreatedAtDesc(String userId);

    void deleteByRelatedEntityIdAndType(String relatedEntityId, String type);
}
