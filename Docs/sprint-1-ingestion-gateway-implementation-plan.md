# Sprint 1 — Global E-Invoicing Gateway: Implementation Plan

**Branch:** `009-zatca-docs-submission`  
**Last Flyway migration:** V57  
**Next migration:** V58 (extends beyond `erp_reference_id` — see Step 2 and §10)
**Created:** 2026-05-20  
**Decisions locked:** 2026-05-25 (see §10)  

---

## 1. Sprint Goal & Scope

Build 4 REST endpoints that let ERP clients push e-invoice documents (already submitted externally) into the platform for archival, status tracking, and reporting. No internal submission to ETA/ZATCA happens in this sprint.

### In Scope
| # | Endpoint | Stored In |
|---|---|---|
| 1 | `POST /api/integration/v1/eta/receipts` | `eta_receipt_headers` + `eta_receipt_lines` |
| 2 | `POST /api/integration/v1/eta/invoices` | `eta_invoice_headers` + `eta_invoice_lines` + `eta_invoice_line_taxes` |
| 3 | `POST /api/integration/v1/zatca/standard` | `zatca_standard_headers` + `zatca_standard_lines` |
| 4 | `POST /api/integration/v1/zatca/simplified` | `zatca_simplified_headers` + `zatca_simplified_lines` |

### Out of Scope (future sprints)
- API key / HMAC authentication on the integration routes
- Idempotency (re-submit with same document number returns existing record)
- Batch ingestion endpoints
- Webhook / callback notifications to ERP
- Dashboard UI for ingested documents

---

## 2. New File Inventory

All new files to create. Modified files are listed separately in Step 6 and Step 17.

```
platform-core/
  src/main/java/com/einvoice/core/error/
    CompanyNotFoundException.java                    (new exception)
    AuthorityEnvironmentNotFoundException.java        (new exception)

platform-api/
  src/main/java/com/einvoice/api/integration/
    dto/
      shared/
        IntegrationEnvironment.java                  (enum)
        IntegrationDocumentStatus.java               (enum)
        DocumentIngestionResponse.java               (response record)
      eta/
        EtaReceiptIngestionRequest.java              (request record + nested)
        EtaInvoiceIngestionRequest.java              (request record + nested)
      zatca/
        ZatcaStandardInvoiceIngestionRequest.java    (request record + nested)
        ZatcaSimplifiedInvoiceIngestionRequest.java  (request record + nested)
    service/
      CompanyResolutionService.java                  (@Service — resolves company+env)
      EtaIngestionService.java                       (@Service — ETA store logic)
      ZatcaIngestionService.java                     (@Service — ZATCA store logic)
    EtaIngestionController.java                      (@RestController)
    ZatcaIngestionController.java                    (@RestController)

platform-core/
  src/main/resources/db/migration/
    V58__integration_erp_reference.sql               (new migration)
```

---

## 3. Modified File Inventory

| File | What Changes |
|---|---|
| `platform-api/pom.xml` | Add SpringDoc OpenAPI dependency |
| `platform-core/.../CompanyRepository.java` | Add `findByTaxNumberAndIsActiveTrue()` |
| `platform-core/.../ZatcaStandardHeaderRepository.java` | Add `existsByCompanyIdAndAuthorityEnvironmentIdAndInvoiceNumber()` |
| `platform-core/.../ZatcaSimplifiedHeaderRepository.java` | Add `existsByCompanyIdAndAuthorityEnvironmentIdAndInvoiceNumber()` |
| `platform-core/.../EtaInvoiceHeader.java` | Add `erpReferenceId` field |
| `platform-core/.../EtaReceiptHeader.java` | Add `erpReferenceId` field |
| `platform-core/.../ZatcaStandardHeader.java` | Add `erpReferenceId` field |
| `platform-core/.../ZatcaSimplifiedHeader.java` | Add `erpReferenceId` field |
| `platform-security/.../SecurityConfig.java` | Add `permitAll()` for `/api/integration/v1/**` |
| `platform-api/.../GlobalExceptionHandler.java` | Add handlers for 2 new exceptions |

---

## 4. Step-by-Step Implementation

### Step 1 — Maven Dependency: SpringDoc OpenAPI

**File:** `platform-api/pom.xml`

Add inside `<dependencies>`:

```xml
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
    <version>2.8.8</version>
</dependency>
```

This enables Swagger UI at `http://localhost:8080/swagger-ui/index.html` and the JSON spec at `/v3/api-docs`.

---

### Step 2 — Flyway V58 Migration

**File:** `platform-core/src/main/resources/db/migration/V58__integration_erp_reference.sql`

```sql
-- V58: Add ERP reference ID column to all four document header tables.
-- Allows ERP clients to store their own document identifier alongside the platform record.
-- Nullable because existing documents created via the internal UI will not have this value.

ALTER TABLE eta_invoice_headers
    ADD COLUMN erp_reference_id VARCHAR(100);

ALTER TABLE eta_receipt_headers
    ADD COLUMN erp_reference_id VARCHAR(100);

ALTER TABLE zatca_standard_headers
    ADD COLUMN erp_reference_id VARCHAR(100);

ALTER TABLE zatca_simplified_headers
    ADD COLUMN erp_reference_id VARCHAR(100);
```

---

### Step 3 — Entity Updates (add erpReferenceId field)

Add to **all four** header entities. Same change in each:

**Files to modify:**
- `platform-core/.../EtaInvoiceHeader.java`
- `platform-core/.../EtaReceiptHeader.java`
- `platform-core/.../ZatcaStandardHeader.java`
- `platform-core/.../ZatcaSimplifiedHeader.java`

**Field to add** (before the `@OneToMany` lines):

```java
@Column(name = "erp_reference_id", length = 100)
private String erpReferenceId;
```

No other changes needed to the entities. The ingestion services set this field; the existing internal write services leave it null.

---

### Step 4 — New Exception Classes (platform-core)

Both follow the exact same pattern as existing exceptions. Package: `com.einvoice.core.error`.

**`CompanyNotFoundException.java`**

```java
package com.einvoice.core.error;

public class CompanyNotFoundException extends RuntimeException {
    public static final String CODE = "COMPANY_NOT_FOUND";
    private final String registrationNumber;

    public CompanyNotFoundException(String registrationNumber) {
        super("Company not found for registration number: " + registrationNumber);
        this.registrationNumber = registrationNumber;
    }

    public String getCode() { return CODE; }
    public String getRegistrationNumber() { return registrationNumber; }
}
```

**`AuthorityEnvironmentNotFoundException.java`**

```java
package com.einvoice.core.error;

public class AuthorityEnvironmentNotFoundException extends RuntimeException {
    public static final String CODE = "AUTHORITY_ENVIRONMENT_NOT_FOUND";
    private final String authority;
    private final String environment;

    public AuthorityEnvironmentNotFoundException(String authority, String environment) {
        super("No active authority environment found for: " + authority + " / " + environment);
        this.authority = authority;
        this.environment = environment;
    }

    public String getCode() { return CODE; }
    public String getAuthority() { return authority; }
    public String getEnvironment() { return environment; }
}
```

---

### Step 5 — Repository Additions

**`CompanyRepository.java`** — add one method:

```java
Optional<Company> findByTaxNumberAndIsActiveTrue(String taxNumber);
```

**`ZatcaStandardHeaderRepository.java`** — add one method:

```java
boolean existsByCompanyIdAndAuthorityEnvironmentIdAndInvoiceNumber(
        UUID companyId, Short authorityEnvironmentId, String invoiceNumber);
```

