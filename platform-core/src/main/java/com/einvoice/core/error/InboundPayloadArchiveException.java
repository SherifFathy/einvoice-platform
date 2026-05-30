package com.einvoice.core.error;

/** Thrown when the inbound payload archive write fails (FR-OBS-005). */
public class InboundPayloadArchiveException extends RuntimeException {

    public static final String CODE = "ARCHIVE_WRITE_FAILED";

    public InboundPayloadArchiveException(String message, Throwable cause) {
        super(message, cause);
    }

    public String getCode() {
        return CODE;
    }
}
