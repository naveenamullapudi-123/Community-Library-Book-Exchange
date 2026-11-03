package com.bookbridge.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.bookbridge.dto.MessageCreateRequest;
import com.bookbridge.dto.MessageResponse;
import com.bookbridge.model.MessageDocument;
import com.bookbridge.model.UserDocument;
import com.bookbridge.repository.MessageRepository;
import com.bookbridge.repository.UserRepository;

@Service
public class PrivateMessageService {

    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final MongoTemplate mongoTemplate;
    private final NotificationService notificationService;

    public PrivateMessageService(
            MessageRepository messageRepository,
            UserRepository userRepository,
            MongoTemplate mongoTemplate,
            NotificationService notificationService) {
        this.messageRepository = messageRepository;
        this.userRepository = userRepository;
        this.mongoTemplate = mongoTemplate;
        this.notificationService = notificationService;
    }

    public MessageResponse send(String senderId, MessageCreateRequest request) {
        if (senderId.equals(request.recipientId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "You cannot message yourself.");
        }
        UserDocument sender = findUser(senderId);
        UserDocument recipient = findUser(request.recipientId());
        String conversationId = conversationId(senderId, recipient.id());
        MessageDocument message = messageRepository.save(new MessageDocument(
                null,
                conversationId,
                senderId,
                recipient.id(),
                request.content().trim(),
                null,
                Instant.now()));
        notificationService.create(
                recipient.id(),
                "NEW_MESSAGE",
                "You received a private message from " + sender.displayName() + ".",
                message.id(),
                "message:" + message.id());
        return toResponse(message, sender, recipient);
    }

    public List<MessageResponse> conversation(String userId, String otherUserId) {
        if (userId.equals(otherUserId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A conversation needs another participant.");
        }
        UserDocument user = findUser(userId);
        UserDocument other = findUser(otherUserId);
        String conversationId = conversationId(user.id(), other.id());
        mongoTemplate.updateMulti(
                Query.query(Criteria.where("conversationId").is(conversationId)
                        .and("recipientId").is(userId)
                        .and("readAt").is(null)),
                new Update().set("readAt", Instant.now()),
                MessageDocument.class);

        List<MessageDocument> messages =
                messageRepository.findTop100ByConversationIdOrderByCreatedAtAsc(conversationId);
        return messages.stream().map(message -> toResponse(
                message,
                message.senderId().equals(userId) ? user : other,
                message.recipientId().equals(userId) ? user : other)).toList();
    }

    private String conversationId(String firstUserId, String secondUserId) {
        String participants = firstUserId.compareTo(secondUserId) < 0
                ? firstUserId + ":" + secondUserId
                : secondUserId + ":" + firstUserId;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(participants.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available.", exception);
        }
    }

    private MessageResponse toResponse(
            MessageDocument message,
            UserDocument sender,
            UserDocument recipient) {
        return new MessageResponse(
                message.id(),
                message.conversationId(),
                sender.id(),
                sender.displayName(),
                recipient.id(),
                recipient.displayName(),
                message.content(),
                message.readAt(),
                message.createdAt());
    }

    private UserDocument findUser(String userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found."));
    }
}