**`ZatcaSimplifiedHeaderRepository.java`** — add one method:

```java
boolean existsByCompanyIdAndAuthorityEnvironmentIdAndInvoiceNumber(
        UUID companyId, Short authorityEnvironmentId, String invoiceNumber);
```

> **Note:** `EtaInvoiceHeaderRepository` already has `findByCompanyIdAndAuthorityEnvironmentIdAndInvoiceNumber()` which returns `Optional` — use `.isPresent()` to check existence. `EtaReceiptHeaderRepository` has the receipt equivalent. No changes needed to those two.

---

### Step 6 — Shared DTO Enums and Response Record

Package: `com.einvoice.api.integration.dto.shared`

**`IntegrationEnvironment.java`**

```java
package com.einvoice.api.integration.dto.shared;

/**
 * Environments accepted by the ingestion gateway.
 * PRODUCTION is intentionally absent: Jackson deserialization will reject it
 * with an unrecognized value error, returning HTTP 400 automatically.
 */
public enum IntegrationEnvironment {
    SANDBOX,
    PREPROD
}
```

**`IntegrationDocumentStatus.java`**

```java
package com.einvoice.api.integration.dto.shared;

/**
 * Document lifecycle status as reported by the ERP system.
 * The ingestion service maps this to the platform's internal DocumentState.
 */
public enum IntegrationDocumentStatus {
    DRAFT,      // not yet submitted externally
    VALID,      // ETA: invoice/receipt accepted and valid
    INVALID,    // ETA: rejected by ETA for content errors
    CLEARED,    // ZATCA Standard: B2B clearance granted
    REPORTED,   // ZATCA Simplified: B2C reporting acknowledged
    REJECTED,   // authority explicitly rejected
    FAILED,     // technical failure during external submission
    CANCELLED   // document was cancelled
}
```

**`DocumentIngestionResponse.java`**

```java
package com.einvoice.api.integration.dto.shared;

import java.util.UUID;

/**
 * Returned by all four ingestion endpoints on success (HTTP 201).
 */
public record DocumentIngestionResponse(
        UUID id,
        String documentNumber,
        String erpReferenceId,
        String internalStatus,
        String message
) {}
```

---

### Step 7 — ETA Receipt Ingestion DTO

Package: `com.einvoice.api.integration.dto.eta`  
**File:** `EtaReceiptIngestionRequest.java`

This DTO is fully aligned to **ETA Receipt SDK v1.2** (see `Docs/eta-receipt-sdk-v1-2-alignment.md` for the full diff).

```java
package com.einvoice.api.integration.dto.eta;

import com.einvoice.api.integration.dto.shared.IntegrationDocumentStatus;
import com.einvoice.api.integration.dto.shared.IntegrationEnvironment;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record EtaReceiptIngestionRequest(

    // ── gateway routing ───────────────────────────────────────────────────
    @NotBlank @Size(max = 100) String companyRegistrationNumber,
    @NotNull IntegrationEnvironment environment,
    @NotNull IntegrationDocumentStatus status,
    @Size(max = 100) String erpReferenceId,

    // ── sdk root-level fields ─────────────────────────────────────────────
    @NotNull @Valid Header header,
    @NotNull @Valid DocumentType documentType,
    @NotNull @Valid Seller seller,
    @NotNull @Valid Buyer buyer,
    @NotEmpty @Valid List<@Valid ItemData> itemData,

    @NotNull @DecimalMin("0") BigDecimal totalSales,
    @DecimalMin("0") BigDecimal totalCommercialDiscount,
    @DecimalMin("0") BigDecimal totalItemsDiscount,
    List<@Valid Discount> extraReceiptDiscountData,
    @NotNull @DecimalMin("0") BigDecimal netAmount,
    @DecimalMin("0") BigDecimal feesAmount,       // SDK reserved: only 0.0 accepted
    @NotNull @DecimalMin("0") BigDecimal totalAmount,
    List<@Valid TaxTotal> taxTotals,
    @NotBlank @Size(max = 50) String paymentMethod,
    @DecimalMin("0") BigDecimal adjustment,        // SDK reserved: only 0.0 accepted
    @Valid Contractor contractor,
    @Valid Beneficiary beneficiary

) {

    public record Header(
        @NotNull OffsetDateTime dateTimeIssued,
        @NotBlank @Size(max = 50) String receiptNumber,
        @NotBlank @Pattern(regexp = "[a-fA-F0-9]{64}") String uuid,
        @NotNull String previousUUID,              // "" for the first receipt in chain
        String referenceOldUUID,
        @NotBlank @Pattern(regexp = "[A-Z]{3}") String currency,
        @DecimalMin("0") BigDecimal exchangeRate,  // required when currency != EGP
        @Size(max = 200) String sOrderNameCode,
        @Size(max = 30) String orderDeliveryMode,
        @DecimalMin("0") BigDecimal grossWeight,
        @DecimalMin("0") BigDecimal netWeight
    ) {}

    public record DocumentType(
        @NotBlank @Pattern(regexp = "s|r|rr|rrwr|cr|crr|gs|gsr") String receiptType,  // resolved 2026-05-25: keep legacy codes accepted
        @NotBlank @Pattern(regexp = "1\\.2") String typeVersion     // v1.2: only "1.2"
    ) {}

    public record Seller(
        @NotBlank @Size(max = 30)  String rin,
        @NotBlank @Size(max = 200) String companyTradeName,
        @NotBlank @Size(max = 50)  String branchCode,
        @NotBlank @Size(max = 100) String deviceSerialNumber,       // formerly posSerial
        @NotBlank @Size(max = 10)  String activityCode,             // formerly taxpayerActivityCode
        @Size(max = 30) String syndicateLicenseNumber,
        @NotNull @Valid BranchAddress branchAddress
    ) {}

    public record BranchAddress(
        @NotBlank @Size(max = 2)   String country,
        @NotBlank @Size(max = 100) String governate,
        @NotBlank @Size(max = 100) String regionCity,
        @NotBlank @Size(max = 200) String street,
        @NotBlank @Size(max = 100) String buildingNumber,
        @Size(max = 30)  String postalCode,
        @Size(max = 100) String floor,
        @Size(max = 100) String room,
        @Size(max = 500) String landmark,
        @Size(max = 500) String additionalInformation
    ) {}

    // id and name required when type=B;
    // required when type=P and totalAmount >= 150,000 EGP — service-layer check
    public record Buyer(
        @NotBlank @Pattern(regexp = "B|P|F") String type,
        @Size(max = 30)  String id,
        @Size(max = 100) String name,
        @Size(max = 30)  String mobileNumber,
        @Size(max = 30)  String paymentNumber
    ) {}

    public record Discount(
        @NotNull @DecimalMin("0") BigDecimal amount,
        @NotBlank @Size(max = 50) String description,
        @DecimalMin("0") @DecimalMax("100") BigDecimal rate
    ) {}

    public record ItemData(
        @NotBlank @Size(max = 50)  String internalCode,
        @NotBlank String description,
        @NotBlank @Pattern(regexp = "GS1|EGS") String itemType,
        @NotBlank @Size(max = 100) String itemCode,
        @NotBlank @Size(max = 30)  String unitType,
        @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal quantity,
        @NotNull @DecimalMin("0") BigDecimal unitPrice,
        @NotNull @DecimalMin("0") BigDecimal totalSale,
        List<@Valid Discount> commercialDiscountData,
        List<@Valid Discount> itemDiscountData,
        @Valid Discount additionalCommercialDiscount,
        @Valid Discount additionalItemDiscount,
        BigDecimal valueDifference,
        @NotNull BigDecimal netSale,
        @NotNull @DecimalMin("0") BigDecimal total,
        List<@Valid TaxableItem> taxableItems
    ) {}

    public record TaxableItem(
        @NotBlank @Size(max = 30) String taxType,
        @NotNull @DecimalMin("0") BigDecimal amount,
        @NotBlank @Size(max = 50) String subType,
        @DecimalMin("0") @DecimalMax("100") BigDecimal rate
    ) {}

    public record TaxTotal(
        @NotBlank @Size(max = 30) String taxType,
        @NotNull @DecimalMin("0") BigDecimal amount
    ) {}

    public record Contractor(
        @Size(max = 100) String name,
        @DecimalMin("0") BigDecimal amount,
        @DecimalMin("0") BigDecimal rate
    ) {}

    public record Beneficiary(
        @DecimalMin("0") BigDecimal amount,
        @DecimalMin("0") BigDecimal rate
    ) {}
}
```

