# Contract — V58–V61 Migration File Sequence

## Overview

This feature's external contract is **the on-disk Flyway migration files** plus the [`deferred-validation.md`](../deferred-validation.md) catalogue and two manual verification scripts. There are no new HTTP endpoints, no new authority APIs, and no new permission scopes (per FR-023).

Each migration is a separate PR (per Clarifications 2026-05-26). The PR boundary IS the contract — each migration's SQL file plus the paired Java entity/serialiser changes MUST ship together; the prior PR MUST be verified on staging before the next opens.

---

## File: `backend/src/main/resources/db/migration/V58__zatca_eta_header_additions.sql`

**Purpose:** Header-level additive changes for ZATCA Standard, ZATCA Simplified, and ETA Receipt; party-field promotion from JSONB.

**Contract:**

1. Idempotent at the Flyway level (Flyway tracks application state via `flyway_schema_history`).
2. Non-destructive for existing rows. Every `ADD COLUMN` is either nullable or uses a `DEFAULT` consistent with existing data semantics (e.g., `DEFAULT 0` for `tax_amount_accounting_currency`, `DEFAULT 'reporting:1.0'` for `business_process_code`, `DEFAULT 'SA'` for `seller_country_code`).
3. Includes the in-place rename `eta_receipt_headers.total_discount_amount → total_commercial_discount` (locked decision; pre-prod, no compat window).
4. Adds the `erp_reference_id` and `original_invoice_number` columns to **all four** header tables (`eta_invoice_headers`, `eta_receipt_headers`, `zatca_standard_headers`, `zatca_simplified_headers`) per FR-017.
5. Promotes seller/buyer party fields from JSONB into typed columns on the two ZATCA header tables; performs the backfill `UPDATE` in the same migration.
6. Creates two indexes (`idx_zatca_std_seller_vat`, `idx_zatca_sim_seller_vat`) to back SC-006.
7. Backfill statements emit `RAISE NOTICE` for any row whose backfilled value would violate a deferred rule from [`deferred-validation.md`](../deferred-validation.md) (e.g., `seller_vat_number` not matching `^3[0-9]{13}3$`).
8. **No `CHECK` / `UNIQUE` / `FK` / `NOT NULL`** added (per smart-app/dumb-DB).
9. **No type change** for `zatca_uuid` (FR-012 abandoned in 010).
10. Estimated runtime < 30 s on staging-shaped data (SC-004).
11. The paired PR contains entity-field additions in `ZatcaStandardHeader`, `ZatcaSimplifiedHeader`, `EtaReceiptHeader`, plus the renamed `EtaReceiptHeader.totalCommercialDiscount`. Serialisers updated: `ZatcaUblSerialiser` emits `cbc:ProfileID`, `cbc:Note`, `cac:PaymentMeans`, the second `cac:TaxTotal/cbc:TaxAmount` (BT-111), `cbc:PayableRoundingAmount`; `EtaReceiptSerialiser` nests fields under `header {}` and moves POS/activity fields into `seller`.

---

## File: `backend/src/main/resources/db/migration/V59__zatca_subtotals_and_allowances.sql`

**Purpose:** Create the new ZATCA header sub-tables required by the schematron, backfill them from existing line data, then drop the now-redundant `allowance_total_amount` scalar.

**Contract:**

1. Creates four new tables, each with `PRIMARY KEY id UUID DEFAULT gen_random_uuid()` and a `created_at TIMESTAMPTZ DEFAULT NOW()`:
   - `zatca_standard_tax_subtotals`
   - `zatca_simplified_tax_subtotals`
   - `zatca_standard_allowances`
   - `zatca_simplified_allowances`
2. Creates four indexes on `header_id` (sub-table → header join).
3. **No `FOREIGN KEY`** from sub-table to parent header (deferred — `deferred-validation.md` §V59.B). The `header_id` is a plain `UUID` column with an inline `-- VALIDATION (deferred): ... see deferred-validation.md §V59.B.N` comment.
4. **No `UNIQUE`** on the natural keys (deferred — §V59.A). Inline comment per table.
5. **No `CHECK`** on `vat_category_code`, `percentage` range, or the cross-column exemption rule (deferred — §V59.C).
6. **No `NOT NULL`** on any column except via column DEFAULT (deferred — §V59.D).
7. Backfills:
   - `zatca_standard_tax_subtotals` from `zatca_standard_lines GROUP BY (header_id, vat_category_code, vat_rate)`.
   - `zatca_standard_allowances` synthesises one row per header where `allowance_total_amount > 0` (with `reason = 'migrated-from-aggregate'`, `vat_category_code = 'S'`, `vat_rate = 15.00`, `sequence = 1`).
   - Mirror for Simplified.
