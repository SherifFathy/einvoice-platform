package com.einvoice.core.error;

/** Thrown when a submitted ZATCA document is missing the required cryptographic stamp (BR-KSA-60). */
public class MissingCryptographicStampException extends RuntimeException {
    public MissingCryptographicStampException(String message) {
        super(message);
    }
}
