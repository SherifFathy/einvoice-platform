package com.einvoice.core.error;

/** Thrown when a buyer of type B or P (with high-value total) lacks required identity fields. */
public class BuyerIdentityRequiredException extends RuntimeException {

    public static final String CODE = "VALIDATION_ERROR";
    private final String buyerType;
    private final String reason;

    /**
     * Constructs a new exception for a missing buyer identity.
     *
     * @param buyerType the buyer type (B or P)
     * @param reason human-readable explanation
     */
    public BuyerIdentityRequiredException(String buyerType, String reason) {
        super("Buyer of type '" + buyerType + "' requires both id and name: " + reason);
        this.buyerType = buyerType;
        this.reason = reason;
    }

    public String getCode() {
        return CODE;
    }

    public String getBuyerType() {
        return buyerType;
    }

    public String getReason() {
        return reason;
    }
}
