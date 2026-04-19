package com.einvoice.core.service.validation;

import com.einvoice.core.domain.enums.Authority;

public record ValidationError(
        ValidationLayer layer,
        Authority authority,
        String ruleId,
        String field,
        String message,
        ValidationSeverity severity
) {}