---

### Step 8 — ETA Invoice Ingestion DTO

Package: `com.einvoice.api.integration.dto.eta`  
**File:** `EtaInvoiceIngestionRequest.java`

Based on the ETA Invoice SDK v1.0 and the existing `EtaInvoiceHeader` + `EtaInvoiceLine` + `EtaInvoiceLineTax` entity structures.

```java
package com.einvoice.api.integration.dto.eta;

import com.einvoice.api.integration.dto.shared.IntegrationDocumentStatus;
import com.einvoice.api.integration.dto.shared.IntegrationEnvironment;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record EtaInvoiceIngestionRequest(

    // ── gateway routing ───────────────────────────────────────────────────
    @NotBlank @Size(max = 100) String companyRegistrationNumber,
    @NotNull IntegrationEnvironment environment,
    @NotNull IntegrationDocumentStatus status,
    @Size(max = 100) String erpReferenceId,

    // ── invoice header ────────────────────────────────────────────────────
    @NotBlank @Size(max = 100) String invoiceNumber,
    @NotBlank @Pattern(regexp = "I|C|D") String documentType,   // Invoice/Credit/Debit
    @NotBlank @Pattern(regexp = "1\\.0") String documentTypeVersion,
    @NotNull OffsetDateTime dateTimeIssued,
    LocalDate serviceDeliveryDate,

    // ── parties ───────────────────────────────────────────────────────────
    @NotNull @Valid TaxpayerParty seller,
    @NotNull @Valid TaxpayerParty buyer,

    // ── references (optional) ─────────────────────────────────────────────
    @Size(max = 50) String taxpayerActivityCode,
    @Size(max = 100) String purchaseOrderReference,
    @Size(max = 100) String salesOrderReference,
    @Size(max = 50) String proformaInvoiceNumber,

    // ── currency ──────────────────────────────────────────────────────────
    @NotBlank @Pattern(regexp = "[A-Z]{3}") String currency,     // EGP is default

    // ── totals (ETA SDK monetary summary) ─────────────────────────────────
    @NotNull @DecimalMin("0") BigDecimal totalSalesAmount,
    @NotNull @DecimalMin("0") BigDecimal totalDiscountAmount,
    @NotNull @DecimalMin("0") BigDecimal extraDiscountAmount,
    @NotNull @DecimalMin("0") BigDecimal totalItemsDiscountAmount,
    @NotNull @DecimalMin("0") BigDecimal netAmount,
    @NotNull @DecimalMin("0") BigDecimal totalAmount,

    // ── ETA-generated reference fields (available after submission) ────────
    @Size(max = 255) String etaUuid,
    @Size(max = 255) String etaLongId,
    @Size(max = 255) String etaSubmissionId,

    // ── reference (credit/debit notes only) ───────────────────────────────
    // Per resolved-2026-05-25 decision: hybrid resolution.
    // Service tries to resolve to a UUID (originalDocumentId FK) when found in DB;
    // always stores the raw number string in the new originalInvoiceNumber column.
    @Size(max = 100) String originalInvoiceNumber,

    // ── lines ─────────────────────────────────────────────────────────────
    @NotEmpty @Valid List<@Valid InvoiceLine> lines

) {

    // type: "B" (Business) or "P" (Natural Person) or "F" (Foreigner)
    // id: tax number for B, national ID or passport for P/F
    public record TaxpayerParty(
        @NotBlank @Pattern(regexp = "B|P|F") String type,
        @NotBlank @Size(max = 100) String id,
        @NotBlank @Size(max = 255) String name,
        @NotNull @Valid PartyAddress address
    ) {}

    public record PartyAddress(
        @NotBlank @Size(max = 2)   String country,
        @NotBlank @Size(max = 100) String governate,
        @NotBlank @Size(max = 100) String regionCity,
        @NotBlank @Size(max = 200) String street,
        @NotBlank @Size(max = 100) String buildingNumber,
        @Size(max = 10)  String postalCode,
        @Size(max = 100) String floor,
        @Size(max = 100) String room,
        @Size(max = 500) String landmark,
        @Size(max = 500) String additionalInformation
    ) {}

    public record InvoiceLine(
        @NotNull @Min(1) Integer lineNumber,
        @NotBlank @Size(max = 100) String internalCode,
        @NotBlank @Pattern(regexp = "GS1|EGS") String itemType,
        @NotBlank @Size(max = 100) String itemCode,
        @NotBlank String description,
        @NotBlank @Size(max = 50) String unitType,
        @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal quantity,
        @NotNull @Valid UnitValue unitValue,         // currency + amount per unit
        @NotNull @DecimalMin("0") BigDecimal salesTotal,
        @DecimalMin("0") @DecimalMax("100") BigDecimal discountRate,
        @NotNull @DecimalMin("0") BigDecimal discountAmount,
        @NotNull @DecimalMin("0") BigDecimal itemsDiscount,
        @NotNull BigDecimal valueDifference,
        @NotNull @DecimalMin("0") BigDecimal totalTaxableFees,
        @NotNull @DecimalMin("0") BigDecimal netTotal,
        @NotNull @DecimalMin("0") BigDecimal taxAmount,
        @NotNull @DecimalMin("0") BigDecimal total,
        @Valid List<@Valid LineTax> taxableItems
    ) {}

    // Mirrors the unit_value JSONB in eta_invoice_lines
    public record UnitValue(
        @NotBlank @Pattern(regexp = "[A-Z]{3}") String currencySold,
        @NotNull @DecimalMin("0") BigDecimal amountEGP,           // always in EGP
        @NotNull @DecimalMin("0") BigDecimal amountSold,          // in currencySold
        @DecimalMin("0") BigDecimal currencyExchangeRate          // required when != EGP
    ) {}

    public record LineTax(
        @NotBlank @Size(max = 30) String taxType,
        @Size(max = 30) String subType,
        @DecimalMin("0") @DecimalMax("100") BigDecimal rate,
        @NotNull @DecimalMin("0") BigDecimal amount
    ) {}
}
```

---

### Step 9 — ZATCA Standard Invoice Ingestion DTO

Package: `com.einvoice.api.integration.dto.zatca`  
**File:** `ZatcaStandardInvoiceIngestionRequest.java`

Based on ZATCA FATOORA specification and `ZatcaStandardHeader` / `ZatcaStandardLine` entity structures.

