package com.einvoice.api.company.dto;

import com.einvoice.core.domain.enums.Authority;
import com.einvoice.core.domain.enums.Environment;
import jakarta.validation.constraints.NotNull;

public record AuthorityConfigRequest(
        @NotNull Authority authority,
        @NotNull Environment environment,
        String credentials,
        String certificate,
        String privateKey,
        String invoicePrefix,
        Long invoiceStartingNumber,
        String invoiceResetPolicy
) {}
