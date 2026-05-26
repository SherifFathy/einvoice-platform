package com.einvoice.core.error;

import java.util.UUID;

/** Javadoc. */
public class DuplicateReceiptNumberException extends RuntimeException {
    public static final String CODE = "DUPLICATE_RECEIPT_NUMBER";
    private final UUID companyId;
    private final String receiptNumber;

    /**
     * Javadoc.
     *
     * @param message the error message
     * @param companyId the company identifier
     * @param receiptNumber the receipt number
     */
    public DuplicateReceiptNumberException(String message, UUID companyId, String receiptNumber) {
        super(message);
        this.companyId = companyId;
        this.receiptNumber = receiptNumber;
    }

    public String getCode() {
        return CODE;
    }

    public UUID getCompanyId() {
        return companyId;
    }

    public String getReceiptNumber() {
        return receiptNumber;
    }
}
