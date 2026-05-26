# Data Model — Feature 010 Authority Spec Alignment

## Scope

This document describes the entity-level changes introduced by V58–V61. It does **not** restate the full target schemas — for those, see:

- [`Docs/zatca-spec-alignment.md`](../../Docs/zatca-spec-alignment.md) §10 — target `zatca_standard_headers` schema
- [`Docs/zatca-spec-alignment.md`](../../Docs/zatca-spec-alignment.md) §12 — UBL XPath ↔ column reference card
- [`Docs/eta-receipt-sdk-v1-2-alignment.md`](../../Docs/eta-receipt-sdk-v1-2-alignment.md) §10 — target ETA Receipt v1.2 payload
- [`Docs/eta-receipt-sdk-v1-2-alignment.md`](../../Docs/eta-receipt-sdk-v1-2-alignment.md) §12 — JSON path ↔ column ↔ Java field reference card

This document captures the **delta per migration** and the entity/JPA shape that ships in the same PR. Every constraint-related decision references [`deferred-validation.md`](./deferred-validation.md) instead of restating the rules.

---

## V58 — Header-level additions

### `ZatcaStandardHeader` (existing entity, modified)

**Added scalar/string/JSONB-free fields** (no DB constraints; service-layer rules per `deferred-validation.md` §V58):

| Field (Java) | Column type | DB default | UBL XPath / Rule ID |
|---|---|---|---|
| `businessProcessCode` | `VARCHAR(40)` | `'reporting:1.0'` | `cbc:ProfileID` / BT-23 |
| `issuanceReason` | `VARCHAR(127)` | NULL | `cbc:Note` / KSA-10 |
| `billingReferenceId` | `VARCHAR(100)` | NULL | `cac:BillingReference/cac:InvoiceDocumentReference/cbc:ID` / BT-25 |
| `originalInvoiceNumber` | `VARCHAR(100)` | NULL | Sprint 1 hybrid resolution |
| `erpReferenceId` | `VARCHAR(100)` | NULL | Sprint 1 ingestion |
| `taxAmountAccountingCurrency` | `NUMERIC(18,2)` | `0` | `cac:TaxTotal/cbc:TaxAmount` (second occurrence) / BT-111 |
| `roundingAmount` | `NUMERIC(18,2)` | `0` | `cbc:PayableRoundingAmount` / BT-114 |
| `paymentMeansCode` | `VARCHAR(3)` | NULL | `cac:PaymentMeans/cbc:PaymentMeansCode` / BT-81 |
| `paymentMeansText` | `VARCHAR(50)` | NULL | `cac:PaymentMeans/cbc:InstructionNote` |

**Promoted party fields** (from `seller_data` / `buyer_data` JSONB; free text remains in JSONB):

| Field | Column type | DB default |
|---|---|---|
| `sellerPartyId`, `sellerPartyIdScheme` | `VARCHAR(50)`, `VARCHAR(10)` | NULL |
| `sellerVatNumber`, `sellerGroupVatNumber` | `CHAR(15)` × 2 | NULL |
| `sellerBuildingNumber`, `sellerAdditionalNumber` | `CHAR(4)` × 2 | NULL |
| `sellerPostalCode` | `CHAR(5)` | NULL |
| `sellerCountryCode` | `CHAR(2)` | `'SA'` |
| `buyerPartyId`, `buyerPartyIdScheme`, `buyerVatNumber`, `buyerGroupVatNumber`, `buyerBuildingNumber`, `buyerAdditionalNumber`, `buyerPostalCode`, `buyerCountryCode` | mirror of seller | NULL |

**JSONB fields retained for free text**: `sellerData`, `buyerData` — house name, street, neighborhood, city.

**Backfill rule**: V58 emits `UPDATE … SET <promoted> = <jsonb path>` for each row. Read path in the entity service: prefer promoted column; fall back to JSONB for free text only.

**Index** (added in V58, backs SC-006):
`idx_zatca_std_seller_vat ON zatca_standard_headers(seller_vat_number) WHERE seller_vat_number IS NOT NULL`

### `ZatcaSimplifiedHeader` (existing entity, modified)

Mirror of Standard for the same fields. Differences:

- All `buyer_*` columns nullable (BR-KSA-10 excludes Simplified — anonymous retail allowed).
- `clearance_status` field name unchanged conceptually as `reporting_status` (per Clarifications §14.8 — keep names separate).
- Service layer enforces BR-KSA-60 (cryptographic stamp mandatory at submission) — see `deferred-validation.md` §V61.B.

