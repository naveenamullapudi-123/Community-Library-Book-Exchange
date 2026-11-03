package com.bookbridge.controller;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.bookbridge.model.NotificationDocument;
import com.bookbridge.service.NotificationService;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public List<NotificationDocument> notifications(@AuthenticationPrincipal String userId) {
        return notificationService.forUser(userId);
    }

    @GetMapping("/unread")
    public List<NotificationDocument> unread(@AuthenticationPrincipal String userId) {
        return notificationService.unread(userId);
    }

    @PatchMapping("/{notificationId}/read")
    public NotificationDocument markRead(
            @AuthenticationPrincipal String userId,
            @PathVariable String notificationId) {
        return notificationService.markRead(userId, notificationId);
    }

    @PatchMapping("/read-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markAllRead(@AuthenticationPrincipal String userId) {
        notificationService.markAllRead(userId);
    }
}
