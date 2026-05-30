# Phase 1 — Data Model: ERP Ingestion Gateway

**Branch**: `011-erp-ingestion-gateway` | **Date**: 2026-05-27

This feature is dominated by **inbound DTOs** (request shape) and **mappings to existing entities** (write shape). Only one new persisted entity is introduced: `InboundPayloadArchive`. Existing operational entities (ETA Invoice / Receipt / ZATCA Standard / Simplified header + line + line-tax tables) are consumed in the SDK-aligned shape delivered by `specs/010-authority-spec-alignment/` (V58–V61); their schema is reproduced here only where the gateway writes to columns introduced by V58–V61.

---

## 1. New entity: `InboundPayloadArchive`

Backs `inbound_payload_archive` (created by Flyway V62). Append-only, immutable at the application layer (no UPDATE, no DELETE — except the `outcome` column patch performed by the archive filter on response committed; that single column UPDATE is the *only* permitted mutation and is treated as part of the original write).

| Column | Type | Nullable | Notes |
|---|---|---|---|
| `id` | UUID | NO | PK. Generated server-side at filter entry; returned to MDC as `payloadArchiveId`. |
| `endpoint` | VARCHAR(120) | NO | The request path, e.g. `/api/integration/v1/eta/receipts`. |
| `received_at` | TIMESTAMPTZ | NO | Default `NOW()`. BRIN-indexed. |
| `body` | JSONB | NO | Raw inbound JSON. Stored verbatim — no canonicalisation, no field stripping. |
| `company_id` | UUID | YES | Populated once `CompanyResolutionService.resolve()` succeeds. NULL when resolution failed before the row was committed (e.g. unknown `companyRegistrationNumber`). |
| `authority_environment_id` | SMALLINT | YES | Same nullability semantics as `company_id`. |
| `outcome` | SMALLINT | YES | HTTP status code, patched on response committed. NULL only transiently between request entry and response. Reads outside the request lifecycle treat NULL as "request crashed before completion" — observable for ops. |

**Indexes**:
- PK on `id` (B-tree, default).
- `inbound_payload_archive_received_at_brin` BRIN on `received_at` — cheap append-friendly index for forensic range queries by recency.

**RBAC**: `GRANT SELECT ON inbound_payload_archive TO integration_forensics;` (the role is created in V62 if absent). No application code outside the archive filter writes to this table.

**Lifecycle / state transitions**: None — this is an event log, not a stateful entity. The single `outcome` patch is conceptually part of the original write, not a state transition.

**Validation rules**: None at the DB level (smart-app / dumb-DB pattern from 010). Application invariants: `body` is non-empty raw JSON; `endpoint` matches one of the four ingest paths; `outcome` is in `{201, 400, 404, 409, 5xx}` when set.

---

## 1.5 Seeded system-principal `users` row (FR-009c)

The V62 migration seeds exactly one row in `users` to represent the gateway as a JWT-less principal. This row exists ONLY to populate `TenantContext.user_id` per FR-009d (which is required by Constitution §XV.6); it is NOT used as the value of any author / submitted-by audit column — those continue to receive the literal string `INTEGRATION_GATEWAY` per FR-009a.

**Note (S1)**: The post-V40 `users` table has no `username` column. V62 inserts into `(id, name, email, password_hash, is_super_user, is_active)` with `ON CONFLICT (email) DO NOTHING` — not `ON CONFLICT (username)` as the original task T004 step 2 assumed. The `name` column carries the human-readable sentinel; `email` is the unique constraint used for idempotent re-runs.

| Column | Value | Notes |
|---|---|---|
| `id` | `00000000-0000-0000-0000-000000000011` | Fixed UUID recorded in `application.yml` under `einvoice.integration.gateway-principal-id`. |
| `name` | `INTEGRATION_GATEWAY` | Matches the literal in FR-009a so audit-grepping is trivially correlated. |
| `email` | `integration-gateway@system.internal` | Sentinel email; the ON CONFLICT target for idempotent re-runs of V62. |
| `password_hash` | `$2a$10$INTEGRATION_GATEWAY_SENTINEL_HASH_NEVER_VALIDATES` | Schema requires NOT NULL; this sentinel never matches any login attempt. |
| `is_active` | `FALSE` | Prevents UI login. |
| `is_super_user` | `FALSE` | Not a super user. |