**Index** (added in V58):
`idx_zatca_sim_seller_vat ON zatca_simplified_headers(seller_vat_number) WHERE seller_vat_number IS NOT NULL`

### `EtaReceiptHeader` (existing entity, modified)

**Renamed field** (in-place SQL rename + Java field rename in same PR — per locked decision):

| Old field | New field | Column type |
|---|---|---|
| `totalDiscountAmount` | `totalCommercialDiscount` | `NUMERIC(18,5)` |

**Added fields**:

| Field (Java) | Column type | DB default | SDK path |
|---|---|---|---|
| `exchangeRate` | `NUMERIC(18,5)` | NULL | `header.exchangeRate` |
| `previousUuid` | `TEXT` | NULL | `header.previousUUID` |
| `referenceOldUuid` | `TEXT` | NULL | `header.referenceOldUUID` |
| `sOrderNameCode` | `VARCHAR(200)` | NULL | `header.sOrderNameCode` |
| `orderDeliveryMode` | `VARCHAR(30)` | NULL | `header.orderdeliveryMode` |
| `grossWeight`, `netWeight` | `NUMERIC(18,5)` × 2 | NULL | `header.grossWeight` / `header.netWeight` |
| `taxTotals` | `JSONB` | NULL | `taxTotals` (array) |
| `extraReceiptDiscountData` | `JSONB` | NULL | `extraReceiptDiscountData` |
| `contractorData` | `JSONB` | NULL | `contractor` |
| `beneficiaryData` | `JSONB` | NULL | `beneficiary` |
| `feesAmount`, `adjustment` | `NUMERIC(18,5)` × 2 | `0` | SDK reserved (allow any non-negative) |
| `erpReferenceId`, `originalInvoiceNumber` | `VARCHAR(100)` × 2 | NULL | Sprint 1 ingestion |

**Backfill of `exchangeRate`**: from line-level `unit_value->>'currencyExchangeRate'` (first non-null per header). If multiple lines disagree, RAISE NOTICE per the spec.md Edge-Case bullet.

---

## V59 — ZATCA sub-tables

### `ZatcaStandardTaxSubtotal` (NEW entity, NEW table `zatca_standard_tax_subtotals`)

| Field | Column type | DB-side | Reference |
|---|---|---|---|
| `id` | `UUID` | `PRIMARY KEY DEFAULT gen_random_uuid()` | — |
| `headerId` | `UUID` | (FK deferred — §V59.B.1) | — |
| `vatCategoryCode` | `CHAR(1)` | (enum CHECK + NOT NULL deferred — §V59.C.1, §V59.D.3) | BG-23 |
| `vatRate` | `NUMERIC(8,2)` | nullable | BG-23 |
| `taxableAmount` | `NUMERIC(18,2)` | (NOT NULL deferred — §V59.D.1) | BG-23 |
| `taxAmount` | `NUMERIC(18,2)` | (NOT NULL deferred — §V59.D.2) | BG-23 |
| `exemptionReasonCode` | `VARCHAR(10)` | (cross-column rule deferred — §V59.C.2) | BR-KSA-CL-04 |
| `exemptionReasonText` | `VARCHAR(127)` | (cross-column rule deferred — §V59.C.2) | BR-KSA-CL-05 |
| `createdAt` | `TIMESTAMPTZ` | `DEFAULT NOW()` | — |

**JPA mapping**: `@OneToMany(mappedBy = "header", cascade = ALL, orphanRemoval = true)` on `ZatcaStandardHeader.taxSubtotals`. Service layer enforces uniqueness on `(headerId, vatCategoryCode, vatRate)` (deferred — §V59.A.1).

**Index** (V59): `idx_zatca_std_tax_subtotal_header ON zatca_standard_tax_subtotals(header_id)`.

**Backfill source** (V59): aggregated from `zatca_standard_lines GROUP BY (header_id, vat_category_code, vat_rate)` — `taxable_amount = SUM(net_amount)`, `tax_amount = SUM(vat_amount)`, `exemption_reason_code = MAX(exemption_reason_code)`, `exemption_reason_text = MAX(exemption_reason_text)`.

### `ZatcaStandardAllowance` (NEW entity, NEW table `zatca_standard_allowances`)

