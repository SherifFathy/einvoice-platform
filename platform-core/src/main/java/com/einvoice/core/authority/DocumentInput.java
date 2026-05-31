package com.einvoice.core.authority;

/** Javadoc. */
public record DocumentInput(
        Object header,
        Object lines,
        String transactionType,
        String documentType
) {}
