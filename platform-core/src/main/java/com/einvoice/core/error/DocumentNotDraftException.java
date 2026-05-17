package com.einvoice.core.error;

/** Javadoc. */
public class DocumentNotDraftException extends RuntimeException {
    public static final String CODE = "DOCUMENT_NOT_DRAFT";
    private final String currentState;

    public DocumentNotDraftException(String message, String currentState) {
        super(message);
        this.currentState = currentState;
    }

    public String getCode() {
        return CODE;
    }

    public String getCurrentState() {
        return currentState;
    }
}
