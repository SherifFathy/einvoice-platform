package com.einvoice.core.authority;

/** Javadoc. */
public record SerializedPayload(
        byte[] canonicalBytes,
        String contentType
) {}