8. After backfill verified within the same migration: `ALTER TABLE zatca_standard_headers DROP COLUMN allowance_total_amount;` (and the Simplified mirror). Per FR-003, this DROP runs only after the prior INSERT executed successfully.
9. The paired PR adds `ZatcaStandardTaxSubtotal`, `ZatcaSimplifiedTaxSubtotal`, `ZatcaStandardAllowance`, `ZatcaSimplifiedAllowance` entities; wires them via `@OneToMany(mappedBy = "header", cascade = ALL, orphanRemoval = true)` on the headers; updates `ZatcaUblSerialiser` to emit `cac:TaxTotal/cac:TaxSubtotal` blocks (per BG-23) and document-level `cac:AllowanceCharge` blocks (per BG-20); replaces the `allowanceTotal` stored field with a derived getter that sums the child table.

---

## File: `backend/src/main/resources/db/migration/V60__line_block_and_allowances.sql`

**Purpose:** Add the UBL line-price block (BT-146..150 + KSA-12) to ZATCA lines, create the line-allowance child table, and execute the ETA Receipt line restructure (drop `unit_value` JSONB, add `unit_price` scalar, replace flat discounts with JSONB arrays).

**Contract:**

1. Adds six columns to each of `zatca_standard_lines` and `zatca_simplified_lines` (`item_gross_price`, `item_price_discount`, `item_price_base_quantity` with `DEFAULT 1`, `item_price_base_quantity_unit`, `vat_inclusive_amount`).
2. Renames `unit_price → item_net_price` on both tables via the add+backfill+drop pattern (net effect = rename; mechanical because Postgres allows in-place RENAME but the explicit pattern is safer when paired with the JPA field rename).
3. Backfills `vat_inclusive_amount = line_extension_amount + vat_amount` on both line tables.
4. Creates `zatca_standard_line_allowances` and `zatca_simplified_line_allowances` (`PRIMARY KEY` only, `created_at DEFAULT NOW()`, index on `line_id`).
5. Backfills line-allowances from `discount_amount + allowance_amount`; then drops both flat columns from both line tables.
6. For `eta_receipt_lines`:
   - Adds `unit_price NUMERIC(18,5)` and backfills currency-aware (per spec.md Edge Case).
   - Adds `commercial_discount_data JSONB DEFAULT '[]'` and `item_discount_data JSONB DEFAULT '[]'`; migrates flat values into the arrays.
   - Drops `unit_value`, `discount_rate`, `discount_amount`, `items_discount`.
6a. Before the `unit_value` drop, V60 also backfills `eta_receipt_headers.exchange_rate` from the first non-null `unit_value->>'currencyExchangeRate'` per header (V58 added the column with `DEFAULT NULL` but deferred the backfill to V60 because the source JSONB lives on lines that V60 itself drops). Multiple-line disagreement emits `RAISE NOTICE` per spec.md Edge Case.
7. No `CHECK` / `UNIQUE` / `FK` / `NOT NULL` (deferred — see `deferred-validation.md` §V60).
8. The paired PR adds `ZatcaStandardLineAllowance` and `ZatcaSimplifiedLineAllowance` entities; renames `ZatcaStandardLine.unitPrice → itemNetPrice` (and mirror for Simplified); adds BT-148..150 + KSA-12 fields; modifies `EtaReceiptLine` to drop `unitValue` and add the scalar + JSONB array fields. `EtaReceiptSerialiser` updated to emit `unitPrice` + `commercialDiscountData` + `itemDiscountData` per the v1.2 SDK §10 sample.

---

## File: `backend/src/main/resources/db/migration/V61__signature_artifacts.sql`

**Purpose:** Promote Phase-2 signature artifacts out of `zatca_response_data` JSONB into first-class columns, link to existing `zatca_configs` (V46) semantically, and execute the best-effort backfill agreed in Clarifications session 2026-05-26.

