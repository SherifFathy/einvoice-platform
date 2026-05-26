package com.einvoice.core.error;

/** Javadoc. */
public class VatExemptionReasonRequiredException extends RuntimeException {
    public static final String CODE = "VAT_EXEMPTION_REASON_REQUIRED";
    private final String vatCategoryCode;

    /**
     * Javadoc.
     *
     * @param message the error message
     * @param vatCategoryCode the VAT category code
     */
    public VatExemptionReasonRequiredException(String message, String vatCategoryCode) {
        super(message);
        this.vatCategoryCode = vatCategoryCode;
    }

    public String getCode() {
        return CODE;
    }

    public String getVatCategoryCode() {
        return vatCategoryCode;
    }
}
