package com.einvoice.api.invoice.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Summary invoice response for list views. */
public record InvoiceListResponse(
        UUID id,
        String invoiceNumber,
        String type,
        String status,
        LocalDate issueDate,
        String buyerName,
        BigDecimal totalWithVat,
        BigDecimal amountDue,
        String authority,
        OffsetDateTime createdAt
) {}
