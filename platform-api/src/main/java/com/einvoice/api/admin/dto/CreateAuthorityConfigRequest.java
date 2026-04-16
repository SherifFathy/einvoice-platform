package com.einvoice.api.admin.dto;

import com.einvoice.core.domain.enums.Authority;
import com.einvoice.core.domain.enums.Environment;
import com.einvoice.core.domain.enums.InvoiceResetPolicy;
import jakarta.validation.constraints.NotNull;

/**
 * Request DTO for creating a new authority configuration for a branch.
 */
public record CreateAuthorityConfigRequest(
        @NotNull Authority authority,
        @NotNull Environment environment,
        String invoicePrefix,
        Long invoiceStartingNumber,
        InvoiceResetPolicy invoiceResetPolicy
) {}