| Field | Column type | DB-side | Reference |
|---|---|---|---|
| `id` | `UUID` | `PRIMARY KEY DEFAULT gen_random_uuid()` | — |
| `headerId` | `UUID` | (FK deferred — §V59.B.3) | — |
| `sequence` | `SMALLINT` | (NOT NULL + UNIQUE deferred — §V59.A.2) | — |
| `amount` | `NUMERIC(18,2)` | (NOT NULL deferred) | BT-92 |
| `baseAmount` | `NUMERIC(18,2)` | nullable | BT-93 |
| `percentage` | `NUMERIC(6,2)` | (range CHECK deferred — §V59.C.4) | BT-94 |
| `vatCategoryCode` | `CHAR(1)` | (NOT NULL + enum CHECK deferred) | BT-95 |
| `vatRate` | `NUMERIC(8,2)` | nullable | BT-96 |
| `reasonCode` | `VARCHAR(10)` | nullable | BT-98 |
| `reason` | `VARCHAR(127)` | nullable | BT-97 |
| `createdAt` | `TIMESTAMPTZ` | `DEFAULT NOW()` | — |

**Backfill rule** (V59): for each `zatca_standard_headers` row where `allowance_total_amount > 0`, insert one synthetic allowance row with `sequence = 1`, `amount = allowance_total_amount`, `vat_category_code = 'S'`, `vat_rate = 15.00`, `reason = 'migrated-from-aggregate'`. After backfill, V59 drops `zatca_*_headers.allowance_total_amount`.

**Computed-on-read**: BT-107 (document allowance total) is no longer stored. The UBL serialiser sums `zatca_standard_allowances.amount` per header at emission time. A derived getter `ZatcaStandardHeader.allowanceTotal()` is added in the same PR.

**Index** (V59): `idx_zatca_std_allowance_header ON zatca_standard_allowances(header_id)`.

### Mirror tables for Simplified

`ZatcaSimplifiedTaxSubtotal` and `ZatcaSimplifiedAllowance` — same shape; semantic FK target is `zatca_simplified_headers`.

---

## V60 — Line-level changes

### `ZatcaStandardLine` (existing entity, modified)

**Rename**: `unitPrice` → `itemNetPrice` (BT-146). Mechanical via add+backfill+drop pattern in SQL; Java rename in the same PR.

**Added fields**:

| Field | Column type | DB default | Reference |
|---|---|---|---|
| `itemGrossPrice` | `NUMERIC(18,5)` | NULL | BT-148 |
| `itemPriceDiscount` | `NUMERIC(18,5)` | NULL | BT-147 |
| `itemPriceBaseQuantity` | `NUMERIC(18,5)` | `1` | BT-149 |
| `itemPriceBaseQuantityUnit` | `VARCHAR(127)` | NULL | BT-150 |
| `vatInclusiveAmount` | `NUMERIC(18,2)` | — (backfilled = `line_extension_amount + vat_amount`) | KSA-12 |

**Dropped fields**: `discountAmount`, `allowanceAmount` (moved to `ZatcaStandardLineAllowance` child).

### `ZatcaStandardLineAllowance` (NEW entity, NEW table `zatca_standard_line_allowances`)

| Field | Column type | DB-side | Reference |
|---|---|---|---|
| `id` | `UUID` | `PRIMARY KEY DEFAULT gen_random_uuid()` | — |
| `lineId` | `UUID` | (FK to `zatca_standard_lines` deferred — §V60.B.1) | — |
| `sequence` | `SMALLINT` | (UNIQUE(line_id, sequence) deferred — §V60.A.1) | — |
| `amount` | `NUMERIC(18,2)` | (NOT NULL deferred — §V60.C.3) | BT-136 |
| `baseAmount` | `NUMERIC(18,2)` | nullable | BT-137 |
| `percentage` | `NUMERIC(6,2)` | (range CHECK deferred — §V60.C.4) | BT-138 |
| `reason` | `VARCHAR(127)` | nullable | — |
| `createdAt` | `TIMESTAMPTZ` | `DEFAULT NOW()` | — |

**Backfill** (V60): one row per existing line where `discount_amount + allowance_amount > 0`, with `amount = COALESCE(discount_amount, 0) + COALESCE(allowance_amount, 0)`, `sequence = 1`, `reason = 'migrated-from-flat'`. After backfill, V60 drops the flat columns from both line tables.

**Index** (V60): `idx_zatca_std_line_allow_line ON zatca_standard_line_allowances(line_id)`.

### `EtaReceiptLine` (existing entity, modified)

**Added fields**:

