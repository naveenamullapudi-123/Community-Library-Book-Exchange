package com.bookbridge.service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import com.bookbridge.dto.BookMetadataResponse;
import com.fasterxml.jackson.databind.JsonNode;

@Service
public class OpenLibraryService {

    private static final Pattern ISBN_PATTERN = Pattern.compile("[0-9Xx-]{10,20}");

    private final RestClient restClient;

    public OpenLibraryService(RestClient.Builder restClientBuilder) {
        restClient = restClientBuilder.baseUrl("https://openlibrary.org").build();
    }

    public BookMetadataResponse lookup(String isbn) {
        String normalizedIsbn = isbn == null ? "" : isbn.replace("-", "").trim();
        if (!ISBN_PATTERN.matcher(normalizedIsbn).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a valid ISBN-10 or ISBN-13.");
        }

        JsonNode response;
        try {
            response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/books")
                            .queryParam("bibkeys", "ISBN:" + normalizedIsbn)
                            .queryParam("format", "json")
                            .queryParam("jscmd", "data")
                            .build())
                    .retrieve()
                    .body(JsonNode.class);
        } catch (HttpClientErrorException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Open Library could not look up this ISBN.");
        } catch (org.springframework.web.client.RestClientException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY, "Open Library is temporarily unavailable.");
        }

        JsonNode book = response == null ? null : response.get("ISBN:" + normalizedIsbn);
        if (book == null || book.isNull()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Open Library has no record for this ISBN.");
        }

        String cover = text(book.path("cover"), "large");
        if (cover == null) {
            cover = text(book.path("cover"), "medium");
        }
        return new BookMetadataResponse(
                normalizedIsbn,
                text(book, "title"),
                joinedNames(book.path("authors"), "name"),
                joinedNames(book.path("subjects"), "name"),
                firstKey(book.path("languages")),
                text(book, "notes"),
                joinedNames(book.path("publishers"), "name"),
                cover);
    }

    private String joinedNames(JsonNode array, String field) {
        if (!array.isArray()) {
            return null;
        }
        List<String> names = new ArrayList<>();
        for (JsonNode value : array) {
            String name = text(value, field);
            if (name != null && !name.isBlank()) {
                names.add(name);
                if (names.size() == 5) {
                    break;
                }
            }
        }
        return names.isEmpty() ? null : String.join(", ", names);
    }

    private String firstKey(JsonNode array) {
        if (!array.isArray() || array.isEmpty()) {
            return null;
        }
        String key = text(array.get(0), "key");
        return key == null ? null : key.substring(key.lastIndexOf('/') + 1);
    }

    private String text(JsonNode object, String field) {
        JsonNode value = object.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (value.isObject()) {
            value = value.get("value");
        }
        return value == null || value.isNull() ? null : value.asText();
    }
}
