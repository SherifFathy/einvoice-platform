package com.einvoice.core.error;

/** Javadoc. */
public class ItemNotFoundException extends RuntimeException {

    public static final String CODE = "ITEM_NOT_FOUND";

    public ItemNotFoundException(String message) {
        super(message);
    }

    public String getCode() {
        return CODE;
    }
}