| Field | Column type | DB default |
|---|---|---|
| `unitPrice` | `NUMERIC(18,5)` | NULL (backfilled per currency rule) |
| `commercialDiscountData` | `JSONB` | `'[]'` |
| `itemDiscountData` | `JSONB` | `'[]'` |

**Dropped fields**: `unitValue` (JSONB), `discountRate`, `discountAmount`, `itemsDiscount`.

**Backfill rule for `unitPrice`** (currency-aware, per spec.md Edge Case):

```sql
unit_price = CASE
    WHEN h.currency = 'EGP' THEN (l.unit_value->>'amountEGP')::NUMERIC
    ELSE                          (l.unit_value->>'amountSold')::NUMERIC
END
```

---

## V61 — Signature artifacts

### Added to `ZatcaStandardHeader` and `ZatcaSimplifiedHeader`

| Field | Column type | DB-side | Reference |
|---|---|---|---|
| `cryptographicStampValue` | `TEXT` | nullable | KSA-15 ECDSA signature value |
| `signedXmlArtifactId` | `UUID` | nullable; **no FK** (deferred — §V61.A.2); semantically references `invoice_artifacts(id)` | — |
| `zatcaConfigId` | `UUID` | nullable; **no FK** (deferred — §V61.A.1); semantically references `zatca_configs(id)` | — |
| `signedAt` | `TIMESTAMPTZ` | nullable | — |

**Service-layer rule** (per `deferred-validation.md` §V61.B):
- On `ZatcaSimplifiedHeader`: when status transitions to a submitted/reported state, BOTH `cryptographicStampValue` AND `signedXmlArtifactId` MUST be non-null. State-machine guard enforces (BR-KSA-60).

**Backfill (V61)**:
1. From `zatca_response_data` JSONB:
   - `cryptographicStampValue` ← `zatca_response_data#>>'{signatureValue}'`
   - `signedAt` ← `(zatca_response_data#>>'{signedAt}')::TIMESTAMPTZ`
2. `zatcaConfigId` — two-pass best-effort lookup:
   - Pass 1: active `zatca_configs` row for `(company_id, authority_environment_id)`.
   - Pass 2: most-recent historic/inactive row for the same key.
   - Pass 3: leave NULL + `RAISE NOTICE` (per Clarifications 2026-05-26).

The V61 migration script carries an inline comment documenting why the column is intentionally nullable.

---

## Entity lifecycle changes

| Entity / Field | Was | Now | Migration |
|---|---|---|---|
| `ZatcaStandardHeader.allowanceTotal` | stored field `allowance_total_amount` | derived getter (sums `allowances.amount`) | V59 |
| `ZatcaStandardHeader.zatcaUuid` | `VARCHAR(255)` nullable | unchanged — type change to `UUID` abandoned per FR-012 (validation deferred — §V58.E) | V58 |
| `EtaReceiptHeader.totalCommercialDiscount` | field name was `totalDiscountAmount` | renamed in-place | V58 |
| `EtaReceiptLine.unitPrice` | inside `unitValue` JSONB | scalar `NUMERIC(18,5)` | V60 |
| `ZatcaStandardLine.itemNetPrice` | field name was `unitPrice` | renamed (via add+backfill+drop) | V60 |

---

## Indexes preserved on the DB

| Index | Migration | Purpose |
|---|---|---|
| `idx_zatca_std_seller_vat` | V58 | SC-006 — <100 ms VAT-number query |
| `idx_zatca_sim_seller_vat` | V58 | (same, Simplified) |
| `idx_zatca_std_tax_subtotal_header` | V59 | Header→sub-table join |
| `idx_zatca_sim_tax_subtotal_header` | V59 | (same, Simplified) |
| `idx_zatca_std_allowance_header` | V59 | Header→allowance join |
| `idx_zatca_sim_allowance_header` | V59 | (same, Simplified) |
| `idx_zatca_std_line_allow_line` | V60 | Line→line-allowance join |
| `idx_zatca_sim_line_allow_line` | V60 | (same, Simplified) |

---

## Out-of-scope entities (unchanged)

- `eta_invoice_headers` / `eta_invoice_lines` / `eta_invoice_line_taxes` — verified aligned (FR-022).
- `eta_customers`, `eta_items`, `zatca_customers`, `zatca_items` — no change.
- `zatca_configs` — referenced by the new `zatca_config_id` UUID column but not modified.
- `zatca_chain_state` — chain mechanism unchanged.
- `invoice_artifacts`, `submission_attempts`, `audit_logs` — append-only invariants preserved (Constitution IX, XXI).
