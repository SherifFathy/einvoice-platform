package com.einvoice.core.error;

public class MissingCryptographicStampException extends RuntimeException {
    public MissingCryptographicStampException(String message) {
        super(message);
    }
}