No `user_company_transaction_roles` rows are inserted for this user — by design, since Sprint 1 has no permission check on the gateway path (Constitution VIII caveat per plan.md). Sprint 2's API-key feature is responsible for adding the appropriate role grants when it introduces partner identity.

**Note (S2)**: V62 step 3 (audit-log `action` allow-list extension) is intentionally a no-op. V52's `append_only_guard()` trigger only rejects UPDATE/DELETE — there is no CHECK constraint or trigger allow-list on `audit_logs.action`. The `INGESTED` action value is purely application-enforced; no migration change was needed.

---

## 1.6 Author audit-column inventory (A2)

Per FR-009a the literal `INTEGRATION_GATEWAY` is stamped into every author / submitter audit column on every row the gateway writes. The columns present on each operational table after V58–V61 + V52 are:

| Table | `created_by` | `updated_by` | `submitted_by` | Notes |
|---|---|---|---|---|
| `eta_invoice_headers` | YES | YES | YES (V52) | Header-level submitter is set by the gateway to the literal; lines/line-taxes carry created_by/updated_by only. |
| `eta_invoice_lines` | YES | YES | — | |
| `eta_invoice_line_taxes` | YES | YES | — | |
| `eta_receipt_headers` | YES | YES | YES (V52) | Same as ETA Invoice. |
| `eta_receipt_lines` | YES | YES | — | |
| `zatca_standard_headers` | YES | YES | YES (V52) | |
| `zatca_standard_lines` | YES | YES | — | |
| `zatca_standard_tax_subtotals` (V59) | YES | YES | — | Verify column presence during T036 implementation — V59 may not have wired audit columns. If absent, omit. |
| `zatca_standard_allowances` (V59) | YES | YES | — | Same caveat as `tax_subtotals`. |
| `zatca_standard_line_allowances` (V60) | YES | YES | — | Same caveat. |
| `zatca_simplified_headers` | YES | YES | YES (V52) | |
| `zatca_simplified_lines` | YES | YES | — | |
| `zatca_simplified_*` child tables | (mirror Standard side) | | | |
| `audit_logs` | (uses `actor` column) | — | — | `actor='INTEGRATION_GATEWAY'` per FR-009b. |
| `inbound_payload_archive` | — | — | — | No author columns; the row itself is the audit record. |

If during implementation (T025 / T030 / T036 / T042) any column listed YES is found to be absent on the actual schema, default behaviour is to **silently omit** the stamp for that specific column. The literal MUST never become a "create the column if missing" trigger; that is a 010 / V58–V61 concern.

---

## 2. Inbound DTOs (request shape)

All DTOs are Java 17 `record`s in `platform-api/.../integration/dto/`. Jakarta Bean Validation 3.0 annotations on the components; no Lombok. The full annotated record source lives in `Docs/sprint-1-ingestion-gateway-implementation-plan.md` §6–§10 — this section catalogues the DTO tree and pin-points the gateway-routing fields that the source plan attaches to every request.

### 2.1 Shared types — `dto.shared`

| Type | Kind | Definition |
|---|---|---|
| `IntegrationEnvironment` | `enum` | `SANDBOX`, `PREPROD`. `PRODUCTION` is intentionally absent (FR-008). Jackson rejects unknown values with HTTP 400. |
| `IntegrationDocumentStatus` | `enum` | `DRAFT`, `VALID`, `INVALID`, `CLEARED`, `REPORTED`, `REJECTED`, `FAILED`, `CANCELLED`. Exhaustive per FR-016. |
| `DocumentIngestionResponse` | `record` | `(UUID id, String documentNumber, String erpReferenceId, String internalStatus, String message)`. Returned by all four endpoints on HTTP 201. |

### 2.2 Gateway-routing fields (present on every request DTO)

