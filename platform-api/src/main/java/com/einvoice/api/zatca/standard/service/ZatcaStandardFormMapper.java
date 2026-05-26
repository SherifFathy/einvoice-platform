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

/** Maps between ZATCA standard write forms, entities, and response DTOs. */
public class ZatcaStandardFormMapper {

    /**
     * Converts a write form into a new ZATCA standard header entity.
     *
     * @param form the write form
     * @param companyId the company identifier
     * @param authorityEnvironmentId the authority environment
     * @param userId the creating user
     * @return a new header entity with promoted party fields
     */
    public static ZatcaStandardHeader toEntity(ZatcaStandardWriteForm form,
            UUID companyId, Short authorityEnvironmentId, UUID userId) {
        ZatcaStandardHeader header = ZatcaStandardHeader.builder()
                .companyId(companyId)
                .authorityEnvironmentId(authorityEnvironmentId)
                .invoiceNumber(form.invoiceNumber())
                .invoiceTypeCode(form.invoiceTypeCode() != null
                        ? form.invoiceTypeCode() : "388")
                .transactionTypeCode(form.transactionTypeCode())
                .businessProcessCode(form.businessProcessCode() != null
                        ? form.businessProcessCode() : "reporting:1.0")
                .issuanceReason(form.issuanceReason())
                .billingReferenceId(form.billingReferenceId())
                .originalInvoiceNumber(form.originalInvoiceNumber())
                .erpReferenceId(form.erpReferenceId())
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
                .paymentMeansCode(form.paymentMeansCode())
                .paymentMeansText(form.paymentMeansText())
                .originalInvoiceId(form.originalInvoiceId())
                .createdBy(userId)
                .build();

        promoteSellerFields(header, form.sellerData());
        promoteBuyerFields(header, form.buyerData());

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

    /** Promotes seller party fields from JSONB to dedicated columns. */
    static void promoteSellerFields(ZatcaStandardHeader header,
            Map<String, Object> data) {
        if (data == null) {
            return;
        }
        header.setSellerVatNumber(getString(data, "vatNumber",
                "taxRegistrationNumber"));
        header.setSellerGroupVatNumber(getString(data, "groupVatNumber"));
        header.setSellerBuildingNumber(getString(data, "buildingNumber",
                "addressBuildingNumber"));
        header.setSellerAdditionalNumber(getString(data, "additionalNumber",
                "addressAdditionalNumber"));
        header.setSellerPostalCode(getString(data, "postalZone",
                "addressPostalZone"));
        header.setSellerCountryCode(
                getString(data, "countryCode", "addressCountryCode"));
        Object pid = data.get("partyIdentification");
        if (pid instanceof Map<?, ?> m) {
            header.setSellerPartyId(m.get("id") != null
                    ? m.get("id").toString() : null);
            header.setSellerPartyIdScheme(m.get("scheme") != null
                    ? m.get("scheme").toString() : null);
        } else {
            header.setSellerPartyId(getString(data, "partyId"));
            header.setSellerPartyIdScheme(getString(data, "partyIdScheme"));
        }
        if (header.getSellerCountryCode() == null) {
            header.setSellerCountryCode("SA");
        }
    }

    static void promoteBuyerFields(ZatcaStandardHeader header,
            Map<String, Object> data) {
        if (data == null) {
            return;
        }
        header.setBuyerVatNumber(getString(data, "vatNumber",
                "taxRegistrationNumber"));
        header.setBuyerGroupVatNumber(getString(data, "groupVatNumber"));
        header.setBuyerBuildingNumber(getString(data, "buildingNumber",
                "addressBuildingNumber"));
        header.setBuyerAdditionalNumber(getString(data, "additionalNumber",
                "addressAdditionalNumber"));
        header.setBuyerPostalCode(getString(data, "postalZone",
                "addressPostalZone"));
        header.setBuyerCountryCode(
                getString(data, "countryCode", "addressCountryCode"));
        Object pid = data.get("partyIdentification");
        if (pid instanceof Map<?, ?> m) {
            header.setBuyerPartyId(m.get("id") != null
                    ? m.get("id").toString() : null);
            header.setBuyerPartyIdScheme(m.get("scheme") != null
                    ? m.get("scheme").toString() : null);
        } else {
            header.setBuyerPartyId(getString(data, "partyId"));
            header.setBuyerPartyIdScheme(getString(data, "partyIdScheme"));
        }
    }

    private static String getString(Map<String, Object> data,
            String... keys) {
        for (String key : keys) {
            Object val = data.get(key);
            if (val != null) {
                return val.toString();
            }
        }
        return null;
    }

    /**
     * Converts a header entity into a response DTO.
     *
     * @param header the header entity
     * @return the response DTO
     */
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
                header.getBusinessProcessCode(),
                header.getIssuanceReason(),
                header.getBillingReferenceId(),
                header.getOriginalInvoiceNumber(),
                header.getErpReferenceId(),
                header.getIssueDate(), header.getIssueTime(),
                header.getSupplyDate(), header.getSupplyEndDate(),
                header.getSellerData(), header.getBuyerData(),
                header.getSellerVatNumber(),
                header.getSellerCountryCode(),
                header.getBuyerVatNumber(),
                header.getBuyerCountryCode(),
                header.getCurrency(), header.getTaxCurrency(),
                header.getLineExtensionAmount(),
                header.getAllowanceTotalAmount(),
                header.getTaxExclusiveAmount(), header.getTaxAmount(),
                header.getTaxAmountAccountingCurrency(),
                header.getTaxInclusiveAmount(), header.getPrepaidAmount(),
                header.getRoundingAmount(),
                header.getPayableAmount(),
                header.getPaymentMeansCode(),
                header.getPaymentMeansText(),
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
            String businessProcessCode,
            String issuanceReason,
            String billingReferenceId,
            String originalInvoiceNumber,
            String erpReferenceId,
            LocalDate issueDate,
            LocalTime issueTime,
            LocalDate supplyDate,
            LocalDate supplyEndDate,
            Map<String, Object> sellerData,
            Map<String, Object> buyerData,
            String currency,
            String taxCurrency,
            BigDecimal prepaidAmount,
            String paymentMeansCode,
            String paymentMeansText,
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
            String businessProcessCode,
            String issuanceReason,
            String billingReferenceId,
            String originalInvoiceNumber,
            String erpReferenceId,
            LocalDate issueDate, LocalTime issueTime,
            LocalDate supplyDate, LocalDate supplyEndDate,
            Map<String, Object> sellerData,
            Map<String, Object> buyerData,
            String sellerVatNumber,
            String sellerCountryCode,
            String buyerVatNumber,
            String buyerCountryCode,
            String currency, String taxCurrency,
            BigDecimal lineExtensionAmount,
            BigDecimal allowanceTotalAmount,
            BigDecimal taxExclusiveAmount, BigDecimal taxAmount,
            BigDecimal taxAmountAccountingCurrency,
            BigDecimal taxInclusiveAmount, BigDecimal prepaidAmount,
            BigDecimal roundingAmount,
            BigDecimal payableAmount,
            String paymentMeansCode,
            String paymentMeansText,
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
