package com.einvoice.api.error;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Conflict response matching the OpenAPI ConflictBody schema. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ConflictResponse(
        String code,
        String message,
        Integer expectedVersion,
        Integer actualVersion,
        Object current) {
}
