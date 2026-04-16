package com.einvoice.core.service.importing;

/** Thrown when an Excel template cannot be generated. */
public class TemplateException extends RuntimeException {

    /**
     * Creates a new TemplateException.
     *
     * @param message the detail message
     * @param cause the underlying cause
     */
    public TemplateException(String message, Throwable cause) {
        super(message, cause);
    }
}
