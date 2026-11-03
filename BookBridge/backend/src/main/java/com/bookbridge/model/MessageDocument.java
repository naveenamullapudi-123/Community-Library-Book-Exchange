package com.bookbridge.model;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import jakarta.validation.constraints.NotBlank;

@Document(collection = "messages")
@CompoundIndex(name = "message_conversation_created", def = "{'conversationId': 1, 'createdAt': 1}")
public record MessageDocument(
        @Id String id,
        @NotBlank @Indexed String conversationId,
        @NotBlank String senderId,
        @NotBlank String recipientId,
        @NotBlank String content,
        Instant readAt,
        Instant createdAt) {
}
