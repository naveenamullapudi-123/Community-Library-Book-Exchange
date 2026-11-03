package com.bookbridge.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import com.bookbridge.dto.BookMetadataResponse;

class OpenLibraryServiceTest {

    @Test
    void mapsOpenLibraryMetadataAndNormalizesAnIsbn() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(
                        "https://openlibrary.org/api/books?bibkeys=ISBN:0451526538&format=json&jscmd=data"))
                .andRespond(withSuccess("""
                        {
                          "ISBN:0451526538": {
                            "title": "Nineteen Eighty-Four",
                            "authors": [{"name": "George Orwell"}],
                            "subjects": [{"name": "Dystopian fiction"}],
                            "languages": [{"key": "/languages/eng"}],
                            "publishers": [{"name": "Secker & Warburg"}],
                            "cover": {"large": "https://covers.example.test/book.jpg"},
                            "notes": {"type": "text", "value": "A novel."}
                          }
                        }
                        """, MediaType.APPLICATION_JSON));
        OpenLibraryService service = new OpenLibraryService(builder);

        BookMetadataResponse result = service.lookup("0-451-52653-8");

        assertEquals("0451526538", result.isbn());
        assertEquals("Nineteen Eighty-Four", result.title());
        assertEquals("George Orwell", result.author());
        assertEquals("Dystopian fiction", result.genre());
        assertEquals("eng", result.language());
        assertEquals("A novel.", result.description());
        assertEquals("https://covers.example.test/book.jpg", result.coverImageUrl());
        server.verify();
    }

    @Test
    void rejectsMalformedIsbnBeforeCallingOpenLibrary() {
        OpenLibraryService service = new OpenLibraryService(RestClient.builder());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class, () -> service.lookup("not-an-isbn"));

        assertEquals(400, exception.getStatusCode().value());
    }
}
