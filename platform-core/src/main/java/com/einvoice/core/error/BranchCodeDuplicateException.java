package com.einvoice.core.error;

/** Javadoc. */
public class BranchCodeDuplicateException extends RuntimeException {

    public static final String CODE = "BRANCH_CODE_DUPLICATE_IN_COMPANY";

    public BranchCodeDuplicateException(String message) {
        super(message);
    }

    public String getCode() {
        return CODE;
    }
}
