# ZATCA E-Invoicing Spec — Schema Alignment Investigation

**Source SDK:** ZATCA Fatoora SDK v2.0.3 (Phase 2 / Integration phase)
**Schematron:** `Data/Rules/schematrons/20210819_ZATCA_E-invoice_Validation_Rules.xsl`
**UBL schema:** `Data/Schemas/xsds/UBL2.1/xsd/maindoc/UBL-Invoice-2.1.xsd`
**Investigated:** 2026-05-25
**Decisions locked:** 2026-05-25
**Scope:** Diff between published ZATCA Phase-2 requirements and the current `zatca_standard_*` (V54) + `zatca_simplified_*` (V55) tables.
**Status:** Open questions resolved (see §14). Ready for inclusion in `specs/010-authority-spec-alignment/`.

---

## 1. Executive Summary

The current ZATCA tables were modelled to capture *enough* fields to round-trip a UBL invoice for the Wave-8 happy path, not to enforce the full KSA schematron at the DB layer. Diffing the two `CREATE TABLE` statements against the SDK schematron reveals five materially missing areas:

1. **Mandatory header identifiers are wrong type / nullability.** `zatca_uuid` is `VARCHAR(255)` nullable; KSA-1 requires ≤36 chars and is mandatory at submission.
2. **No header-level VAT breakdown table.** UBL `cac:TaxTotal/cac:TaxSubtotal` (BG-23) with one entry per VAT category is mandatory (BR-KSA-EN16931-08). Currently only line-level VAT exists; the per-rate header summary cannot be reconstructed without an aggregate column or table.
3. **No document-level allowance table.** UBL `cac:AllowanceCharge` (BG-20, BT-92 to BT-98) supports multiple header-level discounts with VAT category, base, percentage, reason. Schema collapses everything into one scalar `allowance_total_amount`.
4. **Line items miss the UBL price block.** BT-146 (item net price), BT-147 (item price discount), BT-148 (item gross price), BT-149 (item price base quantity), BT-150 (base quantity unit code) are all absent. BR-KSA-EN16931-11 enforces a formula across them that cannot be validated without storage.
5. **Phase 2 signature artifacts have no first-class storage.** Only `invoice_hash` and `qr_code_base64` columns exist. The signed XML, signing certificate, ECDSA signature value (KSA-15), and signature timestamp live nowhere (or implicitly in `zatca_response_data` JSONB).

Additionally, ~12 KSA address/identity fields are validated per-character by the schematron (digit counts, length limits, scheme IDs) but currently live in `seller_data` / `buyer_data` JSONB with no DB-level enforcement.

This document catalogues every gap; a separate migration plan will translate it to V58–V61 SQL.

---

## 2. Reference List of Spec Sources

| ID | Source | Used for |
|---|---|---|
| UBL 2.1 | `UBL-Invoice-2.1.xsd` | Canonical element/attribute names, cardinality |
| EN16931 | `CEN-EN16931-UBL.xsl` schematron | European business rules ZATCA layers on |
| KSA Schematron | `20210819_ZATCA_E-invoice_Validation_Rules.xsl` | KSA-specific business rules (`BR-KSA-*`) |
| ZATCA E-Invoicing Resolution | zatca.gov.sa publication | Legal mandate (out of scope here) |
| KSA Technical Standard PDF | ZATCA developer portal | BT/KSA field catalogue |

All `BR-KSA-*` rule references in this document come from the schematron file shipped in the SDK ZIP.

---

## 3. Header Diff — `zatca_standard_headers` (V54)

### 3.1 Identification & document type

| Spec field | BT/KSA ID | KSA rule | Current column | Action |
|---|---|---|---|---|
| Invoice number | BT-1 | mandatory | `invoice_number VARCHAR(100) NOT NULL` | OK |
| Invoice UUID | KSA-1 | BR-KSA-03 — mandatory at submission, ≤36 chars | `zatca_uuid VARCHAR(255)` nullable | **Fix type only** — change to `UUID`, keep nullable. NULL is correct for DRAFT documents; the column is populated at submission time. The schematron rule applies to submitted UBL, not DB state. |
| Invoice type code | BT-3 | BR-KSA-05 — must be 388/381/383 | `invoice_type_code VARCHAR(10) NOT NULL` | OK (consider `CHECK (invoice_type_code IN ('388','381','383'))`) |
| Invoice transaction code | KSA-2 | BR-KSA-06 — 7 positions, position 1+2 = 01 for Standard | `transaction_type_code VARCHAR(10) NOT NULL` | OK (consider `CHECK` for length=7 and digit-only) |
| Business process | BT-23 | BR-KSA-EN16931-01 — must be `"reporting:1.0"` | **missing** | **Add** `business_process_code VARCHAR(40) NOT NULL DEFAULT 'reporting:1.0'` |
| Note / reason for issuance | KSA-10 | BR-KSA-17 — mandatory for 381/383, ≤127 chars | **missing** | **Add** `issuance_reason VARCHAR(127)` + CHECK |
| Billing reference ID (original invoice number string) | BT-25 | BR-KSA-56 — mandatory for 381/383 | `original_invoice_id UUID FK` only | **Add** `billing_reference_id VARCHAR(100)` (carries the original invoice number even when not in this DB) |

### 3.2 Dates

| Spec field | BT/KSA ID | KSA rule | Current column | Action |
|---|---|---|---|---|
| Issue date | BT-2 | BR-KSA-04 — ≤ today | `issue_date DATE NOT NULL` | OK |
| Issue time | KSA-25 | BR-KSA-CL-70 — ≤24 chars (with offset) | `issue_time TIME NOT NULL` | OK (Java side must format with offset on serialisation) |
| Supply date | KSA-5 | BR-KSA-15 — mandatory for tax invoices | `supply_date DATE` | **Fix** — should be `NOT NULL` for Standard tax invoices (CHECK on `invoice_type_code = 388 AND transaction_type_code LIKE '01%'`) |
| Supply end date | KSA-24 | BR-KSA-35 — requires `supply_date` if present; BR-KSA-36 — must be > supply_date | `supply_end_date DATE` | OK (add `CHECK (supply_end_date IS NULL OR supply_end_date > supply_date)`) |

### 3.3 Currency

