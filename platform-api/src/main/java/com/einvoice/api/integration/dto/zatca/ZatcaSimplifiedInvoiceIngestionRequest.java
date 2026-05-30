package com.einvoice.api.integration.dto.zatca;

import com.einvoice.api.integration.dto.shared.IntegrationDocumentStatus;
import com.einvoice.api.integration.dto.shared.IntegrationEnvironment;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * ZATCA Simplified (B2C) invoice ingestion request -- UBL Fatoora v2.0.3 shape.
 * Buyer is optional (anonymous retail is the dominant case).
 * {@code transactionTypeCode} bit-1 = 0 enforces Simplified shape.
 * The status field maps to {@code reporting_status} on the simplified header.
 */
public record ZatcaSimplifiedInvoiceIngestionRequest(
        @NotBlank @Size(max = 100) String companyRegistrationNumber,
        @NotNull IntegrationEnvironment environment,
        @NotNull IntegrationDocumentStatus status,
        @Size(max = 100) String erpReferenceId,
        @NotBlank @Size(max = 100) String invoiceNumber,
        @NotBlank @Pattern(regexp = "388|381|383") String invoiceTypeCode,
        @NotBlank @Pattern(regexp = "0[01]{3}0000") String transactionTypeCode,
        @NotNull LocalDate issueDate,
        @NotNull LocalTime issueTime,
        @NotNull @Valid SellerParty seller,
        @Valid BuyerParty buyer,
        @NotBlank @Pattern(regexp = "SAR",
                message = "Sprint 1 supports SAR only; non-SAR FX deferred to Sprint 2") String currency,
        @NotNull @DecimalMin("0") BigDecimal lineExtensionAmount,
        @DecimalMin("0") BigDecimal allowanceTotalAmount,
        @NotNull @DecimalMin("0") BigDecimal taxExclusiveAmount,
        @NotNull @DecimalMin("0") BigDecimal taxAmount,
        @NotNull @DecimalMin("0") BigDecimal taxInclusiveAmount,
        @DecimalMin("0") BigDecimal prepaidAmount,
        @NotNull @DecimalMin("0") BigDecimal payableAmount,
        String paymentMeansCode,
        String paymentMeansText,
        Long invoiceCounterValue,
        String previousInvoiceHash,
        String invoiceHash,
        String qrCodeBase64,
        String reportingStatus,
        @Size(max = 100) String originalInvoiceNumber,
        @Valid List<@Valid HeaderAllowance> allowances,
        @NotEmpty @Valid List<@Valid LineItem> lines) {

    /**
     * Seller party for a ZATCA Simplified invoice.
     * Saudi 15-digit VAT number is required per UBL BR-KSA-EN16931.
     */
    public record SellerParty(
            @NotBlank @Size(max = 50) String partyId,
            @Size(max = 10) String partyIdScheme,
            @NotBlank @Pattern(regexp = "3[0-9]{14}") String vatNumber,
            @Size(max = 15) String groupVatNumber,
            @NotBlank @Size(max = 4) String buildingNumber,
            @Size(max = 4) String additionalNumber,
            @NotBlank @Size(max = 5) String postalCode,
            @Size(max = 100) String street,
            @Size(max = 100) String additionalStreetName,
            @Size(max = 100) String plotIdentification,
            @NotBlank @Size(max = 100) String city,
            @NotBlank @Size(max = 2) String countryCode) {}

    /**
     * Buyer party for a ZATCA Simplified invoice. All fields are optional
     * (Simplified = B2C, anonymous retail is the dominant case).
     */
    public record BuyerParty(
            @Size(max = 50) String partyId,
            @Size(max = 10) String partyIdScheme,
            @Pattern(regexp = "3[0-9]{14}") String vatNumber,
            @Size(max = 15) String groupVatNumber,
            @Size(max = 4) String buildingNumber,
            @Size(max = 4) String additionalNumber,
            @Size(max = 5) String postalCode,
            @Size(max = 100) String street,
            @Size(max = 100) String additionalStreetName,
            @Size(max = 100) String plotIdentification,
            @Size(max = 100) String city,
            @Size(max = 2) String countryCode) {}

    /**
     * Header-level allowance (BG-20). Materialised into
     * {@code zatca_simplified_allowances} by the ingestion service.
     */
    public record HeaderAllowance(
            @NotNull Short sequence,
            @NotNull @DecimalMin("0") BigDecimal amount,
            @DecimalMin("0") BigDecimal baseAmount,
            @DecimalMin("0") BigDecimal percentage,
            @Size(max = 5) String vatCategoryCode,
            @DecimalMin("0") BigDecimal vatRate,
            @Size(max = 10) String reasonCode,
            @Size(max = 127) String reason) {}

    /**
     * UBL InvoiceLine. {@code vatCategoryCode} E or O requires
     * both {@code exemptionReasonCode} and {@code exemptionReasonText} (FR-011).
     * Line-level allowances are materialised into
     * {@code zatca_simplified_line_allowances}.
     */
    public record LineItem(
            @NotNull Integer lineNumber,
            @Size(max = 100) String itemCode,
            @NotBlank String description,
            @Size(max = 50) String unitType,
            @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal quantity,
            @DecimalMin("0") BigDecimal unitPrice,
            @DecimalMin("0") BigDecimal itemGrossPrice,
            @DecimalMin("0") BigDecimal itemPriceDiscount,
            @DecimalMin("0") BigDecimal itemPriceBaseQuantity,
            @Size(max = 127) String itemPriceBaseQuantityUnit,
            @NotNull @DecimalMin("0") BigDecimal lineExtensionAmount,
            @NotNull @DecimalMin("0") BigDecimal netAmount,
            @NotBlank @Pattern(regexp = "S|Z|E|O") String vatCategoryCode,
            @NotNull @DecimalMin("0") BigDecimal vatRate,
            @NotNull @DecimalMin("0") BigDecimal vatAmount,
            @Size(max = 10) String exemptionReasonCode,
            String exemptionReasonText,
            @Valid List<@Valid LineAllowance> allowances) {}

    /**
     * Per-line allowance (BG-27). Materialised into
     * {@code zatca_simplified_line_allowances} by the ingestion service.
     */
    public record LineAllowance(
            @NotNull Short sequence,
            @NotNull @DecimalMin("0") BigDecimal amount,
            @DecimalMin("0") BigDecimal baseAmount,
            @DecimalMin("0") BigDecimal percentage,
            @Size(max = 127) String reason) {}
}
