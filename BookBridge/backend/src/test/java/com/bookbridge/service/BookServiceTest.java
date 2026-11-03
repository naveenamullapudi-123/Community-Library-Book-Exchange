package com.bookbridge.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.bookbridge.dto.BookRequest;
import com.bookbridge.dto.BookResponse;
import com.bookbridge.model.BookCondition;
import com.bookbridge.model.BookDocument;
import com.bookbridge.model.BookStatus;
import com.bookbridge.model.UserDocument;
import com.bookbridge.repository.BookRepository;
import com.bookbridge.repository.BorrowRequestRepository;
import com.bookbridge.repository.LoanRepository;
import com.bookbridge.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class BookServiceTest {

    @Mock
    private BookRepository bookRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private BorrowRequestRepository borrowRequestRepository;

    @Mock
    private LoanRepository loanRepository;

    @Mock
    private NotificationService notificationService;

    @Mock
    private MongoTemplate mongoTemplate;

    private BookService bookService;

    @BeforeEach
    void setUp() {
        bookService = new BookService(
                bookRepository,
                userRepository,
                borrowRequestRepository,
                loanRepository,
                notificationService,
                mongoTemplate);
    }

    @Test
    void createUsesAuthenticatedOwnerAndSensibleDefaults() {
        when(userRepository.findById("signed-in-user")).thenReturn(Optional.of(
                new UserDocument("signed-in-user", "Reader", "reader@example.com", "hash", "Town", Instant.now())));
        when(bookRepository.save(any(BookDocument.class))).thenAnswer(invocation -> {
            BookDocument book = invocation.getArgument(0);
            return new BookDocument(
                    "book-1",
                    book.isbn(),
                    book.title(),
                    book.author(),
                    book.genre(),
                    book.language(),
                    book.description(),
                    book.publisher(),
                    book.coverImageUrl(),
                    book.condition(),
                    book.status(),
                    book.ownerId(),
                    book.loanDurationDays(),
                    book.pickupPreferences(),
                    book.cityOrPostalCode(),
                    book.createdAt(),
                    book.updatedAt());
        });

        BookResponse response = bookService.create(
                "signed-in-user",
                new BookRequest("1234567890", "A Book", "An Author", null, null, null, null, null,
                        BookCondition.GOOD, null, null, null, null));

        assertEquals("signed-in-user", response.owner().id());
        assertEquals(BookStatus.AVAILABLE, response.status());
        assertEquals(14, response.loanDurationDays());
    }

    @Test
    void anotherUserCannotDeleteOrEditABook() {
        when(bookRepository.findById("book-1")).thenReturn(Optional.of(
                new BookDocument("book-1", "1234567890", "A Book", "An Author", null, null, null, null, null,
                        BookCondition.GOOD, BookStatus.AVAILABLE, "owner-1", 14, null, null,
                        Instant.now(), Instant.now())));

        ResponseStatusException deleteException = assertThrows(
                ResponseStatusException.class, () -> bookService.delete("reader-2", "book-1"));
        ResponseStatusException editException = assertThrows(
                ResponseStatusException.class,
                () -> bookService.update(
                        "reader-2",
                        "book-1",
                        new BookRequest("1234567890", "Changed", "An Author", null, null, null, null, null,
                                BookCondition.GOOD, null, null, null, null)));

        assertEquals(HttpStatus.FORBIDDEN, deleteException.getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, editException.getStatusCode());
        verify(bookRepository, never()).delete(any(BookDocument.class));
        verify(bookRepository, never()).save(any(BookDocument.class));
    }
}
