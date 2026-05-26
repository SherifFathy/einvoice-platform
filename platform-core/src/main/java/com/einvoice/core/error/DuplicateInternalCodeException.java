package com.einvoice.core.error;

/** Javadoc. */
public class DuplicateInternalCodeException extends RuntimeException {

    public static final String CODE = "DUPLICATE_INTERNAL_CODE_IN_CONTEXT";

    private final Object conflictingId;
    private final String field;

    /**
     * Javadoc.
     *
     * @param message error message
     * @param conflictingId id of the existing row that caused the conflict
     * @param field name of the conflicting field
     */
    public DuplicateInternalCodeException(String message, Object conflictingId, String field) {
        super(message);
        this.conflictingId = conflictingId;
        this.field = field;
    }

    public String getCode() {
        return CODE;
    }

    public Object getConflictingId() {
        return conflictingId;
    }

    public String getField() {
        return field;
    }
}
