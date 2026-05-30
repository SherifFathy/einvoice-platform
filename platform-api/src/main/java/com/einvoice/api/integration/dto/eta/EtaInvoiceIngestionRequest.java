package com.einvoice.api.integration.dto.eta;

import com.einvoice.api.integration.dto.shared.IntegrationDocumentStatus;
import com.einvoice.api.integration.dto.shared.IntegrationEnvironment;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record EtaInvoiceIngestionRequest(
        @NotBlank @Size(max = 100) String companyRegistrationNumber,
        @NotNull IntegrationEnvironment environment,
        @NotNull IntegrationDocumentStatus status,
        @Size(max = 100) String erpReferenceId,
        @NotBlank @Size(max = 100) String invoiceNumber,
        @NotBlank @Pattern(regexp = "I|C|D") String documentType,
        @NotBlank @Pattern(regexp = "1\\.0") String documentTypeVersion,
        @NotNull OffsetDateTime dateTimeIssued,
        LocalDate serviceDeliveryDate,
        @NotNull @Valid TaxpayerParty seller,
        @NotNull @Valid TaxpayerParty buyer,
        @Size(max = 50) String taxpayerActivityCode,
        @Size(max = 100) String purchaseOrderReference,
        @Size(max = 100) String salesOrderReference,
        @Size(max = 50) String proformaInvoiceNumber,
        @NotBlank @Pattern(regexp = "[A-Z]{3}") String currency,
        @NotNull @DecimalMin("0") BigDecimal totalSalesAmount,
        @NotNull @DecimalMin("0") BigDecimal totalDiscountAmount,
        @NotNull @DecimalMin("0") BigDecimal extraDiscountAmount,
        @NotNull @DecimalMin("0") BigDecimal totalItemsDiscountAmount,
        @NotNull @DecimalMin("0") BigDecimal netAmount,
        @NotNull @DecimalMin("0") BigDecimal totalAmount,
        @Size(max = 255) String etaUuid,
        @Size(max = 255) String etaLongId,
        @Size(max = 255) String etaSubmissionId,
        @Size(max = 100) String originalInvoiceNumber,
        @NotEmpty @Valid List<@Valid InvoiceLine> lines) {

    public record TaxpayerParty(
            @NotBlank @Pattern(regexp = "B|P|F") String type,
            @NotBlank @Size(max = 100) String id,
            @NotBlank @Size(max = 255) String name,
            @NotNull @Valid PartyAddress address) {}

    public record PartyAddress(
            @NotBlank @Size(max = 2) String country,
            @NotBlank @Size(max = 100) String governate,
            @NotBlank @Size(max = 100) String regionCity,
            @NotBlank @Size(max = 200) String street,
            @NotBlank @Size(max = 100) String buildingNumber,
            @Size(max = 10) String postalCode,
            @Size(max = 100) String floor,
            @Size(max = 100) String room,
            @Size(max = 500) String landmark,
            @Size(max = 500) String additionalInformation) {}

    public record InvoiceLine(
            @NotNull @Min(1) Integer lineNumber,
            @NotBlank @Size(max = 100) String internalCode,
            @NotBlank @Pattern(regexp = "GS1|EGS") String itemType,
            @NotBlank @Size(max = 100) String itemCode,
            @NotBlank String description,
            @NotBlank @Size(max = 50) String unitType,
            @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal quantity,
            @NotNull @Valid UnitValue unitValue,
            @NotNull @DecimalMin("0") BigDecimal salesTotal,
            @DecimalMin("0") @DecimalMax("100") BigDecimal discountRate,
            @NotNull @DecimalMin("0") BigDecimal discountAmount,
            @NotNull @DecimalMin("0") BigDecimal itemsDiscount,
            @NotNull BigDecimal valueDifference,
            @NotNull @DecimalMin("0") BigDecimal totalTaxableFees,
            @NotNull @DecimalMin("0") BigDecimal netTotal,
            @NotNull @DecimalMin("0") BigDecimal taxAmount,
            @NotNull @DecimalMin("0") BigDecimal total,
            @Valid List<@Valid LineTax> taxableItems) {}

    public record UnitValue(
            @NotBlank @Pattern(regexp = "[A-Z]{3}") String currencySold,
            @NotNull @DecimalMin("0") BigDecimal amountEGP,
            @NotNull @DecimalMin("0") BigDecimal amountSold,
            @DecimalMin("0") BigDecimal currencyExchangeRate) {}

    public record LineTax(
            @NotBlank @Size(max = 30) String taxType,
            @Size(max = 30) String subType,
            @DecimalMin("0") @DecimalMax("100") BigDecimal rate,
            @NotNull @DecimalMin("0") BigDecimal amount) {}
}
