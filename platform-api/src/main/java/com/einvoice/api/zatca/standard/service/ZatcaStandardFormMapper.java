package com.einvoice.api.zatca.standard.service;

import com.einvoice.core.domain.shared.DocumentState;
import com.einvoice.core.domain.zatca.ZatcaStandardHeader;
import com.einvoice.core.domain.zatca.ZatcaStandardLine;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class ZatcaStandardFormMapper {

    public static ZatcaStandardHeader toEntity(ZatcaStandardWriteForm form,
            UUID companyId, Short authorityEnvironmentId, UUID userId) {
        ZatcaStandardHeader header = ZatcaStandardHeader.builder()
                .companyId(companyId)
                .authorityEnvironmentId(authorityEnvironmentId)
                .invoiceNumber(form.invoiceNumber())
                .invoiceTypeCode(form.invoiceTypeCode() != null
                        ? form.invoiceTypeCode() : "388")
                .transactionTypeCode(form.transactionTypeCode())
                .issueDate(form.issueDate())
                .issueTime(form.issueTime())
                .supplyDate(form.supplyDate())
                .supplyEndDate(form.supplyEndDate())
                .sellerData(form.sellerData())
                .buyerData(form.buyerData())
                .currency(form.currency() != null ? form.currency() : "SAR")
                .taxCurrency(form.taxCurrency() != null
                        ? form.taxCurrency() : "SAR")
                .prepaidAmount(form.prepaidAmount() != null
                        ? form.prepaidAmount() : BigDecimal.ZERO)
                .originalInvoiceId(form.originalInvoiceId())
                .createdBy(userId)
                .build();

        List<ZatcaStandardLine> lines = new ArrayList<>();
        if (form.lines() != null) {
            for (int i = 0; i < form.lines().size(); i++) {
                ZatcaStandardLineForm lf = form.lines().get(i);
                ZatcaStandardLine line = ZatcaStandardLine.builder()
                        .header(header)
                        .lineNumber(lf.lineNumber() != null
                                ? lf.lineNumber() : i + 1)
                        .itemId(lf.itemId())
                        .itemCode(lf.itemCode())
                        .description(lf.description())
                        .unitType(lf.unitType())
                        .quantity(lf.quantity())
                        .unitPrice(lf.unitPrice())
                        .lineExtensionAmount(lf.lineExtensionAmount() != null
                                ? lf.lineExtensionAmount() : BigDecimal.ZERO)
                        .discountAmount(lf.discountAmount() != null
                                ? lf.discountAmount() : BigDecimal.ZERO)
                        .allowanceAmount(lf.allowanceAmount() != null
                                ? lf.allowanceAmount() : BigDecimal.ZERO)
                        .netAmount(lf.netAmount() != null
                                ? lf.netAmount() : BigDecimal.ZERO)
                        .vatCategoryCode(lf.vatCategoryCode())
                        .vatRate(lf.vatRate())
                        .vatAmount(lf.vatAmount() != null
                                ? lf.vatAmount() : BigDecimal.ZERO)
                        .exemptionReasonCode(lf.exemptionReasonCode())
                        .exemptionReasonText(lf.exemptionReasonText())
                        .build();
                lines.add(line);
            }
        }
        header.setLines(lines);
        return header;
    }

    public static ZatcaStandardResponse toResponse(ZatcaStandardHeader header) {
        List<ZatcaStandardLineResponse> lineResponses = header.getLines()
                .stream()
                .map(l -> new ZatcaStandardLineResponse(
                        l.getId(), l.getItemId(), l.getItemCode(),
                        l.getDescription(), l.getUnitType(),
                        l.getQuantity(), l.getUnitPrice(),
                        l.getLineExtensionAmount(), l.getDiscountAmount(),
                        l.getAllowanceAmount(), l.getNetAmount(),
                        l.getVatCategoryCode(), l.getVatRate(),
                        l.getVatAmount(),
                        l.getExemptionReasonCode(),
                        l.getExemptionReasonText()))
                .collect(Collectors.toList());

        return new ZatcaStandardResponse(
                header.getId(), header.getCompanyId(),
                header.getBranchId(),
                header.getInvoiceNumber(), header.getInvoiceTypeCode(),
                header.getTransactionTypeCode(),
                header.getIssueDate(), header.getIssueTime(),
                header.getSupplyDate(), header.getSupplyEndDate(),
                header.getSellerData(), header.getBuyerData(),
                header.getCurrency(), header.getTaxCurrency(),
                header.getLineExtensionAmount(),
                header.getAllowanceTotalAmount(),
                header.getTaxExclusiveAmount(), header.getTaxAmount(),
                header.getTaxInclusiveAmount(), header.getPrepaidAmount(),
                header.getPayableAmount(),
                header.getInvoiceCounterValue(),
                header.getPreviousInvoiceHash(),
                header.getInvoiceHash(), header.getQrCodeBase64(),
                header.getClearanceStatus(),
                header.getZatcaResponseData(),
                header.getOriginalInvoiceId(),
                header.getStatus(), header.getVersion(),
                header.getZatcaUuid(),
                header.getCreatedBy(), header.getCreatedAt(),
                header.getUpdatedAt(),
                lineResponses);
    }

    public record ZatcaStandardWriteForm(
            String invoiceNumber,
            String invoiceTypeCode,
            String transactionTypeCode,
            LocalDate issueDate,
            LocalTime issueTime,
            LocalDate supplyDate,
            LocalDate supplyEndDate,
            Map<String, Object> sellerData,
            Map<String, Object> buyerData,
            String currency,
            String taxCurrency,
            BigDecimal prepaidAmount,
            UUID originalInvoiceId,
            List<ZatcaStandardLineForm> lines) {}

    public record ZatcaStandardLineForm(
            Integer lineNumber,
            UUID itemId,
            String itemCode,
            String description,
            String unitType,
            BigDecimal quantity,
            BigDecimal unitPrice,
            BigDecimal lineExtensionAmount,
            BigDecimal discountAmount,
            BigDecimal allowanceAmount,
            BigDecimal netAmount,
            String vatCategoryCode,
            BigDecimal vatRate,
            BigDecimal vatAmount,
            String exemptionReasonCode,
            String exemptionReasonText) {}

    public record ZatcaStandardResponse(
            UUID id, UUID companyId, UUID branchId,
            String invoiceNumber, String invoiceTypeCode,
            String transactionTypeCode,
            LocalDate issueDate, LocalTime issueTime,
            LocalDate supplyDate, LocalDate supplyEndDate,
            Map<String, Object> sellerData,
            Map<String, Object> buyerData,
            String currency, String taxCurrency,
            BigDecimal lineExtensionAmount,
            BigDecimal allowanceTotalAmount,
            BigDecimal taxExclusiveAmount, BigDecimal taxAmount,
            BigDecimal taxInclusiveAmount, BigDecimal prepaidAmount,
            BigDecimal payableAmount,
            Long invoiceCounterValue,
            String previousInvoiceHash,
            String invoiceHash, String qrCodeBase64,
            String clearanceStatus,
            Map<String, Object> zatcaResponseData,
            UUID originalInvoiceId,
            DocumentState status, Long version,
            String zatcaUuid,
            UUID createdBy, OffsetDateTime createdAt,
            OffsetDateTime updatedAt,
            List<ZatcaStandardLineResponse> lines) {}

    public record ZatcaStandardLineResponse(
            UUID id, UUID itemId, String itemCode,
            String description, String unitType,
            BigDecimal quantity, BigDecimal unitPrice,
            BigDecimal lineExtensionAmount, BigDecimal discountAmount,
            BigDecimal allowanceAmount, BigDecimal netAmount,
            String vatCategoryCode, BigDecimal vatRate,
            BigDecimal vatAmount,
            String exemptionReasonCode,
            String exemptionReasonText) {}
}
