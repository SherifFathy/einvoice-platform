package com.einvoice.api.invoice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/** A single line item within an invoice request. */
public record InvoiceLineRequest(
        Long itemId,
        @NotBlank String descriptionEn,
        String descriptionAr,
        @NotNull BigDecimal quantity,
        @NotBlank String unit,
        @NotNull BigDecimal unitPrice,
        BigDecimal discountAmount,
        @NotBlank String vatCategory,
        @NotNull BigDecimal vatRate,
        @NotNull Integer sortOrder
) {}
