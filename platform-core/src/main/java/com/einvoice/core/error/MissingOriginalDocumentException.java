package com.einvoice.core.error;

import java.util.UUID;

/** Javadoc. */
public class MissingOriginalDocumentException extends RuntimeException {
    public static final String CODE = "MISSING_ORIGINAL_DOCUMENT";
    private final UUID documentId;
    private final String documentType;

    /**
     * Javadoc.
     *
     * @param message the error message
     * @param documentId the document identifier
     * @param documentType the document type
     */
    public MissingOriginalDocumentException(String message, UUID documentId, String documentType) {
        super(message);
        this.documentId = documentId;
        this.documentType = documentType;
    }

    public String getCode() {
        return CODE;
    }

    public UUID getDocumentId() {
        return documentId;
    }

    public String getDocumentType() {
        return documentType;
    }
}