Every request DTO begins with these four fields, validated identically:

```
@NotBlank @Size(max = 100) String companyRegistrationNumber  // resolves against companies.tax_number
@NotNull                   IntegrationEnvironment environment
@NotNull                   IntegrationDocumentStatus status   // ZATCA Simplified reuses this for reportingStatus
@Size(max = 100)           String erpReferenceId              // optional; persisted on header.erp_reference_id (V58)
```

### 2.3 `EtaReceiptIngestionRequest` (ETA Receipt SDK v1.2 shape)

Root + 11 nested records. Authoritative source: `Docs/eta-receipt-sdk-v1-2-alignment.md`; mechanical record-definition source: source plan §7.

| Record | Cardinality | Purpose |
|---|---|---|
| `EtaReceiptIngestionRequest` | root | All v1.2 root-level fields + the 4 gateway-routing fields. |
| `Header` | 1 | `dateTimeIssued`, `receiptNumber`, `uuid` (SHA-256 hex, FR-012), `previousUUID`, `currency`, `exchangeRate`, etc. |
| `DocumentType` | 1 | `receiptType` (regex `r|rr|rrwr|cr|crr|gs|gsr` — aligned with `EtaReceiptDocumentType` enum), `typeVersion` (regex `1\.2`). |
| `Seller` | 1 | RIN, trade name, branch code, device serial (formerly posSerial), activity code, syndicate license. |
| `BranchAddress` | nested in Seller | country / governate / region / street / building / postal / floor / room / landmark. |
| `Buyer` | 1 | `type` ∈ {B,P,F}; `id`/`name` nullable at DTO, required at service layer per FR-011. |
| `Discount` | 0..N | header & item discounts. |
| `ItemData` | 1..N | per-line: internal/item codes, item type (GS1\|EGS), unit, qty, price, totals, `taxableItems`. |
| `TaxableItem` | 0..N per item | taxType / amount / subType / rate. |
| `TaxTotal` | 0..N at root | document-level tax totals. |
| `Contractor` | 0..1 | name / amount / rate. |
| `Beneficiary` | 0..1 | amount / rate. |

### 2.4 `EtaInvoiceIngestionRequest` (ETA Invoice SDK v1.0 shape)

Root + 5 nested records. Authoritative source: source plan §8.

| Record | Cardinality | Purpose |
|---|---|---|
| `EtaInvoiceIngestionRequest` | root | Header fields + 4 gateway-routing fields + `etaUuid`/`etaLongId`/`etaSubmissionId` reference triple + `originalInvoiceNumber` (FR-017) + `lines[]`. |
| `TaxpayerParty` | 2 (seller, buyer) | `type` ∈ {B,P,F}, `id`, `name`, `address`. |
| `PartyAddress` | nested in TaxpayerParty | country / governate / region / street / building / postal / floor / room / landmark / additionalInformation. |
| `InvoiceLine` | 1..N | per-line: codes, item type, qty, `unitValue`, totals, discounts, `taxableItems`. |
| `UnitValue` | 1 per line | `currencySold`, `amountEGP`, `amountSold`, `currencyExchangeRate`. Maps to the existing `unit_value` JSONB column on `eta_invoice_lines`. |
| `LineTax` | 0..N per line | `taxType`, `subType`, `rate`, `amount`. Persists to `eta_invoice_line_taxes`. |

### 2.5 `ZatcaStandardInvoiceIngestionRequest` (ZATCA Fatoora v2.0.3 — Standard / B2B)

Root + 3 nested records. Authoritative source: source plan §9 + `Docs/zatca-spec-alignment.md` (post-V58/V59/V60/V61 shape).

