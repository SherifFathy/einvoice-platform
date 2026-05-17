package com.einvoice.core.error;

/** Javadoc. */
public class AppendOnlyViolationException extends RuntimeException {
    public static final String CODE = "APPEND_ONLY_VIOLATION";
    private final String tableName;

    public AppendOnlyViolationException(String message, String tableName) {
        super(message);
        this.tableName = tableName;
    }

    public String getCode() {
        return CODE;
    }

    public String getTableName() {
        return tableName;
    }
}
