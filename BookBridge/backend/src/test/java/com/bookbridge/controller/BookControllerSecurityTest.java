package com.bookbridge.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.bookbridge.dto.BookRequest;
import com.bookbridge.dto.BookResponse;
import com.bookbridge.dto.PublicUserResponse;
import com.bookbridge.model.BookCondition;
import com.bookbridge.model.BookStatus;
import com.bookbridge.security.SecurityConfiguration;
import com.bookbridge.security.TokenService;
import com.bookbridge.service.BookCsvService;
import com.bookbridge.service.BookService;
import com.bookbridge.service.OpenLibraryService;

@WebMvcTest(BookController.class)
@AutoConfigureMockMvc
@Import({SecurityConfiguration.class, TokenService.class})
class BookControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TokenService tokenService;

    @MockitoBean
    private BookService bookService;

    @MockitoBean
    private BookCsvService bookCsvService;

    @MockitoBean
    private OpenLibraryService openLibraryService;

    @Test
    void bookCreationRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookJson()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Unauthorized"));
    }

    @Test
    void bookOwnerComesFromTheVerifiedBearerToken() throws Exception {
        when(bookService.create(eq("verified-reader"), any(BookRequest.class))).thenReturn(
                new BookResponse(
                        "book-1",
                        "1234567890",
                        "A Book",
                        "An Author",
                        null,
                        null,
                        null,
                        null,
                        null,
                        BookCondition.GOOD,
                        BookStatus.AVAILABLE,
                        new PublicUserResponse("verified-reader", "Reader", null),
                        14,
                        null,
                        Instant.now(),
                        Instant.now()));
        String token = tokenService.issue("verified-reader").value();

        mockMvc.perform(post("/api/books")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookJson()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.owner.id").value("verified-reader"));

        verify(bookService).create(eq("verified-reader"), any(BookRequest.class));
    }

    private String bookJson() {
        return """
                {
                  "isbn": "1234567890",
                  "title": "A Book",
                  "author": "An Author",
                  "condition": "GOOD"
                }
                """;
    }
}
