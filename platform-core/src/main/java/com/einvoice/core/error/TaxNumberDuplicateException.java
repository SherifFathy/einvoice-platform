package com.einvoice.core.error;

/** Javadoc. */
public class TaxNumberDuplicateException extends RuntimeException {

    public static final String CODE = "TAX_NUMBER_DUPLICATE_IN_CONTEXT";

    public TaxNumberDuplicateException(String message) {
        super(message);
    }

    public String getCode() {
        return CODE;
    }
}
