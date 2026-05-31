package com.einvoice.core.error;

/** Javadoc. */
public class LastSuperUserProtectedException extends RuntimeException {

    public static final String CODE = "LAST_SUPER_USER_PROTECTED";

    public LastSuperUserProtectedException(String message) {
        super(message);
    }

    public String getCode() {
        return CODE;
    }
}
