package com.einvoice.api.eta.receipt.service;

import com.einvoice.core.domain.eta.EtaReceiptHeader;
import com.einvoice.core.domain.eta.EtaReceiptLine;
import com.einvoice.core.domain.eta.EtaReceiptLineTax;
import com.einvoice.core.domain.eta.document.EtaReceiptDocumentType;
import com.einvoice.core.domain.shared.DocumentState;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/** Maps between ETA receipt DTOs and JPA entities. */
public class EtaReceiptFormMapper {

    /**
     * Converts a write form to a JPA entity.
     *
     * @param form the write form
     * @param companyId the company identifier
     * @param authorityEnvironmentId the authority environment identifier
     * @param userId the user creating the receipt
     * @return the JPA entity
     */
    public static EtaReceiptHeader toEntity(EtaReceiptWriteForm form,
            UUID companyId, Short authorityEnvironmentId, UUID userId) {
        EtaReceiptHeader header = EtaReceiptHeader.builder()
                .companyId(companyId)
                .authorityEnvironmentId(authorityEnvironmentId)
                .receiptNumber(form.receiptNumber())
                .documentType(form.documentType())
                .documentTypeVersion(form.documentTypeVersion() != null
                        ? form.documentTypeVersion() : "1.2")
                .issueDatetime(form.issueDatetime())
                .sellerData(form.sellerData())
                .buyerData(form.buyerData())
                .posSerial(form.posSerial())
                .paymentMethod(form.paymentMethod())
                .currency(form.currency() != null ? form.currency() : "EGP")
                .totalSalesAmount(form.totalSalesAmount() != null
                        ? form.totalSalesAmount() : BigDecimal.ZERO)
                .totalCommercialDiscount(form.totalCommercialDiscount() != null
                        ? form.totalCommercialDiscount() : BigDecimal.ZERO)
                .extraDiscountAmount(form.extraDiscountAmount() != null
                        ? form.extraDiscountAmount() : BigDecimal.ZERO)
                .totalItemsDiscountAmount(
                        form.totalItemsDiscountAmount() != null
                                ? form.totalItemsDiscountAmount()
                                : BigDecimal.ZERO)
                .netAmount(form.netAmount() != null
                        ? form.netAmount() : BigDecimal.ZERO)
                .totalAmount(form.totalAmount() != null
                        ? form.totalAmount() : BigDecimal.ZERO)
                .exchangeRate(form.exchangeRate())
                .previousUuid(form.previousUuid())
                .referenceOldUuid(form.referenceOldUuid())
                .sOrderNameCode(form.sOrderNameCode())
                .orderDeliveryMode(form.orderDeliveryMode())
                .grossWeight(form.grossWeight())
                .netWeight(form.netWeight())
                .taxTotals(form.taxTotals())
                .extraReceiptDiscountData(form.extraReceiptDiscountData())
                .contractorData(form.contractorData())
                .beneficiaryData(form.beneficiaryData())
                .feesAmount(form.feesAmount() != null
                        ? form.feesAmount() : BigDecimal.ZERO)
                .adjustment(form.adjustment() != null
                        ? form.adjustment() : BigDecimal.ZERO)
                .erpReferenceId(form.erpReferenceId())
                .originalInvoiceNumber(form.originalInvoiceNumber())
                .originalReceiptId(form.originalReceiptId())
                .createdBy(userId)
                .build();

        List<EtaReceiptLine> lines = new ArrayList<>();
        if (form.lines() != null) {
            for (int i = 0; i < form.lines().size(); i++) {
                EtaReceiptLineForm lf = form.lines().get(i);
                EtaReceiptLine line = EtaReceiptLine.builder()
                        .header(header)
                        .lineNumber(lf.lineNumber() != null
                                ? lf.lineNumber() : i + 1)
                        .itemId(lf.itemId())
                        .internalCode(lf.internalCode())
                        .itemType(lf.itemType())
                        .itemCode(lf.itemCode())
                        .description(lf.description())
                        .unitType(lf.unitType())
                        .quantity(lf.quantity())
                        .unitValue(lf.unitValue())
                        .salesTotal(lf.salesTotal() != null
                                ? lf.salesTotal() : BigDecimal.ZERO)
                        .discountRate(lf.discountRate())
                        .discountAmount(lf.discountAmount() != null
                                ? lf.discountAmount() : BigDecimal.ZERO)
                        .itemsDiscount(lf.itemsDiscount() != null
                                ? lf.itemsDiscount() : BigDecimal.ZERO)
                        .valueDifference(lf.valueDifference() != null
                                ? lf.valueDifference() : BigDecimal.ZERO)
                        .totalTaxableFees(lf.totalTaxableFees() != null
                                ? lf.totalTaxableFees() : BigDecimal.ZERO)
                        .netTotal(lf.netTotal() != null
                                ? lf.netTotal() : BigDecimal.ZERO)
                        .taxAmount(lf.taxAmount() != null
                                ? lf.taxAmount() : BigDecimal.ZERO)
                        .total(lf.total() != null
                                ? lf.total() : BigDecimal.ZERO)
                        .build();

                if (lf.taxes() != null) {
                    List<EtaReceiptLineTax> taxes = lf.taxes().stream()
                            .map(tf -> EtaReceiptLineTax.builder()
                                    .line(line)
                                    .taxType(tf.taxType())
                                    .subType(tf.subType())
                                    .taxRate(tf.taxRate())
                                    .taxAmount(tf.taxAmount())
                                    .build())
                            .collect(Collectors.toList());
                    line.setTaxes(taxes);
                }
                lines.add(line);
            }
        }
        header.setLines(lines);
        return header;
    }