| Spec field | BT/KSA ID | KSA rule | Current column | Action |
|---|---|---|---|---|
| Document currency | BT-5 | BR-KSA-CL-01 — ISO 4217 | `currency VARCHAR(3) NOT NULL DEFAULT 'SAR'` | OK |
| Tax accounting currency | BT-6 | BR-KSA-68 mandatory; BR-KSA-EN16931-02 — must be `"SAR"` | `tax_currency VARCHAR(3) DEFAULT 'SAR'` | **Fix** — `NOT NULL` + `CHECK (tax_currency = 'SAR')` |

### 3.4 Monetary totals (UBL `cac:LegalMonetaryTotal`)

| Spec field | BT/KSA ID | Current column | Action |
|---|---|---|---|
| Sum of line net amounts | BT-106 | `line_extension_amount` | OK |
| Sum of document allowances | BT-107 | `allowance_total_amount` | OK |
| Sum of document charges | BT-108 | **missing** | Not needed — BR-KSA-EN16931-06 prohibits charges; explicit CHECK or simply don't store |
| Invoice total without VAT | BT-109 | `tax_exclusive_amount` | OK |
| Invoice total VAT amount | BT-110 | `tax_amount` | OK |
| Invoice total VAT in accounting currency | BT-111 | **missing** | **Add** `tax_amount_accounting_currency NUMERIC(18,2) NOT NULL DEFAULT 0`. **Resolved 2026-05-25:** always populated; equals `tax_amount` when currency = SAR. Simpler service code, validator-clean for both cases. |
| Invoice total with VAT | BT-112 | `tax_inclusive_amount` | OK |
| Paid amount | BT-113 | `prepaid_amount` | OK |
| Rounding amount | BT-114 | **missing** | **Add** `rounding_amount NUMERIC(18,2) DEFAULT 0` |
| Amount due | BT-115 | `payable_amount` | OK |

### 3.5 Authority response & chain

| Spec field | BT/KSA ID | KSA rule | Current column | Action |
|---|---|---|---|---|
| Invoice Counter Value | KSA-16 | BR-KSA-33/34 — mandatory, digits only | `invoice_counter_value BIGINT` | **Fix** — `NOT NULL` post-submit |
| Previous Invoice Hash | KSA-13 | BR-KSA-26 — base64 SHA-256; BR-KSA-61 — mandatory | `previous_invoice_hash TEXT` | OK; consider `CHAR(44)` (base64 of 32 bytes) |
| Invoice Hash | (current) | — | `invoice_hash TEXT` | OK |
| QR code | KSA-14 | BR-KSA-27 — base64Binary | `qr_code_base64 TEXT` | OK |
| Cryptographic stamp | KSA-15 | BR-KSA-28/29/30 — specific URN values for signature info | **missing** (only in `zatca_response_data` JSONB) | **Add** `cryptographic_stamp_value TEXT` (the actual ECDSA signature value), separate from `invoice_hash` |
| Signed XML | — | — | **missing** | **Add** `signed_xml_artifact_id UUID REFERENCES invoice_artifacts(id)` |
| Signing certificate | — | — | **missing** | **Add** `zatca_config_id UUID REFERENCES zatca_configs(id)`. **Resolved 2026-05-25:** reuse existing `zatca_configs` table (created in V46) instead of introducing a new `zatca_signing_certificates` table. That table already holds `private_key`, `compliance_certificate`, `production_certificate`, `device_uuid`, `csr` per the Phase-1 plain-text-storage policy (Principle XVIII). |
| Signature timestamp | — | — | **missing** | **Add** `signed_at TIMESTAMPTZ` |
| Authority status (clearance) | — | — | `clearance_status VARCHAR(50)` | OK (kept Standard-specific) |
| Full authority response | — | — | `zatca_response_data JSONB` | OK as audit blob |

---

## 4. Seller Party Diff (`seller_data` JSONB → typed columns)

The schematron validates ~7 seller fields individually. All currently live in `seller_data` JSONB with no DB enforcement.

| Spec field | BT/KSA ID | KSA rule | Action |
|---|---|---|---|
| Seller name | BT-27 | mandatory | Keep in JSONB (free text) |
| Seller ID + scheme | BT-29 / BT-29-1 | BR-KSA-08 — single occurrence, scheme ∈ {CRN, MOM, MLS, SAG, OTH}; BR-KSA-19 — alphanumeric only | **Promote** to `seller_party_id VARCHAR(50)`, `seller_party_id_scheme VARCHAR(10) CHECK (… IN (…))` |
| Seller VAT number | BT-31 | BR-KSA-39 mandatory; BR-KSA-40 — 15 digits, first/last = `3` | **Promote** to `seller_vat_number CHAR(15) CHECK (seller_vat_number ~ '^3[0-9]{13}3$')` |
| Seller group VAT number | KSA-18 | BR-KSA-41 — 15 digits, first/last = `3`, 11th = `1` | **Add** `seller_group_vat_number CHAR(15)` + CHECK |
| Street name | BT-35 | BR-KSA-09 mandatory | Keep in JSONB or promote (free text, ≤127) |
| Building number | KSA-17 | BR-KSA-37 — 4 digits | **Promote** to `seller_building_number CHAR(4) CHECK (seller_building_number ~ '^[0-9]{4}$')` |
| Additional number | KSA-23 | BR-KSA-64/65 — 4 digits | **Promote** to `seller_additional_number CHAR(4) CHECK (seller_additional_number ~ '^[0-9]{4}$')` |
| Neighborhood | KSA-3 | BR-KSA-09 mandatory, ≤127 chars | Keep in JSONB (free text) |
| City | BT-37 | BR-KSA-09 mandatory | Keep in JSONB (free text) |
| Postal code | BT-38 | BR-KSA-66 — 5 digits | **Promote** to `seller_postal_code CHAR(5) CHECK (seller_postal_code ~ '^[0-9]{5}$')` |
| Country code | BT-40 | BR-KSA-38 — must be `"SA"` | **Promote** to `seller_country_code CHAR(2) NOT NULL DEFAULT 'SA' CHECK (seller_country_code = 'SA')` |

**Recommendation:** Promote VAT numbers, building number, additional number, postal code, country code. Leave name/street/neighborhood/city in `seller_data` JSONB (free text with no structural enforcement value).

