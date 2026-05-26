package com.einvoice.core.error;

import java.util.UUID;

/** Javadoc. */
public class DuplicateSimplifiedNumberException extends RuntimeException {
    public static final String CODE = "DUPLICATE_SIMPLIFIED_NUMBER";
    private final UUID companyId;
    private final String invoiceNumber;

    /**
     * Javadoc.
     *
     * @param message the error message
     * @param companyId the company identifier
     * @param invoiceNumber the invoice number
     */
    public DuplicateSimplifiedNumberException(String message, UUID companyId, String invoiceNumber) {
        super(message);
        this.companyId = companyId;
        this.invoiceNumber = invoiceNumber;
    }

    public String getCode() {
        return CODE;
    }

    public UUID getCompanyId() {
        return companyId;
    }

    public String getInvoiceNumber() {
        return invoiceNumber;
    }
}