**Contract:**

1. Adds four columns to each of `zatca_standard_headers` and `zatca_simplified_headers`:
   - `cryptographic_stamp_value TEXT` (KSA-15 ECDSA signature value)
   - `signed_xml_artifact_id UUID` (no FK — deferred — §V61.A.2)
   - `zatca_config_id UUID` (no FK — deferred — §V61.A.1)
   - `signed_at TIMESTAMPTZ`
   All nullable.
2. **No `FOREIGN KEY`** — neither column carries a DB FK. The columns are semantically links to `invoice_artifacts(id)` and `zatca_configs(id)` but referential integrity is enforced at the service layer.
3. The SQL file includes the **mandatory inline comment** documenting the nullability rationale for `zatca_config_id`:
   ```sql
   -- NOTE: zatca_config_id is intentionally NULLABLE and not FK-enforced.
   -- Pre-V46 Wave 7/8 rows may have submitted documents whose original signing
   -- configuration cannot be reconstructed (best-effort backfill below may
   -- return no match). See specs/010 Clarifications session 2026-05-26 and
   -- deferred-validation.md §V61.A for the decision rationale.
   ```
4. Backfill from `zatca_response_data` JSONB where signature artifacts are present (`cryptographic_stamp_value` ← `signatureValue`, `signed_at` ← `signedAt`).
5. Best-effort two-pass `zatca_config_id` backfill (active → historic → NULL + `RAISE NOTICE`).
6. The paired PR adds the four fields to both header entities; updates `ZatcaSigningService` (or the equivalent submission orchestrator) to persist them at submission time; adds the state-machine guard for `ZatcaSimplifiedHeader` (BR-KSA-60: `cryptographic_stamp_value` MUST be non-null when status transitions to submitted/reported).

---

## Deferred-Validation Contract

[`specs/010-authority-spec-alignment/deferred-validation.md`](../deferred-validation.md) is part of this feature's external contract. The downstream "constraint layer" spec consumes it as a backlog.

- Each rule is identified by its section number (e.g., `§V58.A.1`, `§V59.B.3`).
- Sections MUST NOT be renumbered after this feature merges.
- Future enforcement work MAY mark resolved rules as "Resolved — promoted to DB in V<N>" but MUST NOT delete them; preserve the history.

---

## Manual Verification Script Contracts

### `scripts/verify/fatoora-validate.ps1`

**Purpose:** Drive `fatoora -invoice <xml> -validate` over a developer-supplied sample XML.

**Contract:**

- **Input:** path to a UBL XML file serialised from a Wave-8 DB row (via the platform's `ZatcaUblSerialiser`).
- **Output:** stdout + a sibling `.log` file in the script's invocation directory.
- **Exit code:** `0` on zero `BR-KSA-*` errors; non-zero otherwise.
- **Prerequisites:** Fatoora SDK v2.0.3 installed locally; `fatoora` on PATH.
- **Required test coverage** (developer runs once per canonical case before release):
  - Standard with single S-rated line
  - Standard with E-exempt line + exemption reason
  - Standard credit note with original-invoice reference
  - Standard with multi-currency totals
  - Simplified with cryptographic stamp (BR-KSA-60)

### `scripts/verify/eta-receipt-submit.ps1`

**Purpose:** POST an ETA Receipt v1.2 sample JSON to ETA pre-production.

**Contract:**

- **Input:** path to a sample JSON file matching the v1.2 SDK shape ([`Docs/eta-receipt-sdk-v1-2-alignment.md`](../../../Docs/eta-receipt-sdk-v1-2-alignment.md) §10).
- **Output:** stdout + a sibling `.log` capturing the full HTTP response.
- **Exit code:** `0` on HTTP 2xx (structural acceptance, per SC-003); non-zero on a 4xx response whose body indicates a structural rejection ("unrecognised field" / "missing required field"). A 4xx with a business-content rejection (e.g., "invalid tax number") is treated as a successful structural validation — exit `0`.
- **Prerequisites:** ETA pre-production credentials in env (`ETA_PREPROD_CLIENT_ID`, `ETA_PREPROD_CLIENT_SECRET`).

Both scripts MUST be runnable on the developer's machine without CI infrastructure. The contract is "the script exists, is documented in quickstart.md, and produces the documented output."
