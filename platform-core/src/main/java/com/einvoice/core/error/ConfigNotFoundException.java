package com.einvoice.core.error;

/** Javadoc. */
public class ConfigNotFoundException extends RuntimeException {

    public static final String CODE = "CONFIG_NOT_FOUND";

    public ConfigNotFoundException(String message) {
        super(message);
    }

    public String getCode() {
        return CODE;
    }
}