| Record | Cardinality | Purpose |
|---|---|---|
| `ZatcaStandardInvoiceIngestionRequest` | root | Header fields + 4 gateway-routing fields + UBL `LegalMonetaryTotal` block (`lineExtensionAmount`, `allowanceTotalAmount`, `taxExclusiveAmount`, `taxAmount`, `taxInclusiveAmount`, `prepaidAmount`, `payableAmount`) + ZATCA chain fields (`invoiceCounterValue`, `previousInvoiceHash`, `invoiceHash`, `qrCodeBase64`, `clearanceStatus`) + `originalInvoiceNumber` + `lines[]`. `buyer` is `@NotNull`. |
| `SellerParty` | 1 | Saudi 15-digit VAT (`3[0-9]{14}`), street/building/city/country (`SA`-anchored), optional `additionalStreetName`/`plotIdentification`. |
| `BuyerParty` | 1 (required) | Same shape as SellerParty; `@NotBlank` on every required UBL field per BR-KSA-EN16931-04 etc. |
| `LineItem` | 1..N | UBL `InvoiceLine` shape: codes, qty, `unitPrice` (BT-146 `item_net_price` per V60), `lineExtensionAmount`, `discountAmount`, `allowanceAmount`, `netAmount`, `vatCategoryCode` (S\|Z\|E\|O), `vatRate`, `vatAmount`, exemption-reason fields (required when category is E or O — FR-011). |

### 2.6 `ZatcaSimplifiedInvoiceIngestionRequest` (ZATCA Fatoora v2.0.3 — Simplified / B2C)

Same shape as Standard with three precise differences (source plan §10):

- `transactionTypeCode` pattern is `0[01]{3}0000` (bit-1 = 0 enforces Simplified).
- `buyer` is `@Valid` only (no `@NotNull`); anonymous retail is the dominant case.
- `BuyerParty` fields have no `@NotBlank` — the entire object is optional.
- The status field on the response/storage path is `reportingStatus` (REPORTED / REJECTED), not `clearanceStatus`. The inbound DTO carries `clearanceStatus` semantics under the shared `status` field; the service maps it to `reporting_status` on the simplified header (FR-016 maps `REPORTED` → `ACCEPTED`).

---

## 3. Mapping: inbound DTO → existing operational entities

Mappings reproduce the source plan §13 / §14 tables — only fields that consume V58–V61 columns or that require non-trivial transformation are highlighted here.

### 3.1 ETA Receipt → `eta_receipt_headers` + `eta_receipt_lines`

Selected mappings (full table in source plan §13):

| DTO field | Entity column | Notes |
|---|---|---|
| `header.receiptNumber` | `receipt_number` | Dedup key — FR-014. |
| `header.uuid` | `eta_receipt_uuid` | Format validated by FR-012; never canonicalised. |
| `header.dateTimeIssued` | `issue_datetime` | |
| `header.exchangeRate` | `exchange_rate` | New V58 column (moved from line to header per ETA v1.2). |
| `documentType.receiptType` | `document_type` (enum) | Parse via `EtaReceiptDocumentType.valueOf()` — all DTO-pattern values (`r|rr|rrwr|cr|crr|gs|gsr`) are present in the enum. |
| `seller` (record) | `seller_data` (JSONB) | `toSellerMap()`; field order preserved per FR-020. |
| `seller.deviceSerialNumber` | `pos_serial` | Also kept in `seller_data` JSONB for the read-side convention. |
| `buyer` (record) | `buyer_data` (JSONB) | `toBuyerMap()`; same convention. |
| `paymentMethod` | `payment_method` | |
| `totalSales` | `total_sales_amount` | |
| `totalCommercialDiscount` | `total_commercial_discount` | V58 rename (was `total_discount_amount`). |
| `extraReceiptDiscountData` (sum) | `extra_discount_amount` | Sum of `Discount.amount` over the list. |
| `totalItemsDiscount` | `total_items_discount_amount` | |
| `netAmount` | `net_amount` | |
| `totalAmount` | `total_amount` | |
| `status` (mapped) | `state` | Via `toDocumentState()` — FR-016. |
| `erpReferenceId` | `erp_reference_id` | V58 column (010). |
| — | `created_by` / `updated_by` | Literal `INTEGRATION_GATEWAY` per FR-009a. |

Lines: each `ItemData` becomes one `eta_receipt_lines` row; `taxableItems[]` becomes the JSONB on the line (after V60 receipt-line restructure delivered by 010). Per-line scalar `unit_price` is set from `ItemData.unitPrice` (V58 added the scalar column; the old `unit_value` JSONB is removed on the receipt path).

