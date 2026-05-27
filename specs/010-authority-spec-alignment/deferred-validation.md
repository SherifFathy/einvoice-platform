# Deferred Validation Rules — Feature 010 Authority Spec Alignment

**Status:** Locked 2026-05-26 by Clarifications session in `spec.md`.
**Scope:** Catalogue of every data-shape / referential / cardinality rule that the V58–V61 migrations **deliberately do not enforce at the DB layer**.
**Purpose:** Source of truth for a future downstream spec ("constraint layer" / service-layer validation feature) to pick up and implement.

---

## 1. Why this file exists

V58–V61 follow a smart-application / dumb-database pattern. They create **only**:

- `ALTER TABLE ADD/DROP/RENAME COLUMN` (column type and `DEFAULT` clauses only)
- `CREATE TABLE` with `PRIMARY KEY` only
- `CREATE INDEX` (to back SC-006's <100 ms query goal)
- `UPDATE` backfills
- `RAISE NOTICE` for log emission (per FR-021)

No new `CHECK`, `UNIQUE`, `FOREIGN KEY`, or `NOT NULL` is created. The `zatca_uuid VARCHAR(255) → UUID` type change is abandoned (column stays `VARCHAR(255)`).

Every rule that would have lived on a column is recorded here with:

| Field | Meaning |
|---|---|
| **Rule** | The constraint as it would have been written in SQL |
| **Type** | `CHECK` / `UNIQUE` / `FK` / `NOT NULL` / `TYPE` |
| **Enforce at** | Recommended enforcement location (Bean Validation annotation on DTO/entity, service-layer assertion, class-level `@AssertTrue`, Java enum) |
| **Reference** | Authority rule ID (BR-KSA-*, BT-*, KSA-*) or `spec.md` FR cross-link |
| **Risk** | What can go wrong if not enforced (HIGH / MEDIUM / LOW) |

### Migration-script convention

Each V58–V61 SQL file MUST carry an inline comment beside every affected column referencing the relevant section of this file, e.g.:

```sql
ALTER TABLE zatca_standard_headers
    ADD COLUMN seller_vat_number CHAR(15);  -- VALIDATION (deferred): seller_vat_number ~ '^3[0-9]{13}3$' — see deferred-validation.md §V58.A (BR-KSA-40)
```

---

## V58 — Header-level structural additions

### §V58.A — Promoted party-field shape (CHECK, deferred)

| # | Column | Rule | Type | Enforce at | Reference | Risk |
|---|---|---|---|---|---|---|
| 1 | `zatca_*_headers.seller_vat_number` | matches `^3[0-9]{13}3$` | CHECK | Bean Validation `@Pattern` on entity field | BR-KSA-40, FR-006 | HIGH |
| 2 | `zatca_*_headers.seller_group_vat_number` | matches `^3[0-9]{9}1[0-9]{3}3$` (11th digit = `1`) | CHECK | Bean Validation `@Pattern` | BR-KSA-41 | MEDIUM |
| 3 | `zatca_*_headers.seller_building_number` | matches `^[0-9]{4}$` | CHECK | Bean Validation `@Pattern` | BR-KSA-37 | MEDIUM |
| 4 | `zatca_*_headers.seller_additional_number` | matches `^[0-9]{4}$` | CHECK | Bean Validation `@Pattern` | BR-KSA-64 | MEDIUM |
| 5 | `zatca_*_headers.seller_postal_code` | matches `^[0-9]{5}$` | CHECK | Bean Validation `@Pattern` | BR-KSA-66 | MEDIUM |
| 6 | `zatca_standard_headers.seller_country_code` | `= 'SA'` (Standard only) | CHECK | Service-layer assertion | BR-KSA-38 | HIGH |
| 7 | `zatca_*_headers.seller_party_id_scheme` | IN ('CRN','MOM','MLS','SAG','OTH') | CHECK | Java enum + Bean Validation `@Pattern` | BR-KSA-08 | MEDIUM |
| 8 | `zatca_*_headers.buyer_vat_number` | matches `^3[0-9]{13}3$` when present | CHECK | Bean Validation `@Pattern` (conditional) | BR-KSA-44 | HIGH |
| 9 | `zatca_*_headers.buyer_group_vat_number` | matches `^3[0-9]{9}1[0-9]{3}3$` | CHECK | Bean Validation `@Pattern` | BR-KSA-45 | MEDIUM |
| 10 | `zatca_*_headers.buyer_postal_code` | matches `^[0-9]{5}$` (SA buyers) | CHECK | Conditional service-layer assertion (when `buyer_country_code = 'SA'`) | BR-KSA-67 | LOW |
| 11 | `zatca_*_headers.buyer_additional_number` | matches `^[0-9]{4}$` (SA buyers) | CHECK | Conditional service-layer assertion | BR-KSA-63 | LOW |
| 12 | `zatca_*_headers.buyer_party_id_scheme` | IN ('NAT','IQA','PAS','CRN','MOM','MLS','SAG','GCC','OTH') | CHECK | Java enum + Bean Validation | BR-KSA-14 | MEDIUM |

### §V58.B — Document-type enums (CHECK, deferred)

| # | Column | Rule | Type | Enforce at | Reference | Risk |
|---|---|---|---|---|---|---|
| 1 | `zatca_*_headers.invoice_type_code` | IN ('388','381','383') | CHECK | Java enum | BR-KSA-05 | HIGH |
| 2 | `zatca_*_headers.transaction_type_code` | matches `^[01]{7}$` | CHECK | Bean Validation `@Pattern` | BR-KSA-06 | HIGH |
| 3 | `zatca_*_headers.payment_means_code` | IN ('1','10','30','42','48') | CHECK | Java enum | BR-KSA-16, FR-005 | MEDIUM |
| 4 | `zatca_*_headers.tax_currency` | `= 'SAR'` | CHECK | Service-layer assertion at write | BR-KSA-68 | HIGH |

### §V58.C — Cross-column rules (CHECK, deferred)

| # | Rule | Type | Enforce at | Reference | Risk |
|---|---|---|---|---|---|
| 1 | `supply_end_date IS NULL OR supply_end_date > supply_date` | CHECK (cross-column) | Class-level `@AssertTrue` validator method on entity | KSA-35 / KSA-36 | LOW |
| 2 | If `invoice_type_code IN ('381','383')`: `issuance_reason IS NOT NULL AND billing_reference_id IS NOT NULL` | CHECK (cross-column) | Service-layer assertion before persist | BR-KSA-17, BR-KSA-56 | HIGH |
| 3 | If `invoice_type_code = '388' AND transaction_type_code LIKE '01%'`: `supply_date IS NOT NULL` | CHECK (cross-column) | Service-layer assertion before persist | BR-KSA-15 | HIGH |

### §V58.D — NOT NULL clauses (deferred; `DEFAULT` retained on the column)

| # | Column | Default kept on column | Service-layer responsibility |
|---|---|---|---|
| 1 | `zatca_*_headers.business_process_code` | `'reporting:1.0'` | Service MAY rely on the DB default; populate explicitly when overriding |
| 2 | `zatca_*_headers.tax_amount_accounting_currency` | `0` | Service MUST set = `tax_amount` when `currency = 'SAR'`; correctly populated for non-SAR |
| 3 | `zatca_*_headers.rounding_amount` | `0` | Service owns rounding logic |
| 4 | `zatca_standard_headers.seller_country_code` | `'SA'` | Service ensures it stays `'SA'` on Standard |
| 5 | `eta_receipt_headers.fees_amount` | `0` | Service ensures non-negative |
| 6 | `eta_receipt_headers.adjustment` | `0` | Service ensures non-negative |
| 7 | `eta_receipt_lines.commercial_discount_data` | `'[]'` | Service treats NULL as `[]` defensively when reading |
| 8 | `eta_receipt_lines.item_discount_data` | `'[]'` | (same) |

### §V58.E — Deferred type change

| # | Column | Rule | Type | Enforce at | Reference |
|---|---|---|---|---|---|
| 1 | `zatca_*_headers.zatca_uuid` | Stays `VARCHAR(255)`; type change to `UUID` abandoned in 010. App MUST emit a UUID-shaped string matching `^[0-9a-fA-F-]{36}$` at submission time. | TYPE + CHECK | Bean Validation `@Pattern` on entity + serialiser-side format check | KSA-1, BR-KSA-03 | HIGH |

---

## V59 — ZATCA sub-tables (created with `PRIMARY KEY` only)

### §V59.A — UNIQUE on natural keys (deferred)

| # | Table | Rule | Type | Enforce at | Risk |
|---|---|---|---|---|---|
| 1 | `zatca_standard_tax_subtotals` / `zatca_simplified_tax_subtotals` | UNIQUE `(header_id, vat_category_code, vat_rate)` | UNIQUE | Service layer MUST prevent duplicate `(category, rate)` per header before INSERT; UBL serialiser MUST also dedupe defensively before emission | **HIGH** — duplicates produce two `cac:TaxTotal/cac:TaxSubtotal` blocks for the same category, causing BR-KSA-EN16931-08 rejection at Fatoora |
| 2 | `zatca_standard_allowances` / `zatca_simplified_allowances` | UNIQUE `(header_id, sequence)` | UNIQUE | Service layer MUST assign sequential `sequence` values per header | MEDIUM — duplicates corrupt UBL allowance ordering |

### §V59.B — FK from sub-tables to parent header (deferred)

| # | Column | Rule | Type | Enforce at | Risk |
|---|---|---|---|---|---|
| 1 | `zatca_standard_tax_subtotals.header_id` | FK `→ zatca_standard_headers(id) ON DELETE CASCADE` | FK | Service-layer assertion on INSERT (parent header MUST exist); cascade-delete implemented in service-layer header-delete path | **HIGH** — orphan rows are silent and survive header deletes |
| 2 | `zatca_simplified_tax_subtotals.header_id` | FK `→ zatca_simplified_headers(id) ON DELETE CASCADE` | FK | (same) | HIGH |
| 3 | `zatca_standard_allowances.header_id` | FK `→ zatca_standard_headers(id) ON DELETE CASCADE` | FK | (same) | HIGH |
| 4 | `zatca_simplified_allowances.header_id` | FK `→ zatca_simplified_headers(id) ON DELETE CASCADE` | FK | (same) | HIGH |

### §V59.C — CHECK on sub-tables (deferred)

| # | Column | Rule | Type | Enforce at | Reference |
|---|---|---|---|---|---|
| 1 | `tax_subtotals.vat_category_code` | IN ('S','Z','E','O') | CHECK | Java enum | BG-23 |
| 2 | `tax_subtotals` (cross-column) | `(vat_category_code IN ('E','O','Z') AND exemption_reason_code IS NOT NULL AND exemption_reason_text IS NOT NULL) OR vat_category_code = 'S'` | CHECK | Class-level `@AssertTrue` on entity OR service-layer assertion | BR-KSA-CL-04, BR-KSA-CL-05 |
| 3 | `allowances.vat_category_code` | IN ('S','Z','E','O') | CHECK | Java enum | BG-20 |
| 4 | `allowances.percentage` | BETWEEN 0 AND 100 | CHECK | Bean Validation `@DecimalMin("0")` + `@DecimalMax("100")` | BT-94 |

### §V59.D — NOT NULL on sub-tables (deferred)

| # | Column | Rule | Enforce at |
|---|---|---|---|
| 1 | `tax_subtotals.taxable_amount` | NOT NULL | Bean Validation `@NotNull` on entity |
| 2 | `tax_subtotals.tax_amount` | NOT NULL | Bean Validation `@NotNull` |
| 3 | `tax_subtotals.vat_category_code` | NOT NULL | Bean Validation `@NotNull` |
| 4 | `allowances.amount` | NOT NULL | Bean Validation `@NotNull` |
| 5 | `allowances.vat_category_code` | NOT NULL | Bean Validation `@NotNull` |
| 6 | `allowances.sequence` | NOT NULL | Bean Validation `@NotNull` |

---

## V60 — Line-level changes

### §V60.A — UNIQUE on line-allowance natural key (deferred)

| # | Table | Rule | Type | Enforce at | Risk |
|---|---|---|---|---|---|
| 1 | `zatca_*_line_allowances` | UNIQUE `(line_id, sequence)` | UNIQUE | Service layer manages sequence per line | MEDIUM |

### §V60.B — FK from line-allowances to parent line (deferred)

| # | Column | Rule | Type | Enforce at | Risk |
|---|---|---|---|---|---|
| 1 | `zatca_standard_line_allowances.line_id` | FK `→ zatca_standard_lines(id) ON DELETE CASCADE` | FK | Service-layer cascade on line removal | HIGH |
| 2 | `zatca_simplified_line_allowances.line_id` | FK `→ zatca_simplified_lines(id) ON DELETE CASCADE` | FK | (same) | HIGH |

### §V60.C — CHECK and NOT NULL on lines (deferred)

| # | Column | Rule | Type | Enforce at | Reference |
|---|---|---|---|---|---|
| 1 | `zatca_*_lines.item_price_base_quantity` | `> 0` AND `NOT NULL`; DB `DEFAULT 1` kept | CHECK + NOT NULL | Service-layer assertion (DB default covers backfilled rows) | BR-KSA-EN16931-12, BT-149 |
| 2 | `zatca_*_lines.vat_inclusive_amount` | `NOT NULL` (= `line_extension_amount + vat_amount`) | NOT NULL | Service computes and asserts before persist | KSA-12, BR-KSA-51, BR-KSA-53 |
| 3 | `zatca_*_line_allowances.amount` | NOT NULL | NOT NULL | Bean Validation `@NotNull` | BT-136 |
| 4 | `zatca_*_line_allowances.percentage` | BETWEEN 0 AND 100 | CHECK | Bean Validation `@DecimalMin("0")` + `@DecimalMax("100")` | BT-138 |
| 5 | `zatca_*_line_allowances.sequence` | NOT NULL | NOT NULL | Bean Validation `@NotNull` | — |

---

## V61 — Signature artifacts

### §V61.A — FK to existing tables (deferred)

| # | Column | Rule | Type | Enforce at | Risk |
|---|---|---|---|---|---|
| 1 | `zatca_*_headers.zatca_config_id` | FK `→ zatca_configs(id)` | FK | Service-layer lookup + assertion at submission time | MEDIUM — orphan reference possible if `zatca_configs` row deleted |
| 2 | `zatca_*_headers.signed_xml_artifact_id` | FK `→ invoice_artifacts(id)` | FK | Service-layer lookup + assertion | MEDIUM — orphan reference possible if `invoice_artifacts` row deleted |

### §V61.B — Service-layer mandatory submission rule (never had a DB form)

| # | Column | Rule | Enforce at | Reference |
|---|---|---|---|---|
| 1 | `zatca_simplified_headers.cryptographic_stamp_value` | MUST be non-NULL when the header status transitions to a submitted/reported state | State-machine guard in the submission service | BR-KSA-60 |
| 2 | `zatca_simplified_headers.signed_xml_artifact_id` | MUST be non-NULL when the header status transitions to a submitted/reported state | (same guard) | BR-KSA-60 |

---

## Notes for the downstream "Constraint Layer" spec

1. **Priority order for enforcement** (highest correctness risk first):
   1. FK on new sub-tables (§V59.B, §V60.B) — orphans are unrecoverable.
   2. UNIQUE on `tax_subtotals` natural key (§V59.A.1) — directly causes Fatoora rejection.
   3. Cross-column exemption rule (§V59.C.2) — easy to violate at the service layer.
   4. VAT-number, country-code, document-type shape rules (§V58.A, §V58.B).
   5. Everything else.

2. **Bean Validation is insufficient for cross-column rules.** Use a class-level `@AssertTrue` validator method on the entity, or an explicit service-layer guard.

3. **FR-021 audit channel.** The V58–V61 migrations' backfill diff already emits `RAISE NOTICE` lines for rows whose backfilled values would violate any rule above. Future enforcement should keep that audit in place even after DB constraints are added — the notice acts as a regression watch.

4. **specs/011 ingestion gateway** bypasses the internal UI and writes via bulk DTOs. The downstream constraint layer MUST publish a reusable `ValidationProfile` (or annotation set) that both the internal-UI service and the ingestion gateway consume.

5. **Promotion path.** Once a column's deferred rule has been enforced at the app layer for at least one full release with zero audit notices, the rule MAY be promoted back into the DB as a `CHECK` / `UNIQUE` / `FK` / `NOT NULL` in a future migration — at which point the corresponding row in this file SHOULD be marked **"Resolved — promoted to DB in V<N>"** rather than deleted, so the history is preserved.

---

## T058 audit (Phase 7) — cross-reference vs. V58–V61 SQL inline comments

**Date:** 2026-05-27
**Method:** mechanical grep of every `§VXX.X` identifier in this file against `-- VALIDATION (deferred): … see deferred-validation.md §<id>` comments in `platform-core/src/main/resources/db/migration/V58__*.sql`, `V59__*.sql`, `V60__*.sql`, `V61__*.sql`.

### Result

| Section block | Rows defined | Rows referenced in SQL | Notes |
|---|---|---|---|
| §V58.A (promoted party shape) | 12 | 12 | full match |
| §V58.B (document-type enums) | 4 | 1 (B.3) | B.1 / B.2 / B.4 columns pre-date V58 and are not re-altered — see "Pre-existing columns" below |
| §V58.C (cross-column rules) | 3 | 1 (C.2) | C.1 / C.3 reference columns that pre-date V58 |
| §V58.D (NOT NULL with DEFAULT) | 8 | 8 | full match after T058 fix-up (D.4 inline comment added on `seller_country_code` 2026-05-27) |
| §V58.E (zatca_uuid type change abandoned) | 1 | 0 | E.1 column pre-dates V58; type change explicitly NOT made — see "Pre-existing columns" |
| §V59.A | 2 | 2 | full match |
| §V59.B | 4 | 4 | full match |
| §V59.C | 4 | 4 | full match |
| §V59.D | 6 | 6 | full match |
| §V60.A | 1 | 1 | full match |
| §V60.B | 2 | 2 | full match |
| §V60.C | 5 | 5 | full match |
| §V61.A | 2 | 2 | full match |
| §V61.B | 2 | 2 | full match |

### Pre-existing columns (legitimate non-inline orphans)

The migration-script convention in §1 above states each V58–V61 SQL file MUST carry an inline comment beside every **affected column**. The following identifiers reference columns that are NOT altered by any V58–V61 migration (they pre-date V58 and continue to live in earlier-migration DDL), so there is no `ADD COLUMN` / `ALTER COLUMN` line on which to attach an inline comment in V58–V61:

- **§V58.B.1** `invoice_type_code` — column declared pre-V58
- **§V58.B.2** `transaction_type_code` — column declared pre-V58
- **§V58.B.4** `tax_currency` — column declared pre-V58
- **§V58.C.1** `supply_end_date` / `supply_date` — both columns declared pre-V58
- **§V58.C.3** `supply_date` / `invoice_type_code` / `transaction_type_code` — all columns declared pre-V58
- **§V58.E.1** `zatca_uuid` — column declared pre-V58; the V58 type-change-to-UUID was explicitly abandoned, so there is no V58 ALTER on the column

These rows remain in this catalogue as the authoritative reference for the downstream "Constraint Layer" spec to pick up. The lack of an inline comment in V58–V61 is **not** drift — it reflects the convention's scope ("affected column").

### Sign-off

T058 audit complete — zero drift between this catalogue and V58–V61 SQL inline comments, accounting for the pre-existing-column exception above. One inline comment was added (§V58.D.4 on `seller_country_code`) to bring V58 SQL into full alignment.

---

*End of deferred-validation.md — Feature 010 Authority Spec Alignment.*
