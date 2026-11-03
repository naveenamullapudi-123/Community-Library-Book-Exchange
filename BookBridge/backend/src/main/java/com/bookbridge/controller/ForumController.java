package com.bookbridge.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.bookbridge.dto.ForumPostRequest;
import com.bookbridge.dto.ForumPostResponse;
import com.bookbridge.service.ForumService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/forum/posts")
public class ForumController {

    private final ForumService forumService;

    public ForumController(ForumService forumService) {
        this.forumService = forumService;
    }

    @GetMapping
    public List<ForumPostResponse> list() {
        return forumService.list();
    }

    @GetMapping("/{postId}")
    public ForumPostResponse get(@PathVariable String postId) {
        return forumService.get(postId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ForumPostResponse create(
            @AuthenticationPrincipal String userId,
            @Valid @RequestBody ForumPostRequest request) {
        return forumService.create(userId, request);
    }

    @PutMapping("/{postId}")
    public ForumPostResponse update(
            @AuthenticationPrincipal String userId,
            @PathVariable String postId,
            @Valid @RequestBody ForumPostRequest request) {
        return forumService.update(userId, postId, request);
    }

    @DeleteMapping("/{postId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal String userId, @PathVariable String postId) {
        forumService.delete(userId, postId);
    }
}
