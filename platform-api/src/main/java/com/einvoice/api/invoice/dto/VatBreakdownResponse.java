package com.einvoice.api.invoice.dto;

import java.math.BigDecimal;

/** Aggregated VAT breakdown entry in an invoice response. */
public record VatBreakdownResponse(
        String vatCategoryCode,
        BigDecimal vatRate,
        BigDecimal taxableAmount,
        BigDecimal taxAmount
) {}
