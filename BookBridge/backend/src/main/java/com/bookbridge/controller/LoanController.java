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

import com.bookbridge.dto.ExtensionCreateRequest;
import com.bookbridge.dto.ExtensionDecisionRequest;
import com.bookbridge.dto.ExtensionResponse;
import com.bookbridge.dto.LoanResponse;
import com.bookbridge.service.BorrowService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/loans")
public class LoanController {

    private final BorrowService borrowService;

    public LoanController(BorrowService borrowService) {
        this.borrowService = borrowService;
    }

    @GetMapping("/borrowed")
    public List<LoanResponse> borrowed(@AuthenticationPrincipal String borrowerId) {
        return borrowService.borrowedLoans(borrowerId);
    }

    @GetMapping("/lent")
    public List<LoanResponse> lent(@AuthenticationPrincipal String ownerId) {
        return borrowService.lentLoans(ownerId);
    }

    @GetMapping("/returned")
    public List<LoanResponse> returned(@AuthenticationPrincipal String userId) {
        return borrowService.returnedLoans(userId);
    }

    @PostMapping("/{loanId}/extensions")
    @ResponseStatus(HttpStatus.CREATED)
    public ExtensionResponse requestExtension(
            @AuthenticationPrincipal String borrowerId,
            @PathVariable String loanId,
            @Valid @RequestBody ExtensionCreateRequest request) {
        return borrowService.requestExtension(borrowerId, loanId, request);
    }

    @GetMapping("/extensions/incoming")
    public List<ExtensionResponse> incomingExtensions(@AuthenticationPrincipal String ownerId) {
        return borrowService.incomingExtensions(ownerId);
    }

    @PatchMapping("/extensions/{extensionId}/decision")
    public ExtensionResponse decideExtension(
            @AuthenticationPrincipal String ownerId,
            @PathVariable String extensionId,
            @Valid @RequestBody ExtensionDecisionRequest decision) {
        return borrowService.decideExtension(ownerId, extensionId, decision);
    }

    @PostMapping("/{loanId}/return")
    public LoanResponse markReturned(
            @AuthenticationPrincipal String userId,
            @PathVariable String loanId) {
        return borrowService.markReturned(userId, loanId);
    }
}
