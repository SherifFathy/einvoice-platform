# SC-007 — Sprint 1 Ingestion Gateway DTO Mapping Audit

**Task:** T056
**Date:** 2026-05-27
**Scope:** Confirm every DTO field in `Docs/sprint-1-ingestion-gateway-implementation-plan.md` §10 Steps 7–10 has a deterministic target column or JSONB key after V58–V61, with no `raw_payload` JSONB catch-all required.

**Sources:**
- DTO definitions: `Docs/sprint-1-ingestion-gateway-implementation-plan.md` lines 286–807 (Steps 7–10)
- Post-V61 entity model: `specs/010-authority-spec-alignment/data-model.md` + the four header entities

---

## Step 7 — `EtaReceiptIngestionRequest` → `eta_receipt_headers` + `eta_receipt_lines`

### Root-level

| DTO field | Target column / JSONB key | Type | Notes |
|---|---|---|---|
| `companyRegistrationNumber` | resolved via `CompanyResolutionService` → `company_id` FK | — | Gateway routing, not stored |
| `environment` | resolved → `eta_config_id` FK on header | — | |
| `status` | mapped via `IntegrationDocumentStatus → DocumentState` (Step 12) | enum | |
| `erpReferenceId` | `eta_receipt_headers.erp_reference_id` (V58) | VARCHAR(100) | promoted in V58 |
| `header.dateTimeIssued` | `eta_receipt_headers.issued_at` | TIMESTAMPTZ | existing |
| `header.receiptNumber` | `eta_receipt_headers.receipt_number` | VARCHAR(50) | existing |
| `header.uuid` | `eta_receipt_headers.eta_uuid` | VARCHAR | existing |
| `header.previousUUID` | `eta_receipt_headers.previous_uuid` (V58) | VARCHAR | promoted in V58 |
| `header.referenceOldUUID` | `eta_receipt_headers.reference_old_uuid` (V58) | VARCHAR | promoted in V58 |
| `header.currency` | `eta_receipt_headers.currency` | CHAR(3) | existing |
| `header.exchangeRate` | `eta_receipt_headers.exchange_rate` (V58) | NUMERIC | promoted in V58 |
| `header.sOrderNameCode` | `eta_receipt_headers.s_order_name_code` (V58) | VARCHAR(200) | promoted in V58 |
| `header.orderDeliveryMode` | `eta_receipt_headers.order_delivery_mode` (V58) | VARCHAR(30) | promoted in V58 |
| `header.grossWeight` | `eta_receipt_headers.gross_weight` (V58) | NUMERIC | promoted in V58 |
| `header.netWeight` | `eta_receipt_headers.net_weight` (V58) | NUMERIC | promoted in V58 |
| `documentType.receiptType` | `eta_receipt_headers.receipt_type` | VARCHAR | existing |
| `documentType.typeVersion` | constant `"1.2"` — not stored (asserted, not persisted) | — | service-layer guard |
| `seller.*` | `eta_receipt_headers.seller_data` JSONB | JSONB | existing — ETA Receipt did NOT get party promotion in V58 |
| `buyer.*` | `eta_receipt_headers.buyer_data` JSONB | JSONB | (same) |
| `itemData[*]` | rows in `eta_receipt_lines` | — | see line-level table |
| `totalSales` | `eta_receipt_headers.total_sales_amount` | NUMERIC | existing |
| `totalCommercialDiscount` | `eta_receipt_headers.total_commercial_discount` (V58 rename) | NUMERIC | was `total_discount_amount` pre-V58 |
| `totalItemsDiscount` | `eta_receipt_headers.total_items_discount_amount` | NUMERIC | existing |
| `extraReceiptDiscountData` | `eta_receipt_headers.extra_receipt_discount_data` (V58) | JSONB | promoted in V58 |
| `netAmount` | `eta_receipt_headers.net_amount` | NUMERIC | existing |
| `feesAmount` | `eta_receipt_headers.fees_amount` (V58, DEFAULT 0) | NUMERIC | promoted in V58 |
| `totalAmount` | `eta_receipt_headers.total_amount` | NUMERIC | existing |
| `taxTotals` | `eta_receipt_headers.tax_totals` (V58) | JSONB | promoted in V58 |
| `paymentMethod` | `eta_receipt_headers.payment_method` | VARCHAR | existing |
| `adjustment` | `eta_receipt_headers.adjustment` (V58, DEFAULT 0) | NUMERIC | promoted in V58 |
| `contractor` | `eta_receipt_headers.contractor_data` (V58) | JSONB | promoted in V58 |
| `beneficiary` | `eta_receipt_headers.beneficiary_data` (V58) | JSONB | promoted in V58 |