### 3.2 ETA Invoice → `eta_invoice_headers` + `eta_invoice_lines` + `eta_invoice_line_taxes`

Selected mappings (full table in source plan §13):

| DTO field | Entity column | Notes |
|---|---|---|
| `invoiceNumber` | `invoice_number` | Dedup key. |
| `documentType` | `document_type` (enum) | `I` → `EtaInvoiceDocumentType.I` etc. |
| `seller` / `buyer` (TaxpayerParty) | `seller_data` / `buyer_data` (JSONB) | `toTaxpayerMap()`. |
| `etaUuid` / `etaLongId` / `etaSubmissionId` | same columns | ERP-supplied; gateway never recomputes. |
| `originalInvoiceNumber` | `original_invoice_number` + `original_document_id` | Hybrid resolution per FR-017 + R5. |
| `status` (mapped) | `state` | Via FR-016. |
| `erpReferenceId` | `erp_reference_id` | V58. |

Lines: each `InvoiceLine` → one `eta_invoice_lines` row with `UnitValue` materialised as the existing `unit_value` JSONB. `LineTax[]` → N rows in `eta_invoice_line_taxes` keyed by line.

### 3.3 ZATCA Standard → `zatca_standard_headers` + `zatca_standard_lines` + child tables

Per V58–V61, ZATCA Standard now writes through several child tables. The gateway writes them all in the same transaction:

- Header: `zatca_standard_headers` — promoted party columns (V58 schematron-validated structural fields), `clearance_status`, chain fields, `original_invoice_number`, `payment_means_code`/`payment_means_text` (single denormalised columns per source plan §10), `tax_amount_accounting_currency` (always populated; equals `tax_amount` when `currency='SAR'`), `zatca_config_id` (FK to `zatca_configs(id)`, resolved by lookup on the resolved `(company_id, authority_environment_id)`).
- Lines: `zatca_standard_lines` — `item_net_price` (BT-146, V60 rename), `item_gross_price`, `item_price_discount`, `item_price_base_quantity` (default 1), `item_price_base_quantity_unit`, `vat_category_code`, `vat_rate`, `vat_amount`, `exemption_reason_code` / `exemption_reason_text` (required when category ∈ {E, O} per FR-011).
- `zatca_standard_tax_subtotals` (V59) — one row per VAT rate present in the lines. **The gateway computes these** by grouping the inbound lines by `vatRate` + `vatCategoryCode`. ZATCA UBL serialisation reads from this child table; therefore the gateway MUST populate it to keep the read-side consistent.
- `zatca_standard_allowances` (V59) — one row per header-level allowance. The inbound DTO carries header allowances inline in the UBL totals block; the gateway materialises them into the child table.
- `zatca_standard_line_allowances` (V60) — one row per line-level allowance.

**Signature columns** (`cryptographic_stamp_value`, `signed_xml_artifact_id`, `signed_at`) are populated only when the ERP supplies them in the chain block of the request. The platform never recomputes or signs.

### 3.4 ZATCA Simplified → `zatca_simplified_headers` + child tables

Identical to Standard with the same caveats from §2.6 (Simplified-shape `transaction_type_code`, optional buyer JSONB, `reporting_status` not `clearance_status`).

---

## 4. New exceptions (already declared in spec.md §Key Entities)

| Exception | Code | HTTP | Carries |
|---|---|---|---|
| `CompanyNotFoundException` (`platform-core.error`) | `COMPANY_NOT_FOUND` | 404 | `registrationNumber` (echo of `tax_number` lookup that failed) |
| `AuthorityEnvironmentNotFoundException` (`platform-core.error`) | `AUTHORITY_ENVIRONMENT_NOT_FOUND` | 404 | `authority`, `environment` (both echoed) |

Both follow the existing `*NotFoundException` template in the codebase: final string `CODE` constant, fields exposed by getters, super message human-readable.

---

## 5. Repository additions