---

## 5. Buyer Party Diff (`buyer_data` JSONB → typed columns)

Same pattern as seller, plus a wider scheme ID range and conditional nullability for Simplified (handled in §7).

| Spec field | BT/KSA ID | KSA rule | Action |
|---|---|---|---|
| Buyer name | BT-44 | BR-KSA-42 mandatory for tax invoices (`KSA-2` pos 1+2 = 01); ≤127 chars | Keep in JSONB |
| Buyer ID + scheme | BT-46 / BT-46-1 | BR-KSA-14 — single occurrence, scheme ∈ {NAT, IQA, PAS, CRN, MOM, MLS, SAG, GCC, OTH} | **Promote** to `buyer_party_id VARCHAR(50)`, `buyer_party_id_scheme VARCHAR(10)` |
| Buyer VAT number | BT-48 | BR-KSA-44 — 15 digits, first/last = `3`; BR-KSA-46 — must not exist for export | **Promote** to `buyer_vat_number CHAR(15)` + CHECK |
| Buyer group VAT number | KSA-20 | BR-KSA-45 — 15 digits, first/last = `3`, 11th = `1` | **Add** `buyer_group_vat_number CHAR(15)` + CHECK |
| Street name | BT-50 | BR-KSA-10 mandatory for Standard; ≤127 chars | Keep in JSONB |
| Building number | (KSA equivalent) | — | Keep in JSONB |
| Additional number | KSA-19 | BR-KSA-63 — 4 digits for SA buyers | **Promote** to `buyer_additional_number CHAR(4)` (nullable for non-SA) |
| City | BT-52 | BR-KSA-10 mandatory; ≤127 chars | Keep in JSONB |
| Postal code | BT-53 | BR-KSA-67 — 5 digits for SA buyers | **Promote** to `buyer_postal_code CHAR(5)` (nullable for non-SA) |
| Country code | BT-55 | BR-KSA-62 — must be SA unless export; ≤2 chars | **Promote** to `buyer_country_code CHAR(2)` |

---

## 6. Line Item Diff — `zatca_standard_lines` (V54)

### 6.1 Existing columns — OK

| Spec field | BT/KSA ID | Current column |
|---|---|---|
| Line number | BT-126 | `line_number INT NOT NULL` |
| Description | BT-153 | `description TEXT NOT NULL` |
| Quantity unit code | BT-130 | `unit_type VARCHAR(50)` |
| Invoiced quantity | BT-129 | `quantity NUMERIC(18,5) NOT NULL` |
| Line net amount | BT-131 | `line_extension_amount NUMERIC(18,2) NOT NULL` |
| Net amount (computed) | — | `net_amount NUMERIC(18,2) NOT NULL` |
| VAT category | BT-151 | `vat_category_code VARCHAR(5) NOT NULL` |
| VAT rate | BT-152 | `vat_rate NUMERIC(8,2)` |
| Line VAT amount | KSA-11 | `vat_amount NUMERIC(18,2) NOT NULL` |
| Exemption reason code | BT-121 | `exemption_reason_code VARCHAR(10)` |
| Exemption reason text | BT-120 | `exemption_reason_text TEXT` |

### 6.2 Missing UBL price block (mandatory for BR-KSA-EN16931-11)

| Spec field | BT/KSA ID | KSA rule | Action |
|---|---|---|---|
| Item gross price | BT-148 | ≤14 chars; required if price discount present | **Add** `item_gross_price NUMERIC(18,5)` |
| Item price discount | BT-147 | ≤14 chars | **Add** `item_price_discount NUMERIC(18,5)` |
| Item net price | BT-146 | BR-KSA-DEC-05 — max 2 decimals; ≤14 chars; mandatory | **Add** `item_net_price NUMERIC(18,2)` (separate from `unit_price` which currently conflates gross/net) |
| Item price base quantity | BT-149 | BR-KSA-EN16931-12 — positive, > 0 | **Add** `item_price_base_quantity NUMERIC(18,5) NOT NULL DEFAULT 1` |
| Item price base quantity unit | BT-150 | ≤127 chars | **Add** `item_price_base_quantity_unit VARCHAR(127)` |

> Note: Existing `unit_price` column should be **renamed** to `item_net_price` (or kept as a convenience alias). Current code paths treating `unit_price = quantity × line_extension_amount` will need review — the UBL definition is unit price *per* base quantity.

### 6.3 Line allowances — restructure to support multi-allowance

Current schema has flat `discount_amount` + `allowance_amount` scalars. UBL allows multiple `cac:AllowanceCharge` blocks per line, each with its own VAT category, base, percentage, reason.

| Spec field | BT/KSA ID | Action |
|---|---|---|
| Allowance amount | BT-136 | (multiple) |
| Allowance base amount | BT-137 | BR-KSA-EN16931-04 |
| Allowance percentage | BT-138 | BR-KSA-EN16931-05 |
| Allowance reason | (no BT for line-level) | optional |

**Action:** create new child table `zatca_standard_line_allowances`:

```sql
CREATE TABLE zatca_standard_line_allowances (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    line_id         UUID NOT NULL REFERENCES zatca_standard_lines(id) ON DELETE CASCADE,
    sequence        SMALLINT NOT NULL,
    amount          NUMERIC(18,2) NOT NULL,
    base_amount     NUMERIC(18,2),
    percentage      NUMERIC(6,2) CHECK (percentage IS NULL OR (percentage >= 0 AND percentage <= 100)),
    reason          VARCHAR(127),
    CONSTRAINT uq_zatca_std_line_allow UNIQUE (line_id, sequence)
);
```

After backfill from the old flat columns, drop `discount_amount` and `allowance_amount` (or keep as cached aggregates).

### 6.4 Line amount with VAT (KSA-12)

| Spec field | BT/KSA ID | KSA rule | Current | Action |
|---|---|---|---|---|
| Line amount inclusive of VAT | KSA-12 | BR-KSA-51/53 — mandatory for tax invoices; = BT-131 + KSA-11; BR-KSA-DEC-04 max 2 decimals | **missing** | **Add** `vat_inclusive_amount NUMERIC(18,2) NOT NULL` |

---

## 7. Simplified-Specific Differences (`zatca_simplified_*`, V55)

