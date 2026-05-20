package com.einvoice.api.eta.invoice.service;

import com.einvoice.core.domain.eta.EtaInvoiceHeader;
import com.einvoice.core.domain.eta.EtaInvoiceLine;
import com.einvoice.core.domain.eta.EtaInvoiceLineTax;
import com.einvoice.core.domain.eta.document.EtaInvoiceDocumentType;
import com.einvoice.core.domain.shared.DocumentState;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/** Maps between ETA invoice DTOs and JPA entities. */
public class EtaInvoiceFormMapper {

    /**
     * Converts a write form to a JPA entity.
     *
     * @param form the write form
     * @param companyId the company identifier
     * @param authorityEnvironmentId the authority environment identifier
     * @param userId the user creating the invoice
     * @return the JPA entity
     */
    public static EtaInvoiceHeader toEntity(EtaInvoiceWriteForm form, UUID companyId,
            Short authorityEnvironmentId, UUID userId) {
        EtaInvoiceHeader header = EtaInvoiceHeader.builder()
                .companyId(companyId)
                .authorityEnvironmentId(authorityEnvironmentId)
                .invoiceNumber(form.invoiceNumber())
                .documentType(form.documentType())
                .documentTypeVersion(form.documentTypeVersion() != null
                        ? form.documentTypeVersion() : "1.0")
                .issueDatetime(form.issueDatetime())
                .serviceDeliveryDate(form.serviceDeliveryDate())
                .sellerData(form.sellerData())
                .buyerData(form.buyerData())
                .taxpayerActivityCode(form.taxpayerActivityCode())
                .purchaseOrderReference(form.purchaseOrderReference())
                .purchaseOrderDescription(form.purchaseOrderDescription())
                .salesOrderReference(form.salesOrderReference())
                .salesOrderDescription(form.salesOrderDescription())
                .paymentData(form.paymentData())
                .deliveryData(form.deliveryData())
                .currency(form.currency() != null ? form.currency() : "EGP")
                .totalSalesAmount(form.totalSalesAmount())
                .totalDiscountAmount(form.totalDiscountAmount() != null
                        ? form.totalDiscountAmount() : BigDecimal.ZERO)
                .extraDiscountAmount(form.extraDiscountAmount() != null
                        ? form.extraDiscountAmount() : BigDecimal.ZERO)
                .totalItemsDiscountAmount(form.totalItemsDiscountAmount() != null
                        ? form.totalItemsDiscountAmount() : BigDecimal.ZERO)
                .netAmount(form.netAmount())
                .totalAmount(form.totalAmount())
                .originalDocumentId(form.originalDocumentId())
                .createdBy(userId)
                .build();

        List<EtaInvoiceLine> lines = new ArrayList<>();
        if (form.lines() != null) {
            for (int i = 0; i < form.lines().size(); i++) {
                EtaInvoiceLineForm lf = form.lines().get(i);
                EtaInvoiceLine line = EtaInvoiceLine.builder()
                        .header(header)
                        .lineNumber(lf.lineNumber() != null ? lf.lineNumber() : i + 1)
                        .itemId(lf.itemId())
                        .internalCode(lf.internalCode())
                        .itemType(lf.itemType())
                        .itemCode(lf.itemCode())
                        .description(lf.description())
                        .unitType(lf.unitType())
                        .quantity(lf.quantity())
                        .unitValue(lf.unitValue())
                        .salesTotal(lf.salesTotal() != null ? lf.salesTotal() : BigDecimal.ZERO)
                        .discountRate(lf.discountRate())
                        .discountAmount(lf.discountAmount() != null
                                ? lf.discountAmount() : BigDecimal.ZERO)
                        .itemsDiscount(lf.itemsDiscount() != null
                                ? lf.itemsDiscount() : BigDecimal.ZERO)
                        .valueDifference(lf.valueDifference() != null
                                ? lf.valueDifference() : BigDecimal.ZERO)
                        .totalTaxableFees(lf.totalTaxableFees() != null
                                ? lf.totalTaxableFees() : BigDecimal.ZERO)
                        .netTotal(lf.netTotal() != null ? lf.netTotal() : BigDecimal.ZERO)
                        .taxAmount(lf.taxAmount() != null ? lf.taxAmount() : BigDecimal.ZERO)
                        .total(lf.total() != null ? lf.total() : BigDecimal.ZERO)
                        .build();

                if (lf.taxes() != null) {
                    List<EtaInvoiceLineTax> taxes = lf.taxes().stream()
                            .map(tf -> EtaInvoiceLineTax.builder()
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
    public static EtaInvoiceResponse toResponse(EtaInvoiceHeader header) {
        List<EtaInvoiceLineResponse> lineResponses = header.getLines().stream()
                .map(l -> new EtaInvoiceLineResponse(
                        l.getId(), l.getItemId(), l.getInternalCode(),
                        l.getItemType(), l.getItemCode(), l.getDescription(),
                        l.getUnitType(), l.getQuantity(), l.getUnitValue(),
                        l.getSalesTotal(), l.getDiscountRate(), l.getDiscountAmount(),
                        l.getItemsDiscount(), l.getValueDifference(),
                        l.getTotalTaxableFees(), l.getNetTotal(),
                        l.getTaxAmount(), l.getTotal(),
                        l.getTaxes().stream()
                                .map(t -> new EtaLineTaxResponse(
                                        t.getId(), t.getTaxType(), t.getSubType(),
                                        t.getTaxRate(), t.getTaxAmount()))
                                .collect(Collectors.toList())))
                .collect(Collectors.toList());

        return new EtaInvoiceResponse(
                header.getId(), header.getCompanyId(), header.getBranchId(),
                header.getInvoiceNumber(), header.getDocumentType(),
                header.getDocumentTypeVersion(), header.getIssueDatetime(),
                header.getServiceDeliveryDate(),
                header.getSellerData(), header.getBuyerData(),
                header.getTaxpayerActivityCode(),
                header.getPurchaseOrderReference(), header.getPurchaseOrderDescription(),
                header.getSalesOrderReference(), header.getSalesOrderDescription(),
                header.getProformaInvoiceNumber(),
                header.getPaymentData(), header.getDeliveryData(),
                header.getCurrency(),
                header.getTotalSalesAmount(), header.getTotalDiscountAmount(),
                header.getExtraDiscountAmount(), header.getTotalItemsDiscountAmount(),
                header.getNetAmount(), header.getTotalAmount(),
                header.getOriginalDocumentId(),
                header.getState(), header.getVersion(),
                header.getEtaUuid(), header.getEtaLongId(), header.getEtaSubmissionId(),
                header.getCreatedBy(), header.getCreatedAt(), header.getUpdatedAt(),
                lineResponses);
    }

    public record EtaInvoiceWriteForm(
            String invoiceNumber,
            EtaInvoiceDocumentType documentType,
            String documentTypeVersion,
            OffsetDateTime issueDatetime,
            LocalDate serviceDeliveryDate,
            Map<String, Object> sellerData,
            Map<String, Object> buyerData,
            String taxpayerActivityCode,
            String purchaseOrderReference,
            String purchaseOrderDescription,
            String salesOrderReference,
            String salesOrderDescription,
            String proformaInvoiceNumber,
            Map<String, Object> paymentData,
            Map<String, Object> deliveryData,
            String currency,
            BigDecimal totalSalesAmount,
            BigDecimal totalDiscountAmount,
            BigDecimal extraDiscountAmount,
            BigDecimal totalItemsDiscountAmount,
            BigDecimal netAmount,
            BigDecimal totalAmount,
            UUID originalDocumentId,
            List<EtaInvoiceLineForm> lines) {}

    public record EtaInvoiceLineForm(
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

    public record EtaInvoiceResponse(
            UUID id, UUID companyId, UUID branchId,
            String invoiceNumber, EtaInvoiceDocumentType documentType,
            String documentTypeVersion, OffsetDateTime issueDatetime,
            LocalDate serviceDeliveryDate,
            Map<String, Object> sellerData, Map<String, Object> buyerData,
            String taxpayerActivityCode,
            String purchaseOrderReference, String purchaseOrderDescription,
            String salesOrderReference, String salesOrderDescription,
            String proformaInvoiceNumber,
            Map<String, Object> paymentData, Map<String, Object> deliveryData,
            String currency,
            BigDecimal totalSalesAmount, BigDecimal totalDiscountAmount,
            BigDecimal extraDiscountAmount, BigDecimal totalItemsDiscountAmount,
            BigDecimal netAmount, BigDecimal totalAmount,
            UUID originalDocumentId,
            DocumentState state, Integer version,
            String etaUuid, String etaLongId, String etaSubmissionId,
            UUID createdBy, OffsetDateTime createdAt, OffsetDateTime updatedAt,
            List<EtaInvoiceLineResponse> lines) {}

    public record EtaInvoiceLineResponse(
            UUID id, UUID itemId, String internalCode,
            String itemType, String itemCode, String description,
            String unitType, BigDecimal quantity, Map<String, Object> unitValue,
            BigDecimal salesTotal, BigDecimal discountRate, BigDecimal discountAmount,
            BigDecimal itemsDiscount, BigDecimal valueDifference,
            BigDecimal totalTaxableFees, BigDecimal netTotal,
            BigDecimal taxAmount, BigDecimal total,
            List<EtaLineTaxResponse> taxes) {}

    public record EtaLineTaxResponse(
            UUID id, String taxType, String subType,
            BigDecimal taxRate, BigDecimal taxAmount) {}
}