    /**
     * Converts a JPA entity to a response DTO.
     *
     * @param header the JPA entity
     * @return the response DTO
     */
    public static EtaReceiptResponse toResponse(EtaReceiptHeader header) {
        List<EtaReceiptLineResponse> lineResponses = header.getLines()
                .stream()
                .map(l -> new EtaReceiptLineResponse(
                        l.getId(), l.getItemId(), l.getInternalCode(),
                        l.getItemType(), l.getItemCode(), l.getDescription(),
                        l.getUnitType(), l.getQuantity(), l.getUnitValue(),
                        l.getSalesTotal(), l.getDiscountRate(),
                        l.getDiscountAmount(),
                        l.getItemsDiscount(), l.getValueDifference(),
                        l.getTotalTaxableFees(), l.getNetTotal(),
                        l.getTaxAmount(), l.getTotal(),
                        l.getTaxes().stream()
                                .map(t -> new EtaLineTaxResponse(
                                        t.getId(), t.getTaxType(),
                                        t.getSubType(),
                                        t.getTaxRate(), t.getTaxAmount()))
                                .collect(Collectors.toList())))
                .collect(Collectors.toList());

        return new EtaReceiptResponse(
                header.getId(), header.getCompanyId(), header.getBranchId(),
                header.getReceiptNumber(), header.getDocumentType(),
                header.getDocumentTypeVersion(), header.getIssueDatetime(),
                header.getSellerData(), header.getBuyerData(),
                header.getPosSerial(), header.getPaymentMethod(),
                header.getCurrency(),
                header.getTotalSalesAmount(), header.getTotalCommercialDiscount(),
                header.getExtraDiscountAmount(),
                header.getTotalItemsDiscountAmount(),
                header.getNetAmount(), header.getTotalAmount(),
                header.getExchangeRate(),
                header.getPreviousUuid(),
                header.getReferenceOldUuid(),
                header.getSOrderNameCode(),
                header.getOrderDeliveryMode(),
                header.getGrossWeight(),
                header.getNetWeight(),
                header.getTaxTotals(),
                header.getExtraReceiptDiscountData(),
                header.getContractorData(),
                header.getBeneficiaryData(),
                header.getFeesAmount(), header.getAdjustment(),
                header.getErpReferenceId(),
                header.getOriginalInvoiceNumber(),
                header.getOriginalReceiptId(),
                header.getState(), header.getVersion(),
                header.getEtaReceiptUuid(), header.getEtaSubmissionId(),
                header.getCreatedBy(), header.getCreatedAt(),
                header.getUpdatedAt(),
                lineResponses);
    }

