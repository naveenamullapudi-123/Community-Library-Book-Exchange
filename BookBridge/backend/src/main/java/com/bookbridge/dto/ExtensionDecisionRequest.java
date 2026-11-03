package com.bookbridge.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ExtensionDecisionRequest(
        @NotNull Decision decision,
        @Size(max = 1000) String responseMessage) {

    public enum Decision {
        APPROVE,
        REJECT
    }
}