Simplified (B2C) shares the same UBL structure as Standard but with relaxed buyer requirements and stricter signature requirements.

| Aspect | Standard (V54) | Simplified (V55) | Action |
|---|---|---|---|
| Transaction type position 1+2 | `01` | `02` | enforce by service; no schema change |
| Buyer mandatory | YES | **NO** — anonymous retail allowed | Keep `buyer_data JSONB` nullable; promoted columns also nullable |
| Buyer name conditional | always | BR-KSA-71 — required for summary invoices (KSA-2 pos 6 = 1); BR-KSA-25 — required if exemption code is VATEX-SA-EDU or VATEX-SA-HEA | Service-layer CHECK; not enforceable in DB without complex constraint |
| Buyer address fields | mandatory (BR-KSA-10) | not required (BR-KSA-10 explicitly excludes Simplified) | Promoted columns nullable on Simplified table |
| Cryptographic stamp | optional pre-clearance | BR-KSA-60 — **mandatory** | `cryptographic_stamp_value` → `NOT NULL` on Simplified after backfill |
| Self-billing | allowed | not allowed (BR-KSA-31) | Service-layer CHECK |
| Authority status | `clearance_status` | `reporting_status` | Both kept; possibly unify into generic `authority_status` + `authority_stage` columns |

**All structural fixes from §3-§6 apply to both tables.** The migration must update both `zatca_standard_*` and `zatca_simplified_*`.

---

## 8. New Header-Level Sub-Tables (Required, Not Optional)

### 8.1 VAT breakdown — `cac:TaxTotal/cac:TaxSubtotal` (BG-23)

BR-KSA-EN16931-08 mandates exactly one `TaxTotal` block with per-category subtotals. This cannot live in a JSONB column because the validator parses it as structured UBL and computes formulas across rows.

```sql
CREATE TABLE zatca_standard_tax_subtotals (
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    header_id                UUID NOT NULL REFERENCES zatca_standard_headers(id) ON DELETE CASCADE,
    vat_category_code        CHAR(1) NOT NULL CHECK (vat_category_code IN ('S','Z','E','O')),
    vat_rate                 NUMERIC(8,2),  -- nullable for E (exempt) and O (out of scope)
    taxable_amount           NUMERIC(18,2) NOT NULL,
    tax_amount               NUMERIC(18,2) NOT NULL,
    exemption_reason_code    VARCHAR(10),
    exemption_reason_text    VARCHAR(127),
    CONSTRAINT uq_zatca_std_tax_subtotal UNIQUE (header_id, vat_category_code, vat_rate),
    CONSTRAINT chk_zatca_std_tax_subtotal_exempt CHECK (
        (vat_category_code IN ('E','O','Z') AND exemption_reason_code IS NOT NULL AND exemption_reason_text IS NOT NULL)
        OR vat_category_code = 'S'
    )
);
```

Mirror table for Simplified.

### 8.2 Document-level allowances (BG-20)

UBL allows multiple `cac:AllowanceCharge` at document level, each with VAT category and reason. Current schema collapses to one scalar.

```sql
CREATE TABLE zatca_standard_allowances (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    header_id           UUID NOT NULL REFERENCES zatca_standard_headers(id) ON DELETE CASCADE,
    sequence            SMALLINT NOT NULL,
    -- BG-20 fields
    amount              NUMERIC(18,2) NOT NULL,       -- BT-92
    base_amount         NUMERIC(18,2),                -- BT-93
    percentage          NUMERIC(6,2) CHECK (percentage IS NULL OR (percentage >= 0 AND percentage <= 100)),  -- BT-94
    vat_category_code   CHAR(1) NOT NULL CHECK (vat_category_code IN ('S','Z','E','O')),  -- BT-95
    vat_rate            NUMERIC(8,2),                 -- BT-96
    reason_code         VARCHAR(10),                  -- BT-98
    reason              VARCHAR(127),                 -- BT-97
    CONSTRAINT uq_zatca_std_allowance UNIQUE (header_id, sequence)
);
```

Mirror table for Simplified.

Cached aggregate stays in `zatca_*_headers.allowance_total_amount` for convenience; trigger or service-layer assertion keeps it consistent.

### 8.3 Payment means (BG-16)

BR-KSA-16 requires payment means code ∈ {10, 30, 42, 48, 1} for Standard.

**Resolved 2026-05-25:** denormalise as single columns on each header table — `payment_means_code VARCHAR(3)` + `payment_means_text VARCHAR(50)`. Cardinality is typically 1 in real ERPs; a child table is over-engineering for Sprint 1. If multi-method invoices become a real requirement later, migrate to a child table at that time.

```sql
-- Applied to both zatca_standard_headers and zatca_simplified_headers in V58:
ALTER TABLE zatca_standard_headers
    ADD COLUMN payment_means_code VARCHAR(3)
        CHECK (payment_means_code IS NULL OR payment_means_code IN ('1','10','30','42','48')),
    ADD COLUMN payment_means_text VARCHAR(50);
```

---

## 9. Phase 2 Signature Storage

Currently, only `invoice_hash` + `qr_code_base64` exist. Phase 2 submissions need to persist (a) the exact signed XML that was sent, (b) the certificate used, (c) the signature value separately from the hash, and (d) the signature timestamp.

**Resolved 2026-05-25:** the signing-certificate storage already exists as `zatca_configs` (V46). It holds `private_key`, `compliance_certificate`, `production_certificate`, `device_uuid`, `csr` per Principle XVIII (Phase-1 plain-text storage). **Do not create a new `zatca_signing_certificates` table.** The new `zatca_config_id` column on each header table FKs to `zatca_configs.id`.

```sql
ALTER TABLE zatca_standard_headers
    ADD COLUMN signed_xml_artifact_id    UUID REFERENCES invoice_artifacts(id),
    ADD COLUMN zatca_config_id           UUID REFERENCES zatca_configs(id),
    ADD COLUMN cryptographic_stamp_value TEXT,   -- ECDSA signature value (KSA-15), separate from invoice_hash
    ADD COLUMN signed_at                 TIMESTAMPTZ;

ALTER TABLE zatca_simplified_headers
    ADD COLUMN signed_xml_artifact_id    UUID REFERENCES invoice_artifacts(id),
    ADD COLUMN zatca_config_id           UUID REFERENCES zatca_configs(id),
    ADD COLUMN cryptographic_stamp_value TEXT,
    ADD COLUMN signed_at                 TIMESTAMPTZ;
```

