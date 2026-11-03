package com.bookbridge.dto;

import java.util.List;

public record PublicProfileResponse(
        PublicUserResponse user,
        List<BookResponse> listedBooks,
        List<LoanResponse> borrowingHistory,
        List<LoanResponse> lendingHistory,
        TrustScoreResponse trustScore) {
}
