package com.bookbridge.repository;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.bookbridge.model.MessageDocument;

public interface MessageRepository extends MongoRepository<MessageDocument, String> {

    List<MessageDocument> findTop100ByConversationIdOrderByCreatedAtAsc(String conversationId);

    List<MessageDocument> findByRecipientIdAndReadAtIsNull(String recipientId);
}
