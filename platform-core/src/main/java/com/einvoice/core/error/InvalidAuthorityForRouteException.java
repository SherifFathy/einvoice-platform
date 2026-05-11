package com.einvoice.core.error;

/** Javadoc. */
public class InvalidAuthorityForRouteException extends RuntimeException {

    public static final String CODE = "INVALID_AUTHORITY_FOR_ROUTE";

    public InvalidAuthorityForRouteException(String message) {
        super(message);
    }

    public String getCode() {
        return CODE;
    }
}
