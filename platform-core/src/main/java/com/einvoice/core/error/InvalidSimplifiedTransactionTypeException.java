package com.einvoice.core.error;

/** Thrown when a Simplified document has an invalid transaction type code. */
public class InvalidSimplifiedTransactionTypeException extends RuntimeException {

    private final String transactionTypeCode;

    public InvalidSimplifiedTransactionTypeException(String message,
            String transactionTypeCode) {
        super(message);
        this.transactionTypeCode = transactionTypeCode;
    }

    public String getCode() {
        return "INVALID_SIMPLIFIED_TRANSACTION_TYPE";
    }

    public String getTransactionTypeCode() {
        return transactionTypeCode;
    }
}
