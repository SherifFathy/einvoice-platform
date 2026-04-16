package com.einvoice.api.invoice.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Full invoice detail response including lines and VAT breakdown. */
public record InvoiceDetailResponse(
        UUID id,
        String invoiceNumber,
        String type,
        Map<String, Boolean> subtypeFlags,
        String status,
        LocalDate issueDate,
        LocalDate supplyDate,
        LocalDate supplyEndDate,
        String currency,
        Long buyerId,
        String buyerName,
        Map<String, Object> buyerData,
        Map<String, Object> sellerData,
        String paymentMeansCode,
        String paymentTerms,
        BigDecimal prepaidAmount,
        BigDecimal totalLineNet,
        BigDecimal totalAllowances,
        BigDecimal totalWithoutVat,
        BigDecimal totalVat,
        BigDecimal totalWithVat,
        BigDecimal amountDue,
        String authority,
        String environment,
        Long branchId,
        UUID originalInvoiceId,
        String notes,
        OffsetDateTime createdAt,
        List<InvoiceLineResponse> lines,
        List<VatBreakdownResponse> vatBreakdown
) {}