Per BR-KSA-60, `cryptographic_stamp_value` is mandatory for Simplified after submission. Enforce via service-layer validation rather than DB `NOT NULL`, because DRAFT rows pre-signature legitimately have NULLs.

---

## 10. Corrected Header Schema — V54 Target State (Standard)

After V58–V61 land, `zatca_standard_headers` should resemble:

```sql
CREATE TABLE zatca_standard_headers (
    -- identity
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id                  UUID NOT NULL REFERENCES companies(id),
    branch_id                   UUID REFERENCES branches(id),
    authority_environment_id    SMALLINT NOT NULL REFERENCES authority_environments(id),

    -- document identity
    invoice_number              VARCHAR(100) NOT NULL,                     -- BT-1
    zatca_uuid                  UUID,                                       -- KSA-1; nullable on DRAFT, populated at submission
    erp_reference_id            VARCHAR(100),                              -- Sprint 1 ingestion gateway
    original_invoice_number     VARCHAR(100),                              -- Sprint 1: hybrid resolution string (per BT-25)

    -- document type
    invoice_type_code           VARCHAR(10) NOT NULL                       -- BT-3
                                  CHECK (invoice_type_code IN ('388','381','383')),
    transaction_type_code       CHAR(7) NOT NULL                           -- KSA-2
                                  CHECK (transaction_type_code ~ '^[01]{7}$'),
    business_process_code       VARCHAR(40) NOT NULL DEFAULT 'reporting:1.0',  -- BT-23
    issuance_reason             VARCHAR(127),                              -- KSA-10
    billing_reference_id        VARCHAR(100),                              -- BT-25

    -- dates
    issue_date                  DATE NOT NULL CHECK (issue_date <= CURRENT_DATE),  -- BT-2
    issue_time                  TIME NOT NULL,                             -- KSA-25
    supply_date                 DATE,                                       -- KSA-5
    supply_end_date             DATE CHECK (supply_end_date IS NULL OR supply_end_date > supply_date),

    -- seller (promoted columns)
    seller_party_id             VARCHAR(50),
    seller_party_id_scheme      VARCHAR(10)
                                  CHECK (seller_party_id_scheme IS NULL OR
                                         seller_party_id_scheme IN ('CRN','MOM','MLS','SAG','OTH')),
    seller_vat_number           CHAR(15) CHECK (seller_vat_number IS NULL OR seller_vat_number ~ '^3[0-9]{13}3$'),
    seller_group_vat_number     CHAR(15),
    seller_building_number      CHAR(4),
    seller_additional_number    CHAR(4),
    seller_postal_code          CHAR(5),
    seller_country_code         CHAR(2) NOT NULL DEFAULT 'SA' CHECK (seller_country_code = 'SA'),
    seller_data                 JSONB NOT NULL,                            -- street/neighborhood/city free text

    -- buyer (promoted columns)
    buyer_party_id              VARCHAR(50),
    buyer_party_id_scheme       VARCHAR(10),
    buyer_vat_number            CHAR(15),
    buyer_group_vat_number      CHAR(15),
    buyer_building_number       CHAR(4),
    buyer_additional_number     CHAR(4),
    buyer_postal_code           CHAR(5),
    buyer_country_code          CHAR(2),
    buyer_data                  JSONB NOT NULL,

    -- currency
    currency                    CHAR(3) NOT NULL DEFAULT 'SAR',            -- BT-5
    tax_currency                CHAR(3) NOT NULL DEFAULT 'SAR'             -- BT-6
                                  CHECK (tax_currency = 'SAR'),

    -- monetary totals (cac:LegalMonetaryTotal)
    -- BT-107 (allowance total) intentionally NOT stored — computed from zatca_*_allowances on read
    line_extension_amount       NUMERIC(18,2) NOT NULL DEFAULT 0,          -- BT-106
    tax_exclusive_amount        NUMERIC(18,2) NOT NULL DEFAULT 0,          -- BT-109
    tax_amount                  NUMERIC(18,2) NOT NULL DEFAULT 0,          -- BT-110
    tax_amount_accounting_currency NUMERIC(18,2) NOT NULL DEFAULT 0,       -- BT-111; equals tax_amount when currency=SAR
    tax_inclusive_amount        NUMERIC(18,2) NOT NULL DEFAULT 0,          -- BT-112
    prepaid_amount              NUMERIC(18,2) NOT NULL DEFAULT 0,          -- BT-113
    rounding_amount             NUMERIC(18,2) NOT NULL DEFAULT 0,          -- BT-114
    payable_amount              NUMERIC(18,2) NOT NULL DEFAULT 0,          -- BT-115

    -- payment means (BG-16, denormalised)
    payment_means_code          VARCHAR(3) CHECK (payment_means_code IS NULL OR payment_means_code IN ('1','10','30','42','48')),
    payment_means_text          VARCHAR(50),

    -- chain & signature
    invoice_counter_value       BIGINT,                                    -- KSA-16 (NOT NULL post-submit)
    previous_invoice_hash       TEXT,                                       -- KSA-13
    invoice_hash                TEXT,
    qr_code_base64              TEXT,                                       -- KSA-14
    cryptographic_stamp_value   TEXT,                                       -- KSA-15
    signed_xml_artifact_id      UUID REFERENCES invoice_artifacts(id),
    zatca_config_id             UUID REFERENCES zatca_configs(id),         -- reuses V46 config; not a new cert table
    signed_at                   TIMESTAMPTZ,

    -- authority response
    clearance_status            VARCHAR(50),
    zatca_response_data         JSONB,

    -- references
    original_invoice_id         UUID REFERENCES zatca_standard_headers(id),

    -- platform metadata
    status                      VARCHAR(40) NOT NULL DEFAULT 'DRAFT',
    version                     BIGINT NOT NULL DEFAULT 0,
    created_by                  UUID REFERENCES users(id),
    updated_by                  UUID REFERENCES users(id),
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_zatca_standard_number UNIQUE (company_id, authority_environment_id, invoice_number),
    CONSTRAINT chk_zatca_std_credit_debit_ref CHECK (
        invoice_type_code = '388' OR (issuance_reason IS NOT NULL AND billing_reference_id IS NOT NULL)
    ),
    CONSTRAINT chk_zatca_std_supply_date_tax CHECK (
        NOT (invoice_type_code = '388' AND transaction_type_code LIKE '01%') OR supply_date IS NOT NULL
    )
);
```

