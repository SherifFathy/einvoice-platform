package com.einvoice.core.error;

/** Thrown when a bulk request exceeds the configured per-batch limit. */
public class BulkBatchLimitExceededException extends RuntimeException {
    public static final String CODE = "BULK_BATCH_LIMIT_EXCEEDED";
    private final int requested;
    private final int limit;

    /**
     * Construct the exception.
     *
     * @param message   the error message
     * @param requested the requested batch size
     * @param limit     the configured per-batch limit
     */
    public BulkBatchLimitExceededException(String message, int requested, int limit) {
        super(message);
        this.requested = requested;
        this.limit = limit;
    }

    /**
     * @return the error code
     */
    public String getCode() {
        return CODE;
    }

    /**
     * @return the requested count
     */
    public int getRequested() {
        return requested;
    }

    /**
     * @return the configured limit
     */
    public int getLimit() {
        return limit;
    }
}
