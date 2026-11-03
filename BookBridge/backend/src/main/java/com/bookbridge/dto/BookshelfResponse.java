package com.bookbridge.dto;

import java.util.List;

public record BookshelfResponse(
        List<BookResponse> books,
        List<IncomingRequestResponse> incomingRequests) {
}
