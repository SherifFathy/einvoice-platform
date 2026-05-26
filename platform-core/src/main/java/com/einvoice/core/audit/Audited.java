package com.einvoice.core.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Marks a service method for automatic audit logging via AOP. */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Audited {

    /**
     * The action being performed, e.g. "company.create".
     * @return the action string
     */
    String action();

    /**
     * The type of entity being acted upon, e.g. "Company".
     * @return the entity type string
     */
    String entityType();

    /**
     * The JPA entity class to load the before-state from.
     * Required for update/delete methods where the first argument is an ID (Long),
     * not the entity itself. Defaults to {@code void.class} meaning "infer from args".
     * @return the entity class
     */
    Class<?> entityClass() default void.class;
}
