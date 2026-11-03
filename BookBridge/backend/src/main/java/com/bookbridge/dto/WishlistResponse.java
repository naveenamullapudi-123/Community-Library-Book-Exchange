package com.bookbridge.dto;

import java.util.List;

public record WishlistResponse(List<BookResponse> books) {
}