```java
package com.einvoice.api.integration.dto.zatca;

import com.einvoice.api.integration.dto.shared.IntegrationDocumentStatus;
import com.einvoice.api.integration.dto.shared.IntegrationEnvironment;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record ZatcaStandardInvoiceIngestionRequest(

    // ── gateway routing ───────────────────────────────────────────────────
    @NotBlank @Size(max = 100) String companyRegistrationNumber,
    @NotNull IntegrationEnvironment environment,
    @NotNull IntegrationDocumentStatus status,
    @Size(max = 100) String erpReferenceId,

    // ── invoice identification ─────────────────────────────────────────────
    @NotBlank @Size(max = 100) String invoiceNumber,
    @Pattern(regexp = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
    @Size(max = 255) String zatcaUuid,
    @NotBlank @Pattern(regexp = "388|381|383") String invoiceTypeCode,
    // bit 1 = 1 enforces Standard (B2B); bits 7-10 = 0 (reserved)
    @NotBlank @Pattern(regexp = "1[01]{5}0000") String transactionTypeCode,

    // ── dates ─────────────────────────────────────────────────────────────
    @NotNull LocalDate issueDate,
    @NotNull LocalTime issueTime,
    LocalDate supplyDate,
    LocalDate supplyEndDate,

    // ── parties ───────────────────────────────────────────────────────────
    @NotNull @Valid SellerParty seller,
    @NotNull @Valid BuyerParty buyer,       // required for B2B

    // ── currency ──────────────────────────────────────────────────────────
    @NotBlank @Pattern(regexp = "[A-Z]{3}") String currency,
    @Pattern(regexp = "[A-Z]{3}") String taxCurrency,

    // ── UBL LegalMonetaryTotal ─────────────────────────────────────────────
    // lineExtensionAmount = Σ(qty × unitPrice) before discounts
    @NotNull @DecimalMin("0") BigDecimal lineExtensionAmount,
    // allowanceTotalAmount = header-level total discounts
    @NotNull @DecimalMin("0") BigDecimal allowanceTotalAmount,
    // taxExclusiveAmount = lineExtensionAmount − allowanceTotalAmount (net taxable base)
    @NotNull @DecimalMin("0") BigDecimal taxExclusiveAmount,
    @NotNull @DecimalMin("0") BigDecimal taxAmount,
    // taxInclusiveAmount = taxExclusiveAmount + taxAmount
    @NotNull @DecimalMin("0") BigDecimal taxInclusiveAmount,
    @DecimalMin("0") BigDecimal prepaidAmount,   // advance payments, null → stored as 0
    // payableAmount = taxInclusiveAmount − prepaidAmount
    @NotNull @DecimalMin("0") BigDecimal payableAmount,

    // ── ZATCA chain fields (populated after clearance, all optional) ────────
    Long invoiceCounterValue,
    String previousInvoiceHash,
    String invoiceHash,
    String qrCodeBase64,
    @Size(max = 50) String clearanceStatus,     // CLEARED or REJECTED etc.

    // ── reference (credit/debit notes only) ────────────────────────────────
    @Size(max = 100) String originalInvoiceNumber,

    // ── lines ─────────────────────────────────────────────────────────────
    @NotEmpty @Valid List<@Valid LineItem> lines

) {

    public record SellerParty(
        @NotBlank @Size(max = 255) String registrationName,
        @NotBlank @Pattern(regexp = "3[0-9]{14}") String vatNumber,  // 15-digit Saudi VAT
        @NotBlank @Size(max = 200) String streetName,
        @NotBlank @Size(max = 20)  String buildingNumber,
        @NotBlank @Size(max = 100) String cityName,
        @Size(max = 100) String citySubdivisionName,
        @Size(max = 10)  String postalZone,
        @NotBlank @Pattern(regexp = "[A-Z]{2}") String countryCode,
        @Size(max = 200) String additionalStreetName,
        @Size(max = 30)  String plotIdentification
    ) {}

    public record BuyerParty(
        @NotBlank @Size(max = 255) String registrationName,
        @NotBlank @Pattern(regexp = "3[0-9]{14}") String vatNumber,  // required for B2B
        @NotBlank @Size(max = 200) String streetName,
        @NotBlank @Size(max = 20)  String buildingNumber,
        @NotBlank @Size(max = 100) String cityName,
        @Size(max = 100) String citySubdivisionName,
        @Size(max = 10)  String postalZone,
        @NotBlank @Pattern(regexp = "[A-Z]{2}") String countryCode
    ) {}

    public record LineItem(
        @NotNull @Min(1) Integer lineNumber,
        @Size(max = 100) String itemCode,
        @NotBlank String description,
        @Size(max = 50) String unitType,                              // PCE, KGM, etc.
        @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal quantity,
        @NotNull @DecimalMin("0") BigDecimal unitPrice,
        @NotNull @DecimalMin("0") BigDecimal lineExtensionAmount,
        @DecimalMin("0") BigDecimal discountAmount,
        @DecimalMin("0") BigDecimal allowanceAmount,
        @NotNull @DecimalMin("0") BigDecimal netAmount,
        @NotBlank @Pattern(regexp = "S|Z|E|O") String vatCategoryCode,
        @DecimalMin("0") @DecimalMax("100") BigDecimal vatRate,
        @NotNull @DecimalMin("0") BigDecimal vatAmount,
        @Size(max = 10) String exemptionReasonCode,   // required when E|O — service validates
        String exemptionReasonText                    // required when E|O — service validates
    ) {}
}
```

---

### Step 10 — ZATCA Simplified Invoice Ingestion DTO

Package: `com.einvoice.api.integration.dto.zatca`  
**File:** `ZatcaSimplifiedInvoiceIngestionRequest.java`

Differences from Standard:
- `transactionTypeCode` pattern: `0[01]{5}0000` (bit 1 = 0 = Simplified)
- `buyer` is `@Valid` only (no `@NotNull` — anonymous B2C is valid)
- `BuyerParty` fields have no `@NotBlank` (whole object is optional)
- `reportingStatus` instead of `clearanceStatus`

