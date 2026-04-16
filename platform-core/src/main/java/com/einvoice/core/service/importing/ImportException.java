package com.einvoice.core.service.importing;

/** Thrown when an Excel import cannot be read or processed. */
public class ImportException extends RuntimeException {

    /**
     * Creates a new ImportException.
     *
     * @param message the detail message
     * @param cause the underlying cause
     */
    public ImportException(String message, Throwable cause) {
        super(message, cause);
    }
}
