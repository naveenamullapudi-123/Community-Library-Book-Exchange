package com.bookbridge.service;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.bookbridge.dto.BookResponse;
import com.bookbridge.dto.LoanResponse;
import com.bookbridge.dto.PublicProfileResponse;
import com.bookbridge.dto.PublicUserResponse;
import com.bookbridge.dto.ReviewResponse;
import com.bookbridge.dto.TrustScoreResponse;
import com.bookbridge.model.BookDocument;
import com.bookbridge.model.LoanDocument;
import com.bookbridge.model.UserDocument;
import com.bookbridge.repository.BookRepository;
import com.bookbridge.repository.LoanRepository;
import com.bookbridge.repository.UserRepository;

@Service
public class ProfileService {

    private final UserRepository userRepository;
    private final BookRepository bookRepository;
    private final LoanRepository loanRepository;
    private final BookService bookService;
    private final ReviewService reviewService;
    private final BorrowService borrowService;

    public ProfileService(
            UserRepository userRepository,
            BookRepository bookRepository,
            LoanRepository loanRepository,
            BookService bookService,
            ReviewService reviewService,
            BorrowService borrowService) {
        this.userRepository = userRepository;
        this.bookRepository = bookRepository;
        this.loanRepository = loanRepository;
        this.bookService = bookService;
        this.reviewService = reviewService;
        this.borrowService = borrowService;
    }

    public PublicProfileResponse get(String userId) {
        UserDocument user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User profile not found."));
        List<BookResponse> books = bookRepository.findByOwnerIdOrderByCreatedAtDesc(userId).stream()
                .map(book -> bookService.get(book.id()))
                .toList();
        List<LoanResponse> borrowingHistory = loanRepository.findByBorrowerIdOrderByStartDateDesc(userId).stream()
                .map(this::loanResponse)
                .toList();
        List<LoanResponse> lendingHistory = loanRepository.findByOwnerIdOrderByStartDateDesc(userId).stream()
                .map(this::loanResponse)
                .toList();
        TrustScoreResponse trustScore = reviewService.trustScore(userId);
        return new PublicProfileResponse(
                new PublicUserResponse(user.id(), user.displayName(), user.cityOrPostalCode()),
                books,
                borrowingHistory,
                lendingHistory,
                trustScore);
    }

    public List<ReviewResponse> reviews(String userId) {
        if (!userRepository.existsById(userId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User profile not found.");
        }
        return reviewService.receivedReviews(userId);
    }

    private LoanResponse loanResponse(LoanDocument loan) {
        return borrowService.getLoan(loan.id());
    }
}