    public record EtaReceiptWriteForm(
            String receiptNumber,
            EtaReceiptDocumentType documentType,
            String documentTypeVersion,
            OffsetDateTime issueDatetime,
            Map<String, Object> sellerData,
            Map<String, Object> buyerData,
            String posSerial,
            String paymentMethod,
            String currency,
            BigDecimal totalSalesAmount,
            BigDecimal totalCommercialDiscount,
            BigDecimal extraDiscountAmount,
            BigDecimal totalItemsDiscountAmount,
            BigDecimal netAmount,
            BigDecimal totalAmount,
            BigDecimal exchangeRate,
            String previousUuid,
            String referenceOldUuid,
            String sOrderNameCode,
            String orderDeliveryMode,
            BigDecimal grossWeight,
            BigDecimal netWeight,
            Map<String, Object> taxTotals,
            Map<String, Object> extraReceiptDiscountData,
            Map<String, Object> contractorData,
            Map<String, Object> beneficiaryData,
            BigDecimal feesAmount,
            BigDecimal adjustment,
            String erpReferenceId,
            String originalInvoiceNumber,
            UUID originalReceiptId,
            List<EtaReceiptLineForm> lines) {}

    public record EtaReceiptLineForm(
            Integer lineNumber,
            UUID itemId,
            String internalCode,
            String itemType,
            String itemCode,
            String description,
            String unitType,
            BigDecimal quantity,
            Map<String, Object> unitValue,
            BigDecimal salesTotal,
            BigDecimal discountRate,
            BigDecimal discountAmount,
            BigDecimal itemsDiscount,
            BigDecimal valueDifference,
            BigDecimal totalTaxableFees,
            BigDecimal netTotal,
            BigDecimal taxAmount,
            BigDecimal total,
            List<EtaLineTaxForm> taxes) {}

    public record EtaLineTaxForm(
            String taxType,
            String subType,
            BigDecimal taxRate,
            BigDecimal taxAmount) {}

    public record EtaReceiptResponse(
            UUID id, UUID companyId, UUID branchId,
            String receiptNumber, EtaReceiptDocumentType documentType,
            String documentTypeVersion, OffsetDateTime issueDatetime,
            Map<String, Object> sellerData,
            Map<String, Object> buyerData,
            String posSerial, String paymentMethod,
            String currency,
            BigDecimal totalSalesAmount, BigDecimal totalCommercialDiscount,
            BigDecimal extraDiscountAmount,
            BigDecimal totalItemsDiscountAmount,
            BigDecimal netAmount, BigDecimal totalAmount,
            BigDecimal exchangeRate,
            String previousUuid,
            String referenceOldUuid,
            String sOrderNameCode,
            String orderDeliveryMode,
            BigDecimal grossWeight,
            BigDecimal netWeight,
            Map<String, Object> taxTotals,
            Map<String, Object> extraReceiptDiscountData,
            Map<String, Object> contractorData,
            Map<String, Object> beneficiaryData,
            BigDecimal feesAmount, BigDecimal adjustment,
            String erpReferenceId,
            String originalInvoiceNumber,
            UUID originalReceiptId,
            DocumentState state, Integer version,
            String etaReceiptUuid, String etaSubmissionId,
            UUID createdBy, OffsetDateTime createdAt,
            OffsetDateTime updatedAt,
            List<EtaReceiptLineResponse> lines) {}

    public record EtaReceiptLineResponse(
            UUID id, UUID itemId, String internalCode,
            String itemType, String itemCode, String description,
            String unitType, BigDecimal quantity,
            Map<String, Object> unitValue,
            BigDecimal salesTotal, BigDecimal discountRate,
            BigDecimal discountAmount,
            BigDecimal itemsDiscount, BigDecimal valueDifference,
            BigDecimal totalTaxableFees, BigDecimal netTotal,
            BigDecimal taxAmount, BigDecimal total,
            List<EtaLineTaxResponse> taxes) {}

    public record EtaLineTaxResponse(
            UUID id, String taxType, String subType,
            BigDecimal taxRate, BigDecimal taxAmount) {}
}