Simplified target schema is identical structurally except:
- `buyer_*` columns all nullable
- `cryptographic_stamp_value` and `signed_xml_artifact_id` `NOT NULL` (BR-KSA-60)
- `clearance_status` → `reporting_status`
- `chk_zatca_std_credit_debit_ref` becomes equivalent with `__sim` naming

---

## 11. DB Migration Sequence (Deferred to Pre-Launch)

> Status: not yet written. This section sketches what V58–V61 will contain. Each migration is independent and safe to roll out incrementally.

### V58 — Structural fixes (additive, non-breaking)

```sql
-- Add missing columns to both header tables
ALTER TABLE zatca_standard_headers
    ADD COLUMN business_process_code VARCHAR(40) NOT NULL DEFAULT 'reporting:1.0',
    ADD COLUMN issuance_reason       VARCHAR(127),
    ADD COLUMN billing_reference_id  VARCHAR(100),
    ADD COLUMN original_invoice_number VARCHAR(100),                       -- hybrid resolution per Sprint 1 decision
    ADD COLUMN erp_reference_id      VARCHAR(100),                          -- Sprint 1 ingestion gateway
    ADD COLUMN tax_amount_accounting_currency NUMERIC(18,2) NOT NULL DEFAULT 0,
    ADD COLUMN rounding_amount       NUMERIC(18,2) NOT NULL DEFAULT 0,
    ADD COLUMN payment_means_code    VARCHAR(3)
        CHECK (payment_means_code IS NULL OR payment_means_code IN ('1','10','30','42','48')),
    ADD COLUMN payment_means_text    VARCHAR(50);

ALTER TABLE zatca_simplified_headers
    ADD COLUMN business_process_code VARCHAR(40) NOT NULL DEFAULT 'reporting:1.0',
    ADD COLUMN issuance_reason       VARCHAR(127),
    ADD COLUMN billing_reference_id  VARCHAR(100),
    ADD COLUMN original_invoice_number VARCHAR(100),
    ADD COLUMN erp_reference_id      VARCHAR(100),
    ADD COLUMN tax_amount_accounting_currency NUMERIC(18,2) NOT NULL DEFAULT 0,
    ADD COLUMN rounding_amount       NUMERIC(18,2) NOT NULL DEFAULT 0,
    ADD COLUMN payment_means_code    VARCHAR(3)
        CHECK (payment_means_code IS NULL OR payment_means_code IN ('1','10','30','42','48')),
    ADD COLUMN payment_means_text    VARCHAR(50);

-- Backfill BT-111: equals tax_amount when currency = SAR (the common case)
UPDATE zatca_standard_headers   SET tax_amount_accounting_currency = tax_amount WHERE currency = 'SAR';
UPDATE zatca_simplified_headers SET tax_amount_accounting_currency = tax_amount WHERE currency = 'SAR';

-- zatca_uuid: type change only — keep nullable per resolved 2026-05-25 decision
-- (NULL is the correct state for DRAFT documents; the UUID is generated at submission)
ALTER TABLE zatca_standard_headers   ALTER COLUMN zatca_uuid TYPE UUID USING zatca_uuid::uuid;
ALTER TABLE zatca_simplified_headers ALTER COLUMN zatca_uuid TYPE UUID USING zatca_uuid::uuid;

-- Drop allowance_total_amount: per resolved 2026-05-25, this becomes a computed value
-- (sum of zatca_*_allowances child table). UBL serialiser computes BT-107 on read.
-- This runs AFTER V59 creates zatca_*_allowances and backfills it.
-- ALTER TABLE zatca_standard_headers   DROP COLUMN allowance_total_amount;
-- ALTER TABLE zatca_simplified_headers DROP COLUMN allowance_total_amount;

-- Tighten tax_currency
UPDATE zatca_standard_headers   SET tax_currency = 'SAR' WHERE tax_currency IS NULL;
UPDATE zatca_simplified_headers SET tax_currency = 'SAR' WHERE tax_currency IS NULL;
ALTER TABLE zatca_standard_headers   ALTER COLUMN tax_currency SET NOT NULL,
                                     ADD CONSTRAINT chk_zatca_std_tax_curr_sar CHECK (tax_currency = 'SAR');
ALTER TABLE zatca_simplified_headers ALTER COLUMN tax_currency SET NOT NULL,
                                     ADD CONSTRAINT chk_zatca_sim_tax_curr_sar CHECK (tax_currency = 'SAR');

-- Promote seller/buyer party fields out of JSONB
ALTER TABLE zatca_standard_headers
    ADD COLUMN seller_vat_number        CHAR(15),
    ADD COLUMN seller_group_vat_number  CHAR(15),
    ADD COLUMN seller_party_id          VARCHAR(50),
    ADD COLUMN seller_party_id_scheme   VARCHAR(10),
    ADD COLUMN seller_building_number   CHAR(4),
    ADD COLUMN seller_additional_number CHAR(4),
    ADD COLUMN seller_postal_code       CHAR(5),
    ADD COLUMN seller_country_code      CHAR(2) NOT NULL DEFAULT 'SA',
    ADD COLUMN buyer_vat_number         CHAR(15),
    ADD COLUMN buyer_group_vat_number   CHAR(15),
    ADD COLUMN buyer_party_id           VARCHAR(50),
    ADD COLUMN buyer_party_id_scheme    VARCHAR(10),
    ADD COLUMN buyer_building_number    CHAR(4),
    ADD COLUMN buyer_additional_number  CHAR(4),
    ADD COLUMN buyer_postal_code        CHAR(5),
    ADD COLUMN buyer_country_code       CHAR(2);

-- Backfill from existing JSONB
UPDATE zatca_standard_headers SET
    seller_vat_number        = seller_data->>'vatNumber',
    seller_party_id          = seller_data#>>'{partyIdentification,id}',
    seller_party_id_scheme   = seller_data#>>'{partyIdentification,scheme}',
    seller_building_number   = seller_data->>'buildingNumber',
    seller_additional_number = seller_data->>'additionalNumber',
    seller_postal_code       = seller_data->>'postalZone',
    seller_country_code      = COALESCE(seller_data->>'countryCode', 'SA'),
    buyer_vat_number         = buyer_data->>'vatNumber',
    buyer_party_id           = buyer_data#>>'{partyIdentification,id}',
    buyer_party_id_scheme    = buyer_data#>>'{partyIdentification,scheme}',
    buyer_building_number    = buyer_data->>'buildingNumber',
    buyer_additional_number  = buyer_data->>'additionalNumber',
    buyer_postal_code        = buyer_data->>'postalZone',
    buyer_country_code       = buyer_data->>'countryCode';
-- Repeat for zatca_simplified_headers.

-- Add CHECKs only after backfill verified (Option B: defer to V58a to allow data cleanup)
-- ALTER TABLE zatca_standard_headers ADD CONSTRAINT chk_zatca_std_seller_vat
--     CHECK (seller_vat_number IS NULL OR seller_vat_number ~ '^3[0-9]{13}3$');
-- ...
```

