package com.einvoice.core.error;

/** Javadoc. */
public class BranchIdNotAllowedException extends RuntimeException {

    public static final String CODE = "BRANCH_ID_NOT_ALLOWED";

    public BranchIdNotAllowedException(String message) {
        super(message);
    }

    public String getCode() {
        return CODE;
    }
}
