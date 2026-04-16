package com.einvoice.api.admin.dto;

import com.einvoice.core.domain.enums.InvoiceResetPolicy;

/**
 * Request DTO for updating authority config invoice sequence settings.
 */
public record UpdateAuthorityConfigRequest(
        String invoicePrefix,
        Long invoiceStartingNumber,
        InvoiceResetPolicy invoiceResetPolicy
) {}
