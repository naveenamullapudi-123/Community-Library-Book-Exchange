package com.bookbridge.dto;

import java.time.Instant;

public record MessageResponse(
        String id,
        String conversationId,
        String senderId,
        String senderName,
        String recipientId,
        String recipientName,
        String content,
        Instant readAt,
        Instant createdAt) {
}
