package com.bookbridge.dto;

public record BookMetadataResponse(
        String isbn,
        String title,
        String author,
        String genre,
        String language,
        String description,
        String publisher,
        String coverImageUrl) {
}
