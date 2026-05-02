package com.einvoice.core.security;

import com.einvoice.core.domain.enums.Permission;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Marks a service method as requiring a specific fine-grained permission. */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresPermission {

    /**
     * The permission required to invoke the annotated method.
     *
     * @return the required permission
     */
    Permission value();
}
