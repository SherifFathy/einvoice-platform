package com.einvoice.api.customer.dto;

/** A single row-level error from an Excel import. */
public record ImportError(
        int row,
        String field,
        String message
) {}
