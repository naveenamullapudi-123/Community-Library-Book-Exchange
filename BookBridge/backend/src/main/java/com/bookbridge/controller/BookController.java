package com.bookbridge.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.bookbridge.dto.BookMetadataResponse;
import com.bookbridge.dto.BookRequest;
import com.bookbridge.dto.BookResponse;
import com.bookbridge.dto.BookStatusRequest;
import com.bookbridge.dto.BookshelfResponse;
import com.bookbridge.model.BookCondition;
import com.bookbridge.model.BookStatus;
import com.bookbridge.service.BookCsvService;
import com.bookbridge.service.BookService;
import com.bookbridge.service.OpenLibraryService;

import jakarta.validation.Valid;

@Validated
@RestController
@RequestMapping("/api/books")
public class BookController {

    private final BookService bookService;
    private final BookCsvService bookCsvService;
    private final OpenLibraryService openLibraryService;

    public BookController(
            BookService bookService,
            BookCsvService bookCsvService,
            OpenLibraryService openLibraryService) {
        this.bookService = bookService;
        this.bookCsvService = bookCsvService;
        this.openLibraryService = openLibraryService;
    }

    @GetMapping("/isbn/{isbn}")
    public BookMetadataResponse lookupIsbn(@PathVariable String isbn) {
        return openLibraryService.lookup(isbn);
    }

    @GetMapping("/genres")
    public List<String> genres() {
        return bookService.genres();
    }

    @GetMapping
    public List<BookResponse> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) BookStatus status,
            @RequestParam(required = false) String language,
            @RequestParam(required = false) BookCondition condition) {
        return bookService.search(q, status, language, condition);
    }

    @GetMapping("/{bookId}")
    public BookResponse get(@PathVariable String bookId) {
        return bookService.get(bookId);
    }

    @GetMapping("/me/bookshelf")
    public BookshelfResponse bookshelf(@AuthenticationPrincipal String userId) {
        return bookService.bookshelf(userId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BookResponse create(
            @AuthenticationPrincipal String userId,
            @Valid @RequestBody BookRequest request) {
        return bookService.create(userId, request);
    }

    @PutMapping("/{bookId}")
    public BookResponse update(
            @AuthenticationPrincipal String userId,
            @PathVariable String bookId,
            @Valid @RequestBody BookRequest request) {
        return bookService.update(userId, bookId, request);
    }

    @PatchMapping("/{bookId}/status")
    public BookResponse updateStatus(
            @AuthenticationPrincipal String userId,
            @PathVariable String bookId,
            @Valid @RequestBody BookStatusRequest request) {
        return bookService.updateStatus(userId, bookId, request.status());
    }

    @DeleteMapping("/{bookId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal String userId, @PathVariable String bookId) {
        bookService.delete(userId, bookId);
    }

    @PostMapping(value = "/bulk", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public List<BookResponse> uploadCsv(
            @AuthenticationPrincipal String userId,
            @RequestParam("file") MultipartFile file) {
        return bookCsvService.upload(userId, file);
    }
}
