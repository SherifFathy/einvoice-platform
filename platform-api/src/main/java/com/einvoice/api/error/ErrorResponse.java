package com.einvoice.api.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        String code,
        String message,
        Map<String, Object> details
) {
    public ErrorResponse(String code, String message) {
        this(code, message, null);
    }
}
