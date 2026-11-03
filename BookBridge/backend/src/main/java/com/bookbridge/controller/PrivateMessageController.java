package com.bookbridge.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.bookbridge.dto.MessageCreateRequest;
import com.bookbridge.dto.MessageResponse;
import com.bookbridge.service.PrivateMessageService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/messages")
public class PrivateMessageController {

    private final PrivateMessageService messageService;

    public PrivateMessageController(PrivateMessageService messageService) {
        this.messageService = messageService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MessageResponse send(
            @AuthenticationPrincipal String senderId,
            @Valid @RequestBody MessageCreateRequest request) {
        return messageService.send(senderId, request);
    }

    @GetMapping("/with/{otherUserId}")
    public List<MessageResponse> conversation(
            @AuthenticationPrincipal String userId,
            @PathVariable String otherUserId) {
        return messageService.conversation(userId, otherUserId);
    }
}
