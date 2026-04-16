package com.einvoice.api.invoice.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Request body for creating or updating a draft invoice. */
public record CreateInvoiceRequest(
        @NotNull String type,
        Map<String, Boolean> subtypeFlags,
        @NotNull LocalDate issueDate,
        LocalDate supplyDate,
        LocalDate supplyEndDate,
        String currency,
        Long buyerId,
        @NotNull Long branchId,
        @NotNull String authority,
        String paymentMeansCode,
        String paymentTerms,
        BigDecimal prepaidAmount,
        BigDecimal totalAllowances,
        java.util.UUID originalInvoiceId,
        String notes,
        @Valid @NotNull List<InvoiceLineRequest> lines
) {}
