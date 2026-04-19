package com.einvoice.api.invoice.dto;

import com.einvoice.core.service.validation.ValidationLayer;
import java.util.List;

public record ValidationResultResponse(
        boolean valid,
        List<ValidationItem> errors,
        List<ValidationItem> warnings
) {
    public record ValidationItem(
            ValidationLayer layer,
            String authority,
            String ruleId,
            String field,
            String message,
            String severity
    ) {}
}