### Line-level (`itemData[*]` → `eta_receipt_lines`)

| DTO field | Target column / JSONB key | Type | Notes |
|---|---|---|---|
| `internalCode` | `eta_receipt_lines.internal_code` | VARCHAR | existing |
| `description` | `eta_receipt_lines.description` | TEXT | existing |
| `itemType` | `eta_receipt_lines.item_type` | VARCHAR | existing |
| `itemCode` | `eta_receipt_lines.item_code` | VARCHAR | existing |
| `unitType` | `eta_receipt_lines.unit_type` | VARCHAR | existing |
| `quantity` | `eta_receipt_lines.quantity` | NUMERIC | existing |
| `unitPrice` | `eta_receipt_lines.unit_price` (V60) | NUMERIC | scalar replacement of pre-V60 `unit_value` JSONB |
| `totalSale` | `eta_receipt_lines.sales_total` | NUMERIC | existing |
| `commercialDiscountData` | `eta_receipt_lines.commercial_discount_data` (V60, DEFAULT `'[]'`) | JSONB | promoted in V60 |
| `itemDiscountData` | `eta_receipt_lines.item_discount_data` (V60, DEFAULT `'[]'`) | JSONB | promoted in V60 |
| `additionalCommercialDiscount` | append to `commercial_discount_data[]` | JSONB | service-layer merge |
| `additionalItemDiscount` | append to `item_discount_data[]` | JSONB | service-layer merge |
| `valueDifference` | `eta_receipt_lines.value_difference` | NUMERIC | existing |
| `netSale` | `eta_receipt_lines.net_total` | NUMERIC | existing |
| `total` | `eta_receipt_lines.total` | NUMERIC | existing |
| `taxableItems[*]` | rows in `eta_receipt_line_taxes` | — | existing child table |

---

## Step 8 — `EtaInvoiceIngestionRequest` → `eta_invoice_headers` + `eta_invoice_lines`

### Root-level

| DTO field | Target column / JSONB key | Type | Notes |
|---|---|---|---|
| Gateway routing fields | (as per Step 7) | — | |
| `erpReferenceId` | `eta_invoice_headers.erp_reference_id` (V58) | VARCHAR(100) | promoted in V58 |
| `invoiceNumber` | `eta_invoice_headers.invoice_number` | VARCHAR | existing |
| `documentType` | `eta_invoice_headers.document_type` | VARCHAR | existing |
| `documentTypeVersion` | constant `"1.0"` — asserted, not stored | — | service-layer guard |
| `dateTimeIssued` | `eta_invoice_headers.date_time_issued` | TIMESTAMPTZ | existing |
| `serviceDeliveryDate` | `eta_invoice_headers.service_delivery_date` | DATE | existing |
| `seller.*` | `eta_invoice_headers.seller_data` JSONB | JSONB | existing |
| `buyer.*` | `eta_invoice_headers.buyer_data` JSONB | JSONB | existing |
| `taxpayerActivityCode` | `eta_invoice_headers.taxpayer_activity_code` | VARCHAR | existing |
| `purchaseOrderReference` | `eta_invoice_headers.purchase_order_reference` | VARCHAR | existing |
| `salesOrderReference` | `eta_invoice_headers.sales_order_reference` | VARCHAR | existing |
| `proformaInvoiceNumber` | `eta_invoice_headers.proforma_invoice_number` | VARCHAR | existing |
| `currency` | `eta_invoice_headers.currency` | CHAR(3) | existing |
| `totalSalesAmount` | `eta_invoice_headers.total_sales_amount` | NUMERIC | existing |
| `totalDiscountAmount` | `eta_invoice_headers.total_discount_amount` | NUMERIC | existing — NOT renamed in V58 (rename only applied to ETA Receipt) |
| `extraDiscountAmount` | `eta_invoice_headers.extra_discount_amount` | NUMERIC | existing |
| `totalItemsDiscountAmount` | `eta_invoice_headers.total_items_discount_amount` | NUMERIC | existing |
| `netAmount` | `eta_invoice_headers.net_amount` | NUMERIC | existing |
| `totalAmount` | `eta_invoice_headers.total_amount` | NUMERIC | existing |
| `etaUuid` | `eta_invoice_headers.eta_uuid` | VARCHAR | existing |
| `etaLongId` | `eta_invoice_headers.eta_long_id` | VARCHAR | existing |
| `etaSubmissionId` | `eta_invoice_headers.eta_submission_id` | VARCHAR | existing |
| `originalInvoiceNumber` | `eta_invoice_headers.original_invoice_number` (V58) | VARCHAR(100) | promoted in V58 (per Sprint 1 resolved decision) |
| `lines[*]` | rows in `eta_invoice_lines` | — | see line-level table |

