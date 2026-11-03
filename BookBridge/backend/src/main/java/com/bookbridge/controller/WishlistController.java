package com.bookbridge.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bookbridge.dto.WishlistResponse;
import com.bookbridge.service.WishlistService;

@RestController
@RequestMapping("/api/wishlist")
public class WishlistController {

    private final WishlistService wishlistService;

    public WishlistController(WishlistService wishlistService) {
        this.wishlistService = wishlistService;
    }

    @GetMapping
    public WishlistResponse get(@AuthenticationPrincipal String userId) {
        return wishlistService.get(userId);
    }

    @PutMapping("/{bookId}")
    public WishlistResponse add(
            @AuthenticationPrincipal String userId,
            @PathVariable String bookId) {
        return wishlistService.add(userId, bookId);
    }

    @DeleteMapping("/{bookId}")
    public WishlistResponse remove(
            @AuthenticationPrincipal String userId,
            @PathVariable String bookId) {
        return wishlistService.remove(userId, bookId);
    }
}
