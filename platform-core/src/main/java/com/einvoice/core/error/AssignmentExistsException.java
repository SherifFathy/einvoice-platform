package com.einvoice.core.error;

/** Javadoc. */
public class AssignmentExistsException extends RuntimeException {

    public static final String CODE = "ASSIGNMENT_EXISTS";

    public AssignmentExistsException(String message) {
        super(message);
    }

    public String getCode() {
        return CODE;
    }
}