### V59 — New header sub-tables

- `zatca_standard_tax_subtotals` + `zatca_simplified_tax_subtotals`
- `zatca_standard_allowances` + `zatca_simplified_allowances`

### V60 — Line-level UBL price block + line allowance child

- Add BT-146, BT-147, BT-148, BT-149, BT-150 columns to both `*_lines` tables
- Add `vat_inclusive_amount` (KSA-12)
- Create `zatca_standard_line_allowances` + `zatca_simplified_line_allowances`
- Backfill from `discount_amount` + `allowance_amount`, drop the flat columns after verification

### V61 — Signature artifacts

- Add `signed_xml_artifact_id`, `signing_certificate_id`, `cryptographic_stamp_value`, `signed_at` columns
- Make them `NOT NULL` on `zatca_simplified_headers` only after backfill of accepted invoices
- Create `zatca_signing_certificates` if not already present from Wave 8 work

---

## 12. Field Mapping Reference Card

Quick lookup for the UBL XML serialiser:

| UBL XPath | Spec ID | DB Column |
|---|---|---|
| `/Invoice/cbc:ID` | BT-1 | `invoice_number` |
| `/Invoice/cbc:UUID` | KSA-1 | `zatca_uuid` |
| `/Invoice/cbc:IssueDate` | BT-2 | `issue_date` |
| `/Invoice/cbc:IssueTime` | KSA-25 | `issue_time` |
| `/Invoice/cbc:InvoiceTypeCode` | BT-3 | `invoice_type_code` |
| `/Invoice/cbc:InvoiceTypeCode/@name` | KSA-2 | `transaction_type_code` |
| `/Invoice/cbc:Note` | KSA-10 | `issuance_reason` |
| `/Invoice/cbc:DocumentCurrencyCode` | BT-5 | `currency` |
| `/Invoice/cbc:TaxCurrencyCode` | BT-6 | `tax_currency` |
| `/Invoice/cac:OrderReference/cbc:ID` | BT-13 | (not stored) |
| `/Invoice/cac:BillingReference/cac:InvoiceDocumentReference/cbc:ID` | BT-25 | `billing_reference_id` |
| `/Invoice/cac:AdditionalDocumentReference[cbc:ID='ICV']/cbc:UUID` | KSA-16 | `invoice_counter_value` |
| `/Invoice/cac:AdditionalDocumentReference[cbc:ID='PIH']/cac:Attachment/cbc:EmbeddedDocumentBinaryObject` | KSA-13 | `previous_invoice_hash` |
| `/Invoice/cac:AdditionalDocumentReference[cbc:ID='QR']/cac:Attachment/cbc:EmbeddedDocumentBinaryObject` | KSA-14 | `qr_code_base64` |
| `/Invoice/cac:AccountingSupplierParty//cbc:CompanyID` | BT-31 | `seller_vat_number` |
| `/Invoice/cac:AccountingSupplierParty//cac:PartyIdentification/cbc:ID` | BT-29 | `seller_party_id` |
| `/Invoice/cac:AccountingSupplierParty//cac:PartyIdentification/cbc:ID/@schemeID` | BT-29-1 | `seller_party_id_scheme` |
| `/Invoice/cac:AccountingSupplierParty//cac:PostalAddress/cbc:BuildingNumber` | KSA-17 | `seller_building_number` |
| `/Invoice/cac:AccountingSupplierParty//cac:PostalAddress/cbc:PlotIdentification` | KSA-23 | `seller_additional_number` |
| `/Invoice/cac:AccountingSupplierParty//cac:PostalAddress/cbc:PostalZone` | BT-38 | `seller_postal_code` |
| `/Invoice/cac:AccountingSupplierParty//cac:Country/cbc:IdentificationCode` | BT-40 | `seller_country_code` |
| `/Invoice/cac:AccountingCustomerParty//cbc:CompanyID` | BT-48 | `buyer_vat_number` |
| `/Invoice/cac:PaymentMeans/cbc:PaymentMeansCode` | BT-81 | `payment_means_code` |
| `/Invoice/cac:AllowanceCharge[cbc:ChargeIndicator='false']/*` | BG-20 | `zatca_*_allowances` row |
| `/Invoice/cac:TaxTotal/cbc:TaxAmount` | BT-110 | `tax_amount` |
| `/Invoice/cac:TaxTotal/cbc:TaxAmount[@currencyID='SAR']` (second) | BT-111 | `tax_amount_accounting_currency` |
| `/Invoice/cac:TaxTotal/cac:TaxSubtotal/*` | BG-23 | `zatca_*_tax_subtotals` row |
| `/Invoice/cac:LegalMonetaryTotal/cbc:LineExtensionAmount` | BT-106 | `line_extension_amount` |
| `/Invoice/cac:LegalMonetaryTotal/cbc:AllowanceTotalAmount` | BT-107 | `allowance_total_amount` |
| `/Invoice/cac:LegalMonetaryTotal/cbc:TaxExclusiveAmount` | BT-109 | `tax_exclusive_amount` |
| `/Invoice/cac:LegalMonetaryTotal/cbc:TaxInclusiveAmount` | BT-112 | `tax_inclusive_amount` |
| `/Invoice/cac:LegalMonetaryTotal/cbc:PrepaidAmount` | BT-113 | `prepaid_amount` |
| `/Invoice/cac:LegalMonetaryTotal/cbc:PayableRoundingAmount` | BT-114 | `rounding_amount` |
| `/Invoice/cac:LegalMonetaryTotal/cbc:PayableAmount` | BT-115 | `payable_amount` |
| `/Invoice/cac:InvoiceLine/cbc:ID` | BT-126 | `line_number` |
| `/Invoice/cac:InvoiceLine/cbc:InvoicedQuantity` | BT-129 | `quantity` |
| `/Invoice/cac:InvoiceLine/cbc:LineExtensionAmount` | BT-131 | `line_extension_amount` (line table) |
| `/Invoice/cac:InvoiceLine/cac:Price/cbc:PriceAmount` | BT-146 | `item_net_price` |
| `/Invoice/cac:InvoiceLine/cac:Price/cbc:BaseQuantity` | BT-149 | `item_price_base_quantity` |
| `/Invoice/cac:InvoiceLine/cac:Price/cac:AllowanceCharge/cbc:Amount` | BT-147 | `item_price_discount` |
| `/Invoice/cac:InvoiceLine/cac:Price/cac:AllowanceCharge/cbc:BaseAmount` | BT-148 | `item_gross_price` |
| `/Invoice/cac:InvoiceLine/cac:TaxTotal/cbc:TaxAmount` | KSA-11 | `vat_amount` (line table) |
| `/Invoice/cac:InvoiceLine/cac:TaxTotal/cbc:RoundingAmount` | KSA-12 | `vat_inclusive_amount` |
| `/Invoice/cac:InvoiceLine/cac:Item/cac:ClassifiedTaxCategory/cbc:ID` | BT-151 | `vat_category_code` |
| `/Invoice/cac:InvoiceLine/cac:Item/cac:ClassifiedTaxCategory/cbc:Percent` | BT-152 | `vat_rate` |
| `/Invoice/cac:UBLExtensions//ds:SignatureValue` | KSA-15 | `cryptographic_stamp_value` |

