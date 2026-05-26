package com.einvoice.core.error;

/** Javadoc. */
public class InvalidLifecycleTransitionException extends RuntimeException {
    public static final String CODE = "INVALID_LIFECYCLE_TRANSITION";
    private final String fromState;
    private final String action;

    /**
     * Construct the exception.
     *
     * @param message   the error message
     * @param fromState the originating state
     * @param action    the disallowed action
     */
    public InvalidLifecycleTransitionException(String message, String fromState, String action) {
        super(message);
        this.fromState = fromState;
        this.action = action;
    }

    public String getCode() {
        return CODE;
    }

    public String getFromState() {
        return fromState;
    }

    public String getAction() {
        return action;
    }
}
