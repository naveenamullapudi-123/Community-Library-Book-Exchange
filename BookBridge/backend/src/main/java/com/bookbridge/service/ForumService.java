package com.bookbridge.service;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.bookbridge.dto.ForumPostRequest;
import com.bookbridge.dto.ForumPostResponse;
import com.bookbridge.model.ForumPostDocument;
import com.bookbridge.model.UserDocument;
import com.bookbridge.repository.ForumPostRepository;
import com.bookbridge.repository.UserRepository;

@Service
public class ForumService {

    private final ForumPostRepository postRepository;
    private final UserRepository userRepository;
    private final MongoTemplate mongoTemplate;

    public ForumService(
            ForumPostRepository postRepository,
            UserRepository userRepository,
            MongoTemplate mongoTemplate) {
        this.postRepository = postRepository;
        this.userRepository = userRepository;
        this.mongoTemplate = mongoTemplate;
    }

    public List<ForumPostResponse> list() {
        return mongoTemplate.find(new Query().with(Sort.by(Sort.Direction.DESC, "createdAt")).limit(100),
                        ForumPostDocument.class)
                .stream().map(this::toResponse).toList();
    }

    public ForumPostResponse get(String postId) {
        return toResponse(findPost(postId));
    }

    public ForumPostResponse create(String authorId, ForumPostRequest request) {
        Instant now = Instant.now();
        return toResponse(postRepository.save(new ForumPostDocument(
                null,
                authorId,
                request.category().trim(),
                request.title().trim(),
                request.content().trim(),
                request.eventDate(),
                now,
                now)));
    }

    public ForumPostResponse update(String userId, String postId, ForumPostRequest request) {
        ForumPostDocument post = findPost(postId);
        requireOwner(post, userId);
        return toResponse(postRepository.save(new ForumPostDocument(
                post.id(),
                post.authorId(),
                request.category().trim(),
                request.title().trim(),
                request.content().trim(),
                request.eventDate(),
                post.createdAt(),
                Instant.now())));
    }

    public void delete(String userId, String postId) {
        ForumPostDocument post = findPost(postId);
        requireOwner(post, userId);
        postRepository.delete(post);
    }

    private ForumPostDocument findPost(String postId) {
        return postRepository.findById(postId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Forum post not found."));
    }

    private void requireOwner(ForumPostDocument post, String userId) {
        if (!post.authorId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only manage your own forum posts.");
        }
    }

    private ForumPostResponse toResponse(ForumPostDocument post) {
        UserDocument author = userRepository.findById(post.authorId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Post author not found."));
        return new ForumPostResponse(
                post.id(), author.id(), author.displayName(), post.category(), post.title(),
                post.content(), post.eventDate(), post.createdAt(), post.updatedAt());
    }
}