| Repository | New method |
|---|---|
| `CompanyRepository` | `Optional<Company> findByTaxNumberAndIsActiveTrue(String taxNumber)` |
| `ZatcaStandardHeaderRepository` | `boolean existsByCompanyIdAndAuthorityEnvironmentIdAndInvoiceNumber(UUID, Short, String)` |
| `ZatcaStandardHeaderRepository` | `Optional<ZatcaStandardHeader> findByCompanyIdAndAuthorityEnvironmentIdAndInvoiceNumber(UUID, Short, String)` — for FR-017 hybrid resolution |
| `ZatcaSimplifiedHeaderRepository` | same pair, scoped to simplified |
| `InboundPayloadArchiveRepository` (NEW) | `save(InboundPayloadArchive)` + `findById(UUID)` (Spring Data default; no custom queries needed in this feature) |

`EtaInvoiceHeaderRepository` and `EtaReceiptHeaderRepository` already expose the `findByCompanyIdAndAuthorityEnvironmentIdAnd<Number>()` method needed for dedup and original-doc resolution; no edits.

---

## 6. State machine — `IntegrationDocumentStatus` → `DocumentState`

Implemented as a private static method in each ingestion service (per source plan §12):

```
DRAFT      → DRAFT
VALID      → ACCEPTED
INVALID    → REJECTED
CLEARED    → ACCEPTED
REPORTED   → ACCEPTED
REJECTED   → REJECTED
FAILED     → REJECTED
CANCELLED  → CANCELLED
```

Exhaustive switch; the compiler guarantees coverage. No partial mapping or fallthrough.

---

## 7. Cross-field service-layer rules (enforced post-DTO validation)

Re-stated from FR-011 for traceability into `tasks.md`:

| Rule | Service | Exception |
|---|---|---|
| ETA Invoice: `documentType ∈ {C, D}` ⇒ `originalInvoiceNumber` non-null | `EtaIngestionService.ingestInvoice()` | `MissingOriginalDocumentException` (existing) |
| ZATCA: `invoiceTypeCode ∈ {381, 383}` ⇒ `originalInvoiceNumber` non-null | `ZatcaIngestionService.ingest{Standard,Simplified}()` | `MissingOriginalDocumentException` |
| ZATCA line: `vatCategoryCode ∈ {E, O}` ⇒ `exemptionReasonCode` and `exemptionReasonText` non-null | `ZatcaIngestionService` line loop | `VatExemptionReasonRequiredException` (existing) |
| ETA Receipt: `buyer.type = B` ⇒ `buyer.id` + `buyer.name` non-null | `EtaIngestionService.ingestReceipt()` | `BuyerIdentityRequiredException` (mapped to HTTP 400 `VALIDATION_ERROR` by `GlobalExceptionHandler`) |
| ETA Receipt: `buyer.type = P` AND `totalAmount ≥ 150_000 EGP` ⇒ `buyer.id` + `buyer.name` non-null | same | same |

---

## 8. Persistence boundaries

| Concern | Transaction | Justification |
|---|---|---|
| Archive write (FR-OBS-003) | `REQUIRES_NEW` in `InboundPayloadArchiveService` | Must survive the main ingest rollback (R1). |
| Header + lines + line-taxes + ZATCA child tables (`tax_subtotals`, `allowances`, `line_allowances`) | Single ingest transaction (`@Transactional` on each ingestion service method) | FR-018 — atomic save. |
| Audit-log insert (FR-009b) | Same ingest transaction | Audit appears iff the document was persisted. |
| Archive outcome UPDATE | Same `REQUIRES_NEW` transaction as the archive write, fired from the filter's `finally` block | Single-row UPDATE keyed by PK; cannot fail unless the row was already deleted (it won't be — append-only). |

---

## 9. Reads — what this feature does *not* add

No new read endpoint, no new query repository method, no new Angular component. Ingested documents surface through the existing list / detail / artifact paths (FR-023) precisely because they share the entity shape. SC-007 verifies this with an integration test that ingests one document per endpoint and then queries each via the existing read API.
