package com.einvoice.core.error;

/** Javadoc. */
public class InvalidRoleForAuthorityException extends RuntimeException {

    public static final String CODE = "INVALID_ROLE_FOR_AUTHORITY";

    public InvalidRoleForAuthorityException(String message) {
        super(message);
    }

    public String getCode() {
        return CODE;
    }
}
