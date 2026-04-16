package com.einvoice.api.invoice.dto;

import java.math.BigDecimal;

/** A single line item in an invoice response. */
public record InvoiceLineResponse(
        Long id,
        Long itemId,
        String descriptionEn,
        String descriptionAr,
        BigDecimal quantity,
        String unit,
        BigDecimal unitPrice,
        BigDecimal discountAmount,
        String vatCategory,
        BigDecimal vatRate,
        BigDecimal lineNetAmount,
        BigDecimal lineVatAmount,
        BigDecimal lineTotal,
        Integer sortOrder
) {}
