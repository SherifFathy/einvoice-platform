package com.einvoice.api.item.dto;

public record ImportError(
        int row,
        String field,
        String message
) {}