```java
package com.einvoice.api.integration.dto.zatca;

import com.einvoice.api.integration.dto.shared.IntegrationDocumentStatus;
import com.einvoice.api.integration.dto.shared.IntegrationEnvironment;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record ZatcaSimplifiedInvoiceIngestionRequest(

    @NotBlank @Size(max = 100) String companyRegistrationNumber,
    @NotNull IntegrationEnvironment environment,
    @NotNull IntegrationDocumentStatus status,
    @Size(max = 100) String erpReferenceId,

    @NotBlank @Size(max = 100) String invoiceNumber,
    @Pattern(regexp = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
    @Size(max = 255) String zatcaUuid,
    @NotBlank @Pattern(regexp = "388|381|383") String invoiceTypeCode,
    @NotBlank @Pattern(regexp = "0[01]{5}0000") String transactionTypeCode, // bit1=0 = Simplified

    @NotNull LocalDate issueDate,
    @NotNull LocalTime issueTime,
    LocalDate supplyDate,
    LocalDate supplyEndDate,

    @NotNull @Valid SellerParty seller,
    @Valid BuyerParty buyer,              // ← nullable — anonymous retail allowed

    @NotBlank @Pattern(regexp = "[A-Z]{3}") String currency,
    @Pattern(regexp = "[A-Z]{3}") String taxCurrency,

    @NotNull @DecimalMin("0") BigDecimal lineExtensionAmount,
    @NotNull @DecimalMin("0") BigDecimal allowanceTotalAmount,
    @NotNull @DecimalMin("0") BigDecimal taxExclusiveAmount,
    @NotNull @DecimalMin("0") BigDecimal taxAmount,
    @NotNull @DecimalMin("0") BigDecimal taxInclusiveAmount,
    @DecimalMin("0") BigDecimal prepaidAmount,
    @NotNull @DecimalMin("0") BigDecimal payableAmount,

    Long invoiceCounterValue,
    String previousInvoiceHash,
    String invoiceHash,
    String qrCodeBase64,
    @Size(max = 50) String reportingStatus,  // ← REPORTED / REJECTED (not clearanceStatus)

    @Size(max = 100) String originalInvoiceNumber,

    @NotEmpty @Valid List<@Valid LineItem> lines

) {

    // SellerParty: identical to Standard
    public record SellerParty(
        @NotBlank @Size(max = 255) String registrationName,
        @NotBlank @Pattern(regexp = "3[0-9]{14}") String vatNumber,
        @NotBlank @Size(max = 200) String streetName,
        @NotBlank @Size(max = 20)  String buildingNumber,
        @NotBlank @Size(max = 100) String cityName,
        @Size(max = 100) String citySubdivisionName,
        @Size(max = 10)  String postalZone,
        @NotBlank @Pattern(regexp = "[A-Z]{2}") String countryCode,
        @Size(max = 200) String additionalStreetName,
        @Size(max = 30)  String plotIdentification
    ) {}

    // BuyerParty: no @NotBlank on any field — the entire object is optional
    public record BuyerParty(
        @Size(max = 255) String registrationName,
        @Pattern(regexp = "3[0-9]{14}") String vatNumber,
        @Size(max = 200) String streetName,
        @Size(max = 20)  String buildingNumber,
        @Size(max = 100) String cityName,
        @Size(max = 100) String citySubdivisionName,
        @Size(max = 10)  String postalZone,
        @Pattern(regexp = "[A-Z]{2}") String countryCode
    ) {}

    // LineItem: identical to Standard
    public record LineItem(
        @NotNull @Min(1) Integer lineNumber,
        @Size(max = 100) String itemCode,
        @NotBlank String description,
        @Size(max = 50) String unitType,
        @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal quantity,
        @NotNull @DecimalMin("0") BigDecimal unitPrice,
        @NotNull @DecimalMin("0") BigDecimal lineExtensionAmount,
        @DecimalMin("0") BigDecimal discountAmount,
        @DecimalMin("0") BigDecimal allowanceAmount,
        @NotNull @DecimalMin("0") BigDecimal netAmount,
        @NotBlank @Pattern(regexp = "S|Z|E|O") String vatCategoryCode,
        @DecimalMin("0") @DecimalMax("100") BigDecimal vatRate,
        @NotNull @DecimalMin("0") BigDecimal vatAmount,
        @Size(max = 10) String exemptionReasonCode,
        String exemptionReasonText
    ) {}
}
```

---

### Step 11 — CompanyResolutionService

Package: `com.einvoice.api.integration.service`  
**File:** `CompanyResolutionService.java`

Responsibilities:
1. Resolve `companyRegistrationNumber` → `Company` entity (using `taxNumber`)
2. Resolve `authority` + `environment` → `AuthorityEnvironment.id`
3. Return a simple result record holding both resolved IDs

```java
package com.einvoice.api.integration.service;

import com.einvoice.core.domain.company.Company;
import com.einvoice.core.domain.rbac.AuthorityEnvironment;
import com.einvoice.core.error.AuthorityEnvironmentNotFoundException;
import com.einvoice.core.error.CompanyNotFoundException;
import com.einvoice.core.repository.company.CompanyRepository;
import com.einvoice.core.repository.rbac.AuthorityEnvironmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CompanyResolutionService {

    private final CompanyRepository companyRepository;
    private final AuthorityEnvironmentRepository authorityEnvironmentRepository;

    public CompanyResolutionService(
            CompanyRepository companyRepository,
            AuthorityEnvironmentRepository authorityEnvironmentRepository) {
        this.companyRepository = companyRepository;
        this.authorityEnvironmentRepository = authorityEnvironmentRepository;
    }

    public record ResolvedContext(
            java.util.UUID companyId,
            Short authorityEnvironmentId) {}

    /**
     * @param companyRegistrationNumber maps to companies.tax_number
     * @param authority "ETA" or "ZATCA"
     * @param environment "SANDBOX" or "PREPROD"
     */
    @Transactional(readOnly = true)
    public ResolvedContext resolve(
            String companyRegistrationNumber,
            String authority,
            String environment) {

        Company company = companyRepository
                .findByTaxNumberAndIsActiveTrue(companyRegistrationNumber)
                .orElseThrow(() -> new CompanyNotFoundException(companyRegistrationNumber));

        AuthorityEnvironment env = authorityEnvironmentRepository
                .findByAuthorityAndEnvironmentAndIsActiveTrue(authority, environment)
                .orElseThrow(() -> new AuthorityEnvironmentNotFoundException(authority, environment));

        return new ResolvedContext(company.getId(), env.getId());
    }
}
```

---

### Step 12 — IntegrationDocumentStatus → DocumentState Mapping

This logic is used in both `EtaIngestionService` and `ZatcaIngestionService`. Put it as a private static method in each service (no shared utility class needed — keep it simple).

| IntegrationDocumentStatus | → DocumentState | Rationale |
|---|---|---|
| DRAFT | DRAFT | Not yet submitted |
| VALID | ACCEPTED | ETA validated and accepted |
| INVALID | REJECTED | ETA rejected for content errors |
| CLEARED | ACCEPTED | ZATCA Standard: clearance granted |
| REPORTED | ACCEPTED | ZATCA Simplified: reporting acknowledged |
| REJECTED | REJECTED | Authority explicitly rejected |
| FAILED | REJECTED | Technical failure during submission |
| CANCELLED | CANCELLED | Document was voided |

```java
// Private helper in each ingestion service:
private static DocumentState toDocumentState(IntegrationDocumentStatus s) {
    return switch (s) {
        case DRAFT     -> DocumentState.DRAFT;
        case VALID,
             CLEARED,
             REPORTED  -> DocumentState.ACCEPTED;
        case INVALID,
             REJECTED,
             FAILED    -> DocumentState.REJECTED;
        case CANCELLED -> DocumentState.CANCELLED;
    };
}
```

---

### Step 13 — EtaIngestionService

Package: `com.einvoice.api.integration.service`  
**File:** `EtaIngestionService.java`

Key responsibilities:
- Call `CompanyResolutionService.resolve()` to get `(companyId, authorityEnvironmentId)`
- Check for duplicate document number (throws existing exceptions)
- Map DTO fields to existing entity fields
- Convert structured `SellerParty`/`BuyerParty`/`TaxpayerParty` records to `Map<String, Object>` for JSONB columns
- Persist via existing repositories
- Return `DocumentIngestionResponse`

**Receipt ingestion logic — entity mapping:**

| DTO field | Entity field | Notes |
|---|---|---|
| `header.receiptNumber` | `receiptNumber` | Dedup check first |
| `header.dateTimeIssued` | `issueDatetime` | |
| `documentType.receiptType` + `typeVersion` | `documentType` (enum), `documentTypeVersion` | Parse "s" → `EtaReceiptDocumentType.S` |
| `seller` (record) | `sellerData` (JSONB) | Convert via `toSellerMap()` |
| `buyer` (record) | `buyerData` (JSONB) | Convert via `toBuyerMap()` |
| `seller.deviceSerialNumber` | `posSerial` | Also stored in sellerData |
| `paymentMethod` | `paymentMethod` | |
| `header.currency` | `currency` | |
| `totalSales` | `totalSalesAmount` | |
| `totalCommercialDiscount` | `totalDiscountAmount` | |
| `extraReceiptDiscountData` sum | `extraDiscountAmount` | Sum of discount amounts |
| `totalItemsDiscount` | `totalItemsDiscountAmount` | |
| `netAmount` | `netAmount` | |
| `totalAmount` | `totalAmount` | |
| `status` (mapped) | `state` | Via `toDocumentState()` |
| `erpReferenceId` | `erpReferenceId` | New field (V58) |
| `header.uuid` | `etaReceiptUuid` | ETA receipt UUID |

