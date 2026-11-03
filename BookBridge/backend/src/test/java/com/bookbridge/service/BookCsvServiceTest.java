package com.bookbridge.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.bookbridge.dto.BookRequest;
import com.bookbridge.dto.BookResponse;
import com.bookbridge.model.BookCondition;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

@ExtendWith(MockitoExtension.class)
class BookCsvServiceTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @Mock
    private BookService bookService;

    private BookCsvService csvService;

    @BeforeAll
    static void createValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        validatorFactory.close();
    }

    @BeforeEach
    void setUp() {
        csvService = new BookCsvService(validator, bookService);
    }

    @Test
    void importsValidatedRowsUsingTheAuthenticatedOwner() {
        MockMultipartFile file = csv(
                "ISBN,Title,Author,Condition,Genre,Status,LoanDurationDays\n"
                        + "1234567890,The Book,An Author,Worn but Readable,Fantasy,Available,21\n");
        when(bookService.createAll(eq("owner-1"), org.mockito.ArgumentMatchers.anyList()))
                .thenReturn(List.<BookResponse>of());

        assertEquals(List.of(), csvService.upload("owner-1", file));

        ArgumentCaptor<List<BookRequest>> captor = ArgumentCaptor.forClass(List.class);
        verify(bookService).createAll(eq("owner-1"), captor.capture());
        BookRequest imported = captor.getValue().get(0);
        assertEquals("1234567890", imported.isbn());
        assertEquals(BookCondition.WORN_BUT_READABLE, imported.condition());
        assertEquals(21, imported.loanDurationDays());
    }

    @Test
    void rejectsAnInvalidRowWithoutImportingAnyBooks() {
        MockMultipartFile file = csv(
                "isbn,title,author,condition\n1234567890,The Book,An Author,damaged\n");

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class, () -> csvService.upload("owner-1", file));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verify(bookService, never()).createAll(eq("owner-1"), org.mockito.ArgumentMatchers.anyList());
    }

    private MockMultipartFile csv(String contents) {
        return new MockMultipartFile(
                "file", "books.csv", "text/csv", contents.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}
