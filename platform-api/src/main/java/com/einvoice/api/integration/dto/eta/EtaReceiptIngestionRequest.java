package com.einvoice.api.integration.dto.eta;

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
import java.util.List;

public record EtaReceiptIngestionRequest(
        @NotBlank @Size(max = 100) String companyRegistrationNumber,
        @NotNull IntegrationEnvironment environment,
        @NotNull IntegrationDocumentStatus status,
        @Size(max = 100) String erpReferenceId,
        @NotNull @Valid Header header,
        @NotNull @Valid DocumentType documentType,
        @NotNull @Valid Seller seller,
        @Valid Buyer buyer,
        @NotBlank String paymentMethod,
        @NotNull BigDecimal totalSales,
        BigDecimal totalCommercialDiscount,
        List<@Valid Discount> extraReceiptDiscountData,
        BigDecimal totalItemsDiscount,
        @NotNull BigDecimal netAmount,
        @NotNull BigDecimal totalAmount,
        List<@Valid TaxTotal> taxTotals,
        @Valid Contractor contractor,
        @Valid Beneficiary beneficiary,
        @NotEmpty List<@Valid ItemData> itemData) {

    public record Header(
            @NotBlank String dateTimeIssued,
            @NotBlank String receiptNumber,
            @NotBlank @Pattern(regexp = "[a-fA-F0-9]{64}", message = "must be a 64-character hex string") String uuid,
            String previousUUID,
            String currency,
            BigDecimal exchangeRate,
            String referenceOldUUID,
            String sOrderNameCode,
            String orderDeliveryMode,
            BigDecimal grossWeight,
            BigDecimal netWeight) {}

    public record DocumentType(
            @NotBlank @Pattern(regexp = "r|rr|rrwr|cr|crr|gs|gsr") String receiptType,
            @NotBlank @Pattern(regexp = "1\\.2") String typeVersion) {}

    public record Seller(
            @NotBlank String rin,
            @NotBlank String tradeName,
            String branchCode,
            @NotBlank String deviceSerialNumber,
            @NotBlank String activityCode,
            String syndicateLicenseNumber,
            @Valid BranchAddress branchAddress) {}

    public record BranchAddress(
            String country,
            String governate,
            String regionCity,
            String street,
            String building,
            String postalCode,
            String floor,
            String room,
            String landmark) {}

    public record Buyer(
            @NotBlank String type,
            String id,
            String name) {}

    public record Discount(
            @NotNull BigDecimal amount,
            String description) {}

    public record ItemData(
            String internalCode,
            @NotBlank String itemCode,
            @NotBlank @Pattern(regexp = "GS1|EGS") String itemType,
            @NotBlank String description,
            @NotBlank String unitType,
            @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal quantity,
            @NotNull BigDecimal unitPrice,
            BigDecimal salesTotal,
            List<@Valid Discount> commercialDiscountData,
            List<@Valid Discount> itemDiscountData,
            BigDecimal valueDifference,
            BigDecimal totalTaxableFees,
            BigDecimal netTotal,
            BigDecimal taxAmount,
            BigDecimal total,
            List<@Valid TaxableItem> taxableItems) {}

    public record TaxableItem(
            @NotBlank String taxType,
            @NotNull BigDecimal amount,
            String subType,
            BigDecimal rate) {}

    public record TaxTotal(
            @NotBlank String taxType,
            @NotNull BigDecimal amount) {}

    public record Contractor(
            String name,
            BigDecimal amount,
            BigDecimal rate) {}

    public record Beneficiary(
            BigDecimal amount,
            BigDecimal rate) {}
}