**Invoice ingestion logic — entity mapping:**

| DTO field | Entity field | Notes |
|---|---|---|
| `invoiceNumber` | `invoiceNumber` | Dedup check first |
| `documentType` | `documentType` (enum) | "I" → `EtaInvoiceDocumentType.I` |
| `documentTypeVersion` | `documentTypeVersion` | |
| `dateTimeIssued` | `issueDatetime` | |
| `serviceDeliveryDate` | `serviceDeliveryDate` | |
| `seller` (record) | `sellerData` (JSONB) | `toTaxpayerMap()` |
| `buyer` (record) | `buyerData` (JSONB) | `toTaxpayerMap()` |
| `taxpayerActivityCode` | `taxpayerActivityCode` | |
| `purchaseOrderReference` | `purchaseOrderReference` | |
| `salesOrderReference` | `salesOrderReference` | |
| `proformaInvoiceNumber` | `proformaInvoiceNumber` | |
| `currency` | `currency` | |
| `totalSalesAmount` | `totalSalesAmount` | |
| `totalDiscountAmount` | `totalDiscountAmount` | |
| `extraDiscountAmount` | `extraDiscountAmount` | |
| `totalItemsDiscountAmount` | `totalItemsDiscountAmount` | |
| `netAmount` | `netAmount` | |
| `totalAmount` | `totalAmount` | |
| `etaUuid` | `etaUuid` | |
| `etaLongId` | `etaLongId` | |
| `etaSubmissionId` | `etaSubmissionId` | |
| `originalInvoiceNumber` | `originalDocumentId` | Resolve to UUID via `findByCompanyIdAndAuthorityEnvironmentIdAndInvoiceNumber()` |
| `status` (mapped) | `state` | Via `toDocumentState()` |
| `erpReferenceId` | `erpReferenceId` | New field (V58) |

**JSONB conversion helpers** (private methods in service):

```java
// For ETA Invoice buyer/seller party → JSONB map
private Map<String, Object> toTaxpayerMap(EtaInvoiceIngestionRequest.TaxpayerParty party) {
    Map<String, Object> address = new java.util.LinkedHashMap<>();
    address.put("country", party.address().country());
    address.put("governate", party.address().governate());
    address.put("regionCity", party.address().regionCity());
    address.put("street", party.address().street());
    address.put("buildingNumber", party.address().buildingNumber());
    address.put("postalCode", party.address().postalCode());
    address.put("floor", party.address().floor());
    address.put("room", party.address().room());
    address.put("landmark", party.address().landmark());
    address.put("additionalInformation", party.address().additionalInformation());

    Map<String, Object> map = new java.util.LinkedHashMap<>();
    map.put("type", party.type());
    map.put("id", party.id());
    map.put("name", party.name());
    map.put("address", address);
    return map;
}

// For ETA Receipt UnitValue record → JSONB map  
private Map<String, Object> toUnitValueMap(EtaInvoiceIngestionRequest.UnitValue uv) {
    Map<String, Object> map = new java.util.LinkedHashMap<>();
    map.put("currencySold", uv.currencySold());
    map.put("amountEGP", uv.amountEGP());
    map.put("amountSold", uv.amountSold());
    if (uv.currencyExchangeRate() != null) {
        map.put("currencyExchangeRate", uv.currencyExchangeRate());
    }
    return map;
}
```

**Service method signatures:**

```java
@Service
@Transactional
public class EtaIngestionService {

    // constructor injection: CompanyResolutionService, EtaReceiptHeaderRepository,
    // EtaInvoiceHeaderRepository

    public DocumentIngestionResponse ingestReceipt(EtaReceiptIngestionRequest request);
    public DocumentIngestionResponse ingestInvoice(EtaInvoiceIngestionRequest request);

    private static DocumentState toDocumentState(IntegrationDocumentStatus s) { ... }
    private Map<String, Object> toReceiptSellerMap(EtaReceiptIngestionRequest.Seller s) { ... }
    private Map<String, Object> toReceiptBuyerMap(EtaReceiptIngestionRequest.Buyer b) { ... }
    private Map<String, Object> toTaxpayerMap(EtaInvoiceIngestionRequest.TaxpayerParty p) { ... }
    private Map<String, Object> toUnitValueMap(EtaInvoiceIngestionRequest.UnitValue uv) { ... }
}
```

---

### Step 14 — ZatcaIngestionService

Package: `com.einvoice.api.integration.service`  
**File:** `ZatcaIngestionService.java`

**Standard invoice — entity mapping:**

| DTO field | Entity field | Notes |
|---|---|---|
| `invoiceNumber` | `invoiceNumber` | Dedup with `existsByCompanyIdAndAuthorityEnvironmentIdAndInvoiceNumber()` |
| `zatcaUuid` | `zatcaUuid` | |
| `invoiceTypeCode` | `invoiceTypeCode` | |
| `transactionTypeCode` | `transactionTypeCode` | |
| `issueDate` | `issueDate` | |
| `issueTime` | `issueTime` | |
| `supplyDate` | `supplyDate` | |
| `supplyEndDate` | `supplyEndDate` | |
| `seller` (record) | `sellerData` (JSONB) | `toSellerMap()` |
| `buyer` (record) | `buyerData` (JSONB) | `toBuyerMap()` |
| `currency` | `currency` | |
| `taxCurrency` | `taxCurrency` | null → "SAR" |
| `lineExtensionAmount` | `lineExtensionAmount` | |
| `allowanceTotalAmount` | `allowanceTotalAmount` | |
| `taxExclusiveAmount` | `taxExclusiveAmount` | |
| `taxAmount` | `taxAmount` | |
| `taxInclusiveAmount` | `taxInclusiveAmount` | |
| `prepaidAmount` | `prepaidAmount` | null → ZERO |
| `payableAmount` | `payableAmount` | |
| `invoiceCounterValue` | `invoiceCounterValue` | |
| `previousInvoiceHash` | `previousInvoiceHash` | |
| `invoiceHash` | `invoiceHash` | |
| `qrCodeBase64` | `qrCodeBase64` | |
| `clearanceStatus` | `clearanceStatus` | Standard only |
| `originalInvoiceNumber` | `originalInvoiceId` | Resolve UUID via `findById()`... wait, need number→UUID lookup — see note below |
| `status` (mapped) | `status` | Via `toDocumentState()` |
| `erpReferenceId` | `erpReferenceId` | New field (V58) |

> **Note on `originalInvoiceNumber` → `originalInvoiceId`:** The `ZatcaStandardHeaderRepository` currently has no method to find by invoice number. Add: `Optional<ZatcaStandardHeader> findByCompanyIdAndAuthorityEnvironmentIdAndInvoiceNumber(UUID, Short, String)`. If not found, throw `MissingOriginalDocumentException` (already exists in core).

**Simplified invoice** — identical mapping except:
- `buyerData` is nullable (no `@NotNull` on buyer DTO field)
- `clearanceStatus` → `reportingStatus`
- Dedup uses `ZatcaSimplifiedHeaderRepository`
- `originalInvoiceNumber` resolves using `ZatcaSimplifiedHeaderRepository`