### Line-level (`lines[*]` → `eta_invoice_lines`)

| DTO field | Target column / JSONB key | Type | Notes |
|---|---|---|---|
| `lineNumber` | `eta_invoice_lines.line_number` | INT | existing |
| `internalCode` | `eta_invoice_lines.internal_code` | VARCHAR | existing |
| `itemType`, `itemCode`, `description`, `unitType`, `quantity` | direct columns | — | existing |
| `unitValue.*` (currencySold/amountEGP/amountSold/exchangeRate) | `eta_invoice_lines.unit_value` JSONB | JSONB | existing — ETA Invoice lines were NOT restructured in V60 (rename only applied to ETA Receipt) |
| `salesTotal`, `discountRate`, `discountAmount`, `itemsDiscount`, `valueDifference`, `totalTaxableFees`, `netTotal`, `taxAmount`, `total` | direct columns | — | existing |
| `taxableItems[*]` | rows in `eta_invoice_line_taxes` | — | existing |

---

## Step 9 — `ZatcaStandardInvoiceIngestionRequest` → `zatca_standard_headers` + `zatca_standard_lines`

### Root-level

| DTO field | Target column / JSONB key | Type | Notes |
|---|---|---|---|
| Gateway routing fields | (as per Step 7) | — | |
| `erpReferenceId` | `zatca_standard_headers.erp_reference_id` (V58) | VARCHAR(100) | promoted in V58 |
| `invoiceNumber` | `zatca_standard_headers.invoice_number` | VARCHAR | existing |
| `zatcaUuid` | `zatca_standard_headers.zatca_uuid` | VARCHAR(255) | type change to UUID abandoned per §V58.E.1 — stays VARCHAR with regex check at service layer |
| `invoiceTypeCode` | `zatca_standard_headers.invoice_type_code` | VARCHAR | existing |
| `transactionTypeCode` | `zatca_standard_headers.transaction_type_code` | VARCHAR | existing |
| `issueDate`, `issueTime`, `supplyDate`, `supplyEndDate` | direct columns | — | existing |
| `seller.registrationName` | `zatca_standard_headers.seller_data->>'registrationName'` (JSONB) — also reflected in promoted `seller_name` column if added | JSONB | existing JSONB still carries full payload |
| `seller.vatNumber` | `zatca_standard_headers.seller_vat_number` (V58) | CHAR(15) | promoted in V58 (§V58.A.1) |
| `seller.streetName` | `zatca_standard_headers.seller_street_name` (V58) | VARCHAR | promoted in V58 |
| `seller.buildingNumber` | `zatca_standard_headers.seller_building_number` (V58) | VARCHAR(4) | promoted in V58 (§V58.A.3) |
| `seller.cityName` | `zatca_standard_headers.seller_city_name` (V58) | VARCHAR | promoted in V58 |
| `seller.citySubdivisionName` | `zatca_standard_headers.seller_city_subdivision` (V58) | VARCHAR | promoted in V58 |
| `seller.postalZone` | `zatca_standard_headers.seller_postal_code` (V58) | VARCHAR(5) | promoted in V58 (§V58.A.5) |
| `seller.countryCode` | `zatca_standard_headers.seller_country_code` (V58, DEFAULT 'SA') | CHAR(2) | promoted in V58 (§V58.A.6) |
| `seller.additionalStreetName` | `zatca_standard_headers.seller_additional_street` (V58) | VARCHAR | promoted in V58 |
| `seller.plotIdentification` | `zatca_standard_headers.seller_additional_number` (V58) | VARCHAR(4) | promoted in V58 (§V58.A.4) |
| `buyer.*` | mirror of seller promoted columns + `buyer_data` JSONB fallback | promoted columns | V58 |
| `currency`, `taxCurrency` | direct columns | — | existing |
| `lineExtensionAmount` | `zatca_standard_headers.line_extension_amount` | NUMERIC | existing |
| **`allowanceTotalAmount`** | **derived** — `SUM(zatca_standard_allowances.amount)` (V59 child table) | — | **NO column**. Pre-V59 column `allowance_total_amount` DROPPED in V59. Service computes from child rows at write; `ZatcaStandardHeader.allowanceTotal()` provides derived getter. |
| `taxExclusiveAmount`, `taxAmount`, `taxInclusiveAmount`, `prepaidAmount`, `payableAmount` | direct columns | NUMERIC | existing |
| `invoiceCounterValue`, `previousInvoiceHash`, `invoiceHash`, `qrCodeBase64`, `clearanceStatus` | direct columns | — | existing |
| `originalInvoiceNumber` | `zatca_standard_headers.original_invoice_number` (V58) | VARCHAR(100) | promoted in V58 |
| `lines[*]` | rows in `zatca_standard_lines` | — | see line-level table |

