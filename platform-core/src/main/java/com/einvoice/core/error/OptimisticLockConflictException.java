package com.einvoice.core.error;

/** Javadoc. */
public class OptimisticLockConflictException extends RuntimeException {
    public static final String CODE = "OPTIMISTIC_LOCK_CONFLICT";
    private final Integer expectedVersion;
    private final Integer actualVersion;
    private final Object current;

    /**
     * Construct the exception.
     *
     * @param message         the error message
     * @param expectedVersion the version the client sent (If-Match)
     * @param actualVersion   the current version in the database
     * @param current         the current entity state (may be null)
     */
    public OptimisticLockConflictException(String message,
            Integer expectedVersion, Integer actualVersion, Object current) {
        super(message);
        this.expectedVersion = expectedVersion;
        this.actualVersion = actualVersion;
        this.current = current;
    }

    public String getCode() {
        return CODE;
    }

    public Integer getExpectedVersion() {
        return expectedVersion;
    }

    public Integer getActualVersion() {
        return actualVersion;
    }

    public Object getCurrent() {
        return current;
    }
}