**JSONB conversion** (ZATCA seller/buyer → `Map<String, Object>`):

```java
private Map<String, Object> toSellerMap(ZatcaStandardInvoiceIngestionRequest.SellerParty s) {
    Map<String, Object> map = new java.util.LinkedHashMap<>();
    map.put("registrationName", s.registrationName());
    map.put("vatNumber", s.vatNumber());
    map.put("streetName", s.streetName());
    map.put("buildingNumber", s.buildingNumber());
    map.put("cityName", s.cityName());
    map.put("citySubdivisionName", s.citySubdivisionName());
    map.put("postalZone", s.postalZone());
    map.put("countryCode", s.countryCode());
    map.put("additionalStreetName", s.additionalStreetName());
    map.put("plotIdentification", s.plotIdentification());
    return map;
}
```

**Service method signatures:**

```java
@Service
@Transactional
public class ZatcaIngestionService {

    // constructor injection: CompanyResolutionService,
    // ZatcaStandardHeaderRepository, ZatcaSimplifiedHeaderRepository

    public DocumentIngestionResponse ingestStandard(ZatcaStandardInvoiceIngestionRequest request);
    public DocumentIngestionResponse ingestSimplified(ZatcaSimplifiedInvoiceIngestionRequest request);

    private static DocumentState toDocumentState(IntegrationDocumentStatus s) { ... }
    private Map<String, Object> toSellerMap(ZatcaStandardInvoiceIngestionRequest.SellerParty s) { ... }
    private Map<String, Object> toBuyerMap(ZatcaStandardInvoiceIngestionRequest.BuyerParty b) { ... }
    // overloaded for simplified buyer:
    private Map<String, Object> toSimplifiedBuyerMap(ZatcaSimplifiedInvoiceIngestionRequest.BuyerParty b) { ... }
}
```

**Additional repository method needed in `ZatcaStandardHeaderRepository`:**

```java
Optional<ZatcaStandardHeader> findByCompanyIdAndAuthorityEnvironmentIdAndInvoiceNumber(
        UUID companyId, Short authorityEnvironmentId, String invoiceNumber);
```

And the same in `ZatcaSimplifiedHeaderRepository`:

```java
Optional<ZatcaSimplifiedHeader> findByCompanyIdAndAuthorityEnvironmentIdAndInvoiceNumber(
        UUID companyId, Short authorityEnvironmentId, String invoiceNumber);
```

---

### Step 15 — EtaIngestionController

Package: `com.einvoice.api.integration`  
**File:** `EtaIngestionController.java`

```java
package com.einvoice.api.integration;

import com.einvoice.api.integration.dto.eta.EtaInvoiceIngestionRequest;
import com.einvoice.api.integration.dto.eta.EtaReceiptIngestionRequest;
import com.einvoice.api.integration.dto.shared.DocumentIngestionResponse;
import com.einvoice.api.integration.service.EtaIngestionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/integration/v1/eta")
public class EtaIngestionController {

    private final EtaIngestionService service;

    public EtaIngestionController(EtaIngestionService service) {
        this.service = service;
    }

    @PostMapping("/receipts")
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentIngestionResponse ingestReceipt(
            @RequestBody @Valid EtaReceiptIngestionRequest request) {
        return service.ingestReceipt(request);
    }

    @PostMapping("/invoices")
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentIngestionResponse ingestInvoice(
            @RequestBody @Valid EtaInvoiceIngestionRequest request) {
        return service.ingestInvoice(request);
    }
}
```

---

### Step 16 — ZatcaIngestionController

Package: `com.einvoice.api.integration`  
**File:** `ZatcaIngestionController.java`

```java
package com.einvoice.api.integration;

import com.einvoice.api.integration.dto.shared.DocumentIngestionResponse;
import com.einvoice.api.integration.dto.zatca.ZatcaSimplifiedInvoiceIngestionRequest;
import com.einvoice.api.integration.dto.zatca.ZatcaStandardInvoiceIngestionRequest;
import com.einvoice.api.integration.service.ZatcaIngestionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/integration/v1/zatca")
public class ZatcaIngestionController {

    private final ZatcaIngestionService service;

    public ZatcaIngestionController(ZatcaIngestionService service) {
        this.service = service;
    }

    @PostMapping("/standard")
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentIngestionResponse ingestStandard(
            @RequestBody @Valid ZatcaStandardInvoiceIngestionRequest request) {
        return service.ingestStandard(request);
    }

    @PostMapping("/simplified")
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentIngestionResponse ingestSimplified(
            @RequestBody @Valid ZatcaSimplifiedInvoiceIngestionRequest request) {
        return service.ingestSimplified(request);
    }
}
```

---

### Step 17 — GlobalExceptionHandler: Two New Handlers

**File:** `platform-api/.../GlobalExceptionHandler.java`

Add these two handlers after the existing `handleCustomerNotFound` handler:

```java
@ExceptionHandler(CompanyNotFoundException.class)
public ResponseEntity<ErrorResponse> handleCompanyNotFound(CompanyNotFoundException ex) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse(
            ex.getCode(), ex.getMessage(),
            Map.of("registrationNumber", ex.getRegistrationNumber())));
}

@ExceptionHandler(AuthorityEnvironmentNotFoundException.class)
public ResponseEntity<ErrorResponse> handleAuthorityEnvironmentNotFound(
        AuthorityEnvironmentNotFoundException ex) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse(
            ex.getCode(), ex.getMessage(),
            Map.of("authority", ex.getAuthority(), "environment", ex.getEnvironment())));
}
```

Also add the imports:
```java
import com.einvoice.core.error.CompanyNotFoundException;
import com.einvoice.core.error.AuthorityEnvironmentNotFoundException;
```

---

### Step 18 — SecurityConfig: Permit-All for Integration Routes

**File:** `platform-security/.../SecurityConfig.java`

Add one line to the `authorizeHttpRequests` block, before the `anyRequest().authenticated()` catch-all:

```java
// current order:
.requestMatchers("/api/health/**").permitAll()
// ADD THIS:
.requestMatchers("/api/integration/v1/**").permitAll()
// existing catch-all:
.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
.anyRequest().authenticated()
```

> Sprint 1 has no auth on the ingestion endpoints. Sprint 2 will replace `permitAll()` with API key validation.

---

### Step 19 — OpenAPI Configuration (optional for Sprint 1)

If Swagger grouping is desired, add a config bean in `platform-api/src/main/java/com/einvoice/api/config/`:

**File:** `OpenApiConfig.java`

```java
package com.einvoice.api.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI platformOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("E-Invoice Platform API")
                        .version("1.0.0")
                        .description("Internal platform + ERP integration gateway"));
    }

    @Bean
    public GroupedOpenApi integrationGroup() {
        return GroupedOpenApi.builder()
                .group("integration-gateway")
                .pathsToMatch("/api/integration/**")
                .build();
    }
}
```

---

## 5. Service-Layer Cross-Field Validations

These cannot be expressed with field-level annotations. The services must throw the appropriate existing exceptions.

| Rule | Where | Exception to throw |
|---|---|---|
| ZATCA line `vatCategoryCode` is E or O but `exemptionReasonCode`/`exemptionReasonText` is null | `ZatcaIngestionService` line loop | `VatExemptionReasonRequiredException` (already exists) |
| ETA Receipt `buyer.type` is B and `buyer.id`/`buyer.name` is null | `EtaIngestionService.ingestReceipt()` | Custom validation or `MethodArgumentNotValidException` equivalent — throw `IllegalArgumentException` mapped by generic handler |
| ETA Invoice `documentType` is C or D but `originalInvoiceNumber` is null | `EtaIngestionService.ingestInvoice()` | `MissingOriginalDocumentException` (already exists) |
| ZATCA Standard/Simplified `invoiceTypeCode` 381/383 but `originalInvoiceNumber` is null | `ZatcaIngestionService` | `MissingOriginalDocumentException` (already exists) |

