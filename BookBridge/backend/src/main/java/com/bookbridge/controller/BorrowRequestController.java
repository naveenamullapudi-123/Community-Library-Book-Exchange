package com.bookbridge.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.bookbridge.dto.BorrowDecisionRequest;
import com.bookbridge.dto.BorrowRequestCreateRequest;
import com.bookbridge.dto.BorrowRequestResponse;
import com.bookbridge.service.BorrowService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/borrow-requests")
public class BorrowRequestController {

    private final BorrowService borrowService;

    public BorrowRequestController(BorrowService borrowService) {
        this.borrowService = borrowService;
    }

    @PostMapping("/books/{bookId}")
    @ResponseStatus(HttpStatus.CREATED)
    public BorrowRequestResponse requestBook(
            @AuthenticationPrincipal String borrowerId,
            @PathVariable String bookId,
            @Valid @RequestBody BorrowRequestCreateRequest request) {
        return borrowService.requestBook(borrowerId, bookId, request);
    }

    @GetMapping("/incoming")
    public List<BorrowRequestResponse> incoming(@AuthenticationPrincipal String ownerId) {
        return borrowService.incomingRequests(ownerId);
    }

    @GetMapping("/mine")
    public List<BorrowRequestResponse> mine(@AuthenticationPrincipal String borrowerId) {
        return borrowService.myRequests(borrowerId);
    }

    @PatchMapping("/{requestId}/decision")
    public BorrowRequestResponse decide(
            @AuthenticationPrincipal String ownerId,
            @PathVariable String requestId,
            @Valid @RequestBody BorrowDecisionRequest decision) {
        return borrowService.decide(ownerId, requestId, decision);
    }
}