### Line-level (`lines[*]` → `zatca_standard_lines`)

| DTO field | Target column / JSONB key | Type | Notes |
|---|---|---|---|
| `lineNumber`, `itemCode`, `description`, `unitType`, `quantity` | direct columns | — | existing |
| `unitPrice` | `zatca_standard_lines.item_net_price` (V60 rename) | NUMERIC | was `unit_price` pre-V60 |
| `lineExtensionAmount`, `netAmount` | direct columns | NUMERIC | existing |
| **`discountAmount`** | child row in `zatca_standard_line_allowances` (V60) with `amount=discountAmount`, `sequence=1` | — | **DROPPED as direct column**; promoted to child table per §V60.A.1 |
| **`allowanceAmount`** | (same — second child row with `sequence=2`, or merged with discount per service-layer policy) | — | promoted to child table |
| `vatCategoryCode`, `vatRate`, `vatAmount`, `exemptionReasonCode`, `exemptionReasonText` | direct columns | — | existing |

---

## Step 10 — `ZatcaSimplifiedInvoiceIngestionRequest` → `zatca_simplified_headers` + `zatca_simplified_lines`

Structurally identical to Step 9 mappings; differences only in field nullability (anonymous buyer permitted) and `reportingStatus` (mapped to `zatca_simplified_headers.reporting_status`) instead of `clearanceStatus`. All other targets are mirrored on the Simplified entity. The V61 signature artifact columns (`cryptographic_stamp_value`, `signed_xml_artifact_id`, `signed_at`, `zatca_config_id`) are **not** ingestion inputs — they are populated by the submission engine after signing.

---

## Gaps

| # | DTO field | Issue | Recommendation |
|---|---|---|---|
| 1 | ZATCA Standard / Simplified root `allowanceTotalAmount` | No 1:1 column post-V59. Pre-V59 column DROPPED; replaced by `zatca_*_allowances` child table. | Sprint 1 service layer MUST decompose the scalar into one or more allowance rows (minimally a single row `(header_id, sequence=1, amount=allowanceTotalAmount, vat_category_code=…, vat_rate=…)`). The DTO has no `vat_category_code` or `vat_rate` for the header-level allowance, so the service-layer mapper MUST derive these — likely from line-level VAT data — or require the ingestion DTO to be extended in a follow-up. **Not blocking SC-007** because the scalar can still be stored deterministically (as one row), but the derivation rule SHOULD be locked before Sprint 1 implementation. |
| 2 | ZATCA Standard / Simplified line `discountAmount` + `allowanceAmount` | No 1:1 column post-V60. Pre-V60 columns DROPPED; replaced by `zatca_*_line_allowances` child table. | Sprint 1 service layer MUST insert one or two child rows per line. Same caveat as #1 re: missing `vat_category_code`/`vat_rate` on the child row — derive from the parent line. **Not blocking SC-007**, but a follow-up SDK note may be warranted. |
| 3 | ETA Invoice line `unitValue` JSONB | ETA Invoice lines were NOT restructured in V60 (V60 only restructured `eta_receipt_lines` — `unit_value` JSONB still exists for `eta_invoice_lines`). | Mapping is deterministic into the legacy JSONB; no change needed. |
| 4 | ETA Invoice header `totalDiscountAmount` | ETA Invoice header was NOT renamed in V58 (V58 rename `total_discount_amount → total_commercial_discount` applied only to `eta_receipt_headers`). | Mapping is deterministic into the legacy column; no change needed. |

---

## Conclusion

**SC-007 acceptance: PASS, with two recommended Sprint 1 follow-ups.**

Every DTO field in Steps 7–10 maps to a deterministic target column or child-table row after V58–V61. No field requires a `raw_payload` JSONB catch-all. The two flagged "gaps" (Standard/Simplified `allowanceTotalAmount` and line-level `discountAmount`/`allowanceAmount`) are decomposable into V59/V60 child-table rows but require the Sprint 1 service-layer mapper to derive VAT classification for the child rows from line context. This is a Sprint 1 implementation detail, not a schema gap.