---

## 6. Deduplication Rules

Each service checks for duplicate document numbers before saving. Existing exception classes and their `GlobalExceptionHandler` entries already handle these — no new error handling needed.

| Check | Method | Exception thrown |
|---|---|---|
| ETA Receipt | `EtaReceiptHeaderRepository.findByCompanyIdAndAuthorityEnvironmentIdAndReceiptNumber().isPresent()` | `DuplicateReceiptNumberException` |
| ETA Invoice | `EtaInvoiceHeaderRepository.findByCompanyIdAndAuthorityEnvironmentIdAndInvoiceNumber().isPresent()` | `DuplicateInvoiceNumberException` |
| ZATCA Standard | `ZatcaStandardHeaderRepository.existsByCompanyIdAndAuthorityEnvironmentIdAndInvoiceNumber()` | `DuplicateStandardNumberException` |
| ZATCA Simplified | `ZatcaSimplifiedHeaderRepository.existsByCompanyIdAndAuthorityEnvironmentIdAndInvoiceNumber()` | `DuplicateSimplifiedNumberException` |

---

## 7. Transaction Boundary

Both ingestion services use `@Transactional`. The full save (header + lines) must be atomic. Spring's default propagation (`REQUIRED`) is sufficient — if the line-save fails, the header insert is rolled back automatically.

---

## 8. Test Cases Checklist

Minimum test coverage for Sprint 1 completion.

### Happy-Path Tests (integration tests with Testcontainers — already in the project)

- [ ] POST ETA Receipt with valid PREPROD payload → 201, returns `id` and `receiptNumber`
- [ ] POST ETA Invoice with valid PREPROD payload → 201, returns `id` and `invoiceNumber`
- [ ] POST ZATCA Standard with valid PREPROD payload → 201
- [ ] POST ZATCA Simplified with anonymous buyer (null buyer) → 201
- [ ] ETA Receipt with `status: CLEARED` maps to `state: ACCEPTED` in DB
- [ ] ETA Invoice credit note (`documentType: C`) with valid `originalInvoiceNumber` → 201

### Error-Path Tests

- [ ] Unknown `companyRegistrationNumber` → 404 with `COMPANY_NOT_FOUND`
- [ ] Inactive company → 404 with `COMPANY_NOT_FOUND`
- [ ] `environment: PRODUCTION` in JSON → 400 (Jackson deserialization error)
- [ ] Missing required field (`invoiceNumber`) → 400 with `VALIDATION_ERROR` + field error list
- [ ] `transactionTypeCode` wrong pattern for Standard (starts with 0) → 400
- [ ] ZATCA Standard line with `vatCategoryCode: E` and no exemption reason → appropriate 400
- [ ] Duplicate receipt number → 409 with `DUPLICATE_RECEIPT_NUMBER`
- [ ] ETA Invoice credit note with missing `originalInvoiceNumber` → 400 `MISSING_ORIGINAL_DOCUMENT`
- [ ] Valid ZATCA Standard with `buyer: null` → 400 (buyer is `@NotNull` for Standard)

---

## 9. Implementation Order (Suggested Sequence)

Follow this order to avoid compilation errors:

```
1. V58 migration (SQL only — no compilation needed)
2. Exception classes (CompanyNotFoundException, AuthorityEnvironmentNotFoundException)
3. Entity field additions (erpReferenceId on all 4 entities)
4. Repository method additions (5 methods across 4 repositories)
5. Shared enums (IntegrationEnvironment, IntegrationDocumentStatus, DocumentIngestionResponse)
6. Four DTO request records (no dependencies on each other)
7. CompanyResolutionService
8. EtaIngestionService
9. ZatcaIngestionService
10. EtaIngestionController
11. ZatcaIngestionController
12. GlobalExceptionHandler additions
13. SecurityConfig change
14. pom.xml SpringDoc dependency
15. OpenApiConfig (optional)
16. Run mvn verify — fix any compilation errors
17. Run integration tests
```

---

## 10. Resolved Decisions (2026-05-25)

All open questions from the original plan have been resolved. Implementation proceeds on these locked assumptions.

| # | Question | Resolution |
|---|---|---|
| Q1 | `companyRegistrationNumber` field for ZATCA | **`tax_number` for both ETA and ZATCA.** ZATCA's 15-digit VAT number is stored in `companies.tax_number`. Single lookup, no CR-number branching. |
| Q2 | Idempotent re-submit or 409? | **HTTP 409 Conflict** via existing `Duplicate*Exception` classes. Idempotent retry is a future-sprint concern. |
| Q3 | `receiptType` pattern strictness | **Keep all legacy codes** accepted (`s|r|rr|rrwr|cr|crr|gs|gsr`). DTO `@Pattern` updated in Step 7. |
| Q4 | `feesAmount` / `adjustment` strict 0.0? | **Allow any non-negative** (`@DecimalMin("0")` only). No `@DecimalMax`. |
| Q5 | `originalInvoiceNumber` resolution behavior | **Hybrid: resolve to UUID if found, otherwise store as string.** Requires a new `original_invoice_number VARCHAR(100)` column on all four header tables. Service populates the string always; populates the `original_*_id` FK only when the referenced document exists locally. |

### Additional decisions affecting Sprint 1

- **`header.uuid` generation (ETA Receipt)** — ERP generates the SHA-256; the gateway only validates the format (`[a-fA-F0-9]{64}`). No canonicalisation logic in the platform.
- **Anonymous buyer (ETA Receipt)** — DTO requires the `buyer` object with `type`, but `id`/`name` are nullable. Service validates conditional requirements (B type, P type ≥150k EGP).

### Migration scope expansion

The original Step 2 V58 added only `erp_reference_id`. The resolved decisions expand V58 (or split into V58–V61 — see `Docs/zatca-spec-alignment.md` §11 and `Docs/eta-receipt-sdk-v1-2-alignment.md` §11) to additionally:

1. Add `original_invoice_number VARCHAR(100)` to all four header tables.
2. Rename `eta_receipt_headers.total_discount_amount` → `total_commercial_discount`.
3. Add scalar `unit_price NUMERIC(18,5)` to `eta_receipt_lines`, backfill from `unit_value`, drop `unit_value` JSONB.
4. Add `exchange_rate NUMERIC(18,5)` to `eta_receipt_headers`.
5. Apply the full ZATCA structural changes catalogued in `Docs/zatca-spec-alignment.md` (V58–V61).

**Sprint 1 should not begin until the alignment migrations land.** Implementation order in §9 is revised to reference `specs/010-authority-spec-alignment/` as a prerequisite.

---

## 11. Estimated File Count Summary

| Category | New Files | Modified Files |
|---|---|---|
| Flyway migration | 1 | — |
| Exceptions | 2 | — |
| Enums + Response DTO | 3 | — |
| Request DTOs (with nested) | 4 | — |
| Services | 3 | — |
| Controllers | 2 | — |
| Config | 1 | — |
| Repository additions | — | 4 |
| Entity additions | — | 4 |
| Security + Error handler | — | 2 |
| pom.xml | — | 1 |
| **Total** | **16** | **11** |

---

*End of Sprint 1 Implementation Plan*
