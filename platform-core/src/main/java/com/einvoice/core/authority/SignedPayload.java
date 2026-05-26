package com.einvoice.core.authority;

/** Javadoc. */
public record SignedPayload(
        byte[] canonicalBytes,
        byte[] signature,
        String signatureAlgorithm
) {}
