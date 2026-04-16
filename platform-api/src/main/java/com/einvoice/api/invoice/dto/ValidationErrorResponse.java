package com.einvoice.api.invoice.dto;

/** A single validation error for invoice requests. */
public record ValidationErrorResponse(
        String field,
        String message
) {}
