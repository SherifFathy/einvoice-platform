package com.einvoice.core.error;

/** Thrown when a required token field is missing for ETA Production environment. */
public class ProductionTokenRequiredException extends RuntimeException {

    public static final String CODE = "MISSING_PRODUCTION_TOKEN_FIELDS";

    private final String field;
    private final String value;

    /**
     * Constructs a new ProductionTokenRequiredException.
     *
     * @param field the field name that is required
     * @param value the value that was provided (may be null)
     */
    public ProductionTokenRequiredException(String field, String value) {
        super("Field '" + field + "' is required for ETA Production environment");
        this.field = field;
        this.value = value;
    }

    public String getCode() {
        return CODE;
    }

    public String getField() {
        return field;
    }

    public String getValue() {
        return value;
    }
}