---

## 13. Verification Plan

Once V58–V61 are applied, the schema must be verified by round-tripping a real invoice through the Fatoora CLI:

1. **Serialise** a `zatca_standard_headers` row + its children to UBL XML using the field-mapping table above.
2. **Validate** with no-signature mode:
   ```
   fatoora -invoice generated.xml -validate
   ```
3. Iterate until **zero `BR-KSA-*` errors** reported.
4. **Sign**:
   ```
   fatoora -invoice generated.xml -sign -signedInvoice signed.xml
   ```
5. **Generate QR**:
   ```
   fatoora -invoice signed.xml -qr
   ```
6. **Generate API request**:
   ```
   fatoora -invoice signed.xml -invoiceRequest -apiRequest request.json
   ```
7. Diff `request.json` against what the platform's submission engine produces — they must be byte-equivalent modulo whitespace.

This loop is the only reliable acceptance test. Anything less than fatoora-clean output should not be considered "done".

---

## 14. Resolved Decisions (2026-05-25)

All open questions are resolved. Implementation proceeds on these locked assumptions.

1. **Signing certificate storage** — **Resolved: reuse `zatca_configs` (V46).** That table already holds `private_key`, `compliance_certificate`, `production_certificate`, `device_uuid`, `csr` per Principle XVIII (Phase-1 plain-text policy). New header column is `zatca_config_id UUID REFERENCES zatca_configs(id)`. No new `zatca_signing_certificates` table.

2. **`zatca_uuid` nullability** — **Resolved: keep nullable.** Only the column *type* changes (`VARCHAR(255)` → `UUID`). NULL is correct for DRAFT rows; the UUID is generated at submission. No backfill needed.

3. **`unit_price` rename** — **Resolved: rename `unit_price` → `item_net_price`** on both `zatca_standard_lines` and `zatca_simplified_lines`. Add new columns BT-148 (`item_gross_price`), BT-147 (`item_price_discount`), BT-149 (`item_price_base_quantity`), BT-150 (`item_price_base_quantity_unit`). Wave 8 entity code is renamed in the same PR. Backfill `item_price_base_quantity = 1` for existing rows.

4. **Allowance aggregate** — **Resolved: drop `allowance_total_amount` entirely.** BT-107 is computed on read from `zatca_*_allowances` rows. No DB trigger, no cached aggregate. The UBL serialiser SUMs the child table at document-generation time. Removes drift risk and eliminates the sync mechanism.

5. **`tax_amount_accounting_currency`** — **Resolved: always populate.** Column is `NOT NULL DEFAULT 0`. Equals `tax_amount` when document `currency = 'SAR'`. Simpler service code, validator-clean for both single-currency and multi-currency cases. Eliminates a nullability branch in the serialiser.

6. **Payment means cardinality** — **Resolved: single denormalised columns on header.** `payment_means_code VARCHAR(3)` + `payment_means_text VARCHAR(50)` with a CHECK on the BR-KSA-16 enum. No child table. If multi-method invoices become a real requirement later, migrate then.

7. **Wave 8 entity impact** — **Tracking requirement, not a design question.** Each migration (V58–V61) is paired with a documented set of Wave 8 code paths to update, captured in `specs/010-authority-spec-alignment/tasks.md` (per-task scope). Migrations and entity changes ship together in the same PR.

### Additional decisions resolved during the same review

8. **`clearance_status` vs `reporting_status` naming** — **Keep separate names.** ZATCA itself distinguishes clearance (Standard) from reporting (Simplified). The columns reflect a real domain distinction. No unification.

9. **Party field promotion** — **Promote only the schematron-validated structural fields** (VAT number with 15-digit CHECK, postal code 5-digit CHECK, additional/building number 4-digit CHECK, country code = 'SA', party ID + scheme). Name, street, neighborhood, and city stay in `seller_data`/`buyer_data` JSONB.

10. **`original_invoice_number` storage** — **Add column on both header tables** (per Sprint 1 hybrid-resolution decision). Service writes the raw number string always; populates the existing `original_invoice_id` FK only when the referenced row is found in the DB.

---

*End of ZATCA Spec Alignment Investigation*
