package com.einvoice.core.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Javadoc. */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresPermission {

    /** Javadoc.
     * @return the transaction type
     */
    String transactionType();

    /** Javadoc.
     * @return the permission action code
     */
    String action();
}
