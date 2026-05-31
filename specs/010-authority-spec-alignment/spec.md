# Feature Specification: Authority Spec Alignment

**Feature Branch**: `010-authority-spec-alignment`
**Created**: 2026-05-25
**Status**: Draft
**Input**: Schema-alignment refactor of the four ZATCA + ETA document table groups (V48–V57) to match the published authority specifications, so that platform-generated UBL XML / ETA JSON validates against official validators with zero errors, and so that the inbound ERP ingestion gateway (specs/011) builds on a schema that matches the SDK shape.

**Design inputs** (treat as authoritative for resolved decisions):
- `Docs/eta-receipt-sdk-v1-2-alignment.md` — ETA Receipt SDK v1.2 gap analysis (decisions locked 2026-05-25)
- `Docs/zatca-spec-alignment.md` — ZATCA Fatoora SDK v2.0.3 + KSA schematron gap analysis (decisions locked 2026-05-25)
- `Docs/sprint-1-ingestion-gateway-implementation-plan.md` §10 — Sprint 1 cross-cutting decisions (locked 2026-05-25)
- ETA Invoice tables (V48/V49) — verified aligned 2026-05-25; **no alignment doc, no migration needed**

## Clarifications

### Session 2026-05-26

- Q: How should pre-V46 ZATCA rows with no resolvable `zatca_configs` linkage be reconciled with SC-008's "every previously-submitted document has non-null signature artifacts"? → A: Best-effort backfill with explicit nullable column. V61 looks up an active `zatca_configs` for `(company_id, authority_environment_id)` first, falls back to the most-recent historic/inactive row, and only if both lookups fail does it leave `zatca_config_id` (and the related signature columns when the source JSONB is empty) NULL and emit a migration log entry. `zatca_config_id` MUST remain nullable in the schema (no `NOT NULL` DB constraint); the V61 script carries a code comment documenting that the nullability is preserved to accommodate Wave 7/8 legacy rows whose config linkage cannot be reconstructed.
- Q: Should the Fatoora-CLI / ETA-pre-production verification be wired as a CI gate, nightly scheduled job, or one-shot manual script? → A: One-shot manual script only. Deliver a standalone runnable script that a developer invokes locally on demand (e.g., before a release cut) to drive `fatoora -invoice <xml> -validate` against representative documents and to submit an ETA Receipt sample to pre-production. No CI gate, no scheduled pipeline, no Maven test-suite integration — verification jobs are explicitly out of scope; the platform commits only to making the manual script exist and be runnable.
- Q: Where do migration-backfill findings (duplicate VAT numbers, mismatch counts, unresolved `zatca_configs` linkages) and manual-script results land? → A: Plain log output, single channel per tool. V58–V61 emit findings as Flyway migration log lines (visible in `mvn flyway:migrate` output and the Flyway schema-history audit); FR-019/FR-020 manual scripts write a sibling `.log` file next to the script invocation directory. No `migration_findings` table, no notifications, no automated gate — the developer reading the log before promoting is the gate.
- Q: Should V58–V61 + their entity changes ship as one PR or be split? → A: Four PRs in strict merge sequence — one per migration (V58, then V59, then V60, then V61). Each PR carries exactly its migration SQL plus the Wave 7/8 entity, repository, service, and serialiser updates that consume the new columns from that migration (per FR-004). Each PR MUST be independently verifiable on staging before the next is opened. No "migrations-only" PR, no bundled multi-migration PR. This makes rollback per-migration and keeps review surface bounded.
- Q: How are schema gaps discovered later during specs/011 (ingestion gateway) development handled? → A: Treat as a separate follow-up feature (V62+). Feature 010 closes when V61 merges and its manual verification scripts run clean. Any missing column or constraint surfaced during specs/011 implementation MUST land as its own migration (V62, V63, …) in its own PR, not by reopening 010. This codifies the existing "downstream consumer, not co-feature" boundary in the Assumptions section.
- Q: How aggressively should V58–V61 enforce data shape at the DB layer? → A: Smart-application / dumb-database pattern. V58–V61 are limited to: `ALTER TABLE ADD/DROP/RENAME COLUMN` (with column type and `DEFAULT` clauses only), `CREATE TABLE` with `PRIMARY KEY` only, `CREATE INDEX` (to back SC-006), `UPDATE` backfills, and `RAISE NOTICE` for log emission. **No new `CHECK`, `UNIQUE`, `FOREIGN KEY`, or `NOT NULL` clauses are created by these migrations.** The `zatca_uuid VARCHAR(255) → UUID` type change is abandoned in 010 — the column stays `VARCHAR(255)`. Every deferred rule (shape regex, enum, range, cross-column invariant, sub-table → header FK including `ON DELETE CASCADE`, V61 signature FKs, every `NOT NULL`, every natural-key `UNIQUE`) is catalogued in `specs/010-authority-spec-alignment/deferred-validation.md`. A separate downstream spec will implement them at the service / Bean Validation / DTO layer. Each migration SQL file MUST include an inline comment for every deferred rule that would otherwise have lived on the affected column (e.g. `-- VALIDATION (deferred): seller_vat_number ~ '^3[0-9]{13}3$' — see deferred-validation.md §V58.A`).
- Q: Should the FR-019/FR-020 Fatoora-CLI and ETA-pre-production manual verification scripts ship with this feature? → A: **No — deferred to a follow-up feature.** The locked scope of 010 is now strictly "schema migrations (V58–V61) + the paired entity/repository/service/serialiser updates that consume the new columns + minimal frontend read-side updates." The Fatoora SDK is not invoked by this feature, the ETA pre-production endpoint is not called, no `scripts/verify/` directory is created, no canonical sample fixtures are produced. **SC-001, SC-002, SC-003, FR-019, and FR-020 are deferred along with the scripts.** Correctness of the serialiser changes is verified within this feature by the platform's existing golden-file tests (updated per migration PR). External validator confirmation against the official Fatoora schematron and ETA pre-prod is a separate downstream spec.

### Session 2026-05-25 — Resolved Decisions

These 18 decisions are locked. They drive plan.md and tasks.md; no re-litigation.

**Sprint 1 / cross-cutting**

- Q: When an ERP calls the ingestion gateway with `companyRegistrationNumber`, which column does the platform look up by — `tax_number` or `cr_number`? → A: Always `tax_number` for both ETA and ZATCA. ZATCA's 15-digit VAT number is stored in `companies.tax_number`; `cr_number` is informational only and not used for lookup.
- Q: When an ERP POSTs an ingestion request with a `documentNumber` that already exists for the same (company, authority_environment), what does the gateway return? → A: HTTP 409 Conflict via the existing `Duplicate*Exception` classes. Idempotent retry semantics are out of scope.
- Q: When a credit or debit note references an `originalInvoiceNumber` the platform has never seen (because the original was submitted to ETA/ZATCA outside the gateway), what happens? → A: Hybrid resolution. The service stores the raw number string in a new `original_invoice_number VARCHAR(100)` column on every header table, and populates the existing `original_*_id` FK only when a matching row is found locally.

**ETA Receipt SDK v1.2**

- Q: Are the legacy receipt-type codes (`r`, `rr`, `rrwr`, `cr`, `crr`, `gs`, `gsr`) still valid alongside v1.2's `s`? → A: Keep all legacy codes accepted in both the DTO `@Pattern` and the existing CHECK constraint. The DTO pattern is `s|r|rr|rrwr|cr|crr|gs|gsr`. If ETA narrows the set later, relax with a single annotation change.
- Q: Should `eta_receipt_headers.total_discount_amount` be renamed in place to match the SDK's `totalCommercialDiscount`, or kept as an alias? → A: Rename in place. Pre-production, no compatibility window required. Entity/service references update in the same PR.
- Q: Who generates ETA Receipt `header.uuid` (the SHA-256 hex string) — the ERP or the gateway? → A: ERP generates; the gateway only validates format (`[a-fA-F0-9]{64}`). No canonicalisation algorithm is implemented in the platform.
- Q: Is the ETA Receipt `buyer` object required at root, or may it be omitted for anonymous retail? → A: Required object with `type` always present. `id` and `name` are nullable on the DTO and become required at the service layer when `type = B`, or when `type = P` and `totalAmount ≥ 150,000 EGP`.
- Q: How should the `eta_receipt_lines.unit_value` JSONB column be reconciled with v1.2's scalar `unitPrice`? → A: Add scalar `unit_price NUMERIC(18,5)` to `eta_receipt_lines`; backfill from `unit_value->>'amountSold'`; drop `unit_value`. Add `exchange_rate NUMERIC(18,5)` to `eta_receipt_headers` to carry the field that moved from line to header.
- Q: Should the DTO enforce `feesAmount` and `adjustment` as exactly `0.0` (per the SDK's "reserved" marker), or allow any non-negative? → A: Allow any non-negative (`@DecimalMin("0")` only). Future-flexible if ETA reactivates the fields.

**ZATCA spec (V54 / V55)**

- Q: Does the platform already have a signing-certificate table, or does V61 create one? → A: Reuse `zatca_configs` (V46). It already holds `private_key`, `compliance_certificate`, `production_certificate`, `device_uuid`, `csr` per Principle XVIII (Phase-1 plain-text storage). New header column is `zatca_config_id UUID REFERENCES zatca_configs(id)`.
- Q: Should `zatca_uuid` be made `NOT NULL` after backfill of existing DRAFT rows? → A: No. Change the column type from `VARCHAR(255)` to `UUID` but keep it nullable. NULL is semantically correct for DRAFT documents; the UUID is generated at submission time.
- Q: Should `zatca_standard_lines.unit_price` be renamed to `item_net_price` (BT-146) and the UBL price block added, or left as-is? → A: Rename to `item_net_price`. Add new columns BT-148 (`item_gross_price`), BT-147 (`item_price_discount`), BT-149 (`item_price_base_quantity`), BT-150 (`item_price_base_quantity_unit`). Backfill `item_price_base_quantity = 1` for existing rows.
- Q: How should `allowance_total_amount` be kept consistent with the new `zatca_*_allowances` child table — trigger, service assertion, or removal? → A: Drop the column entirely. BT-107 is computed on read from `zatca_*_allowances`. The UBL serialiser SUMs the child table at document-generation time. Eliminates the drift risk and the sync mechanism.
- Q: BT-111 (`tax_amount_accounting_currency`) is mandatory only when document currency ≠ SAR. Always populate or leave NULL for SAR-only invoices? → A: Always populate. Column is `NOT NULL DEFAULT 0` and equals `tax_amount` when `currency = 'SAR'`. Simpler service code; validator-clean for both single- and multi-currency cases.
- Q: Payment means (BT-81) cardinality — single denormalised columns on the header or a child table? → A: Single denormalised columns on each header table — `payment_means_code VARCHAR(3)` with a CHECK on `{1, 10, 30, 42, 48}` + `payment_means_text VARCHAR(50)`. If multi-method invoices become a real requirement later, migrate to a child table then.
- Q: Should `zatca_standard_headers.clearance_status` and `zatca_simplified_headers.reporting_status` be unified into one generic column? → A: Keep separate. ZATCA itself distinguishes clearance (Standard) from reporting (Simplified); the column names reflect a real domain distinction.
- Q: How aggressive should the promotion of party fields out of `seller_data`/`buyer_data` JSONB be? → A: Promote only the schematron-validated structural fields — VAT number (15-digit CHECK), postal code (5-digit), additional/building number (4-digit), country code = 'SA', party ID + scheme ID. Free text (name, street, neighborhood, city) stays in JSONB.

## Overview

The platform's four operational document table groups (`eta_invoice_*` V48/V49, `eta_receipt_*` V50/V51, `zatca_standard_*` V54, `zatca_simplified_*` V55) were modelled during Waves 7 and 8 to support an internal happy-path UI and submission flow, not to be a faithful representation of the published authority specifications. As the platform prepares for production launch and for the inbound ERP ingestion gateway (specs/011), three problems become structural blockers:

1. **ZATCA Standard and Simplified tables do not capture the full UBL structure** mandated by the KSA schematron — the per-rate VAT breakdown (BG-23) cannot be reconstructed; UBL document-level allowances (BG-20, BT-92..98) collapse into a single scalar; the line-level price block (BT-146..150) is absent so BR-KSA-EN16931-11 cannot be validated; the cryptographic stamp (KSA-15) has no first-class storage. Documents generated from this schema will fail the official Fatoora validator.

2. **`eta_receipt_*` schema lags ETA Receipt SDK v1.2** — flat header fields that v1.2 nests under `header`, the older `unit_value` JSONB on lines that v1.2 replaces with a scalar `unitPrice`, missing root-level structures (`taxTotals`, `contractor`, `beneficiary`), missing header fields (`previousUUID`, `exchangeRate`). Receipts generated from this schema cannot be submitted as v1.2 payloads.

3. **The inbound ERP ingestion gateway** (specs/011) is being designed against SDK-shaped DTOs. Without aligning the DB first, the ingestion services must either map lossy-ly or write fields into a `raw_payload` JSONB workaround and then rewrite the services after the migrations land. Two implementations for the same code path.

This feature delivers the four-migration sequence (V58–V61) that closes those gaps, the corresponding entity and serialiser updates in Wave 7/8 code paths, and a verification harness that uses the official Fatoora CLI and ETA pre-production endpoints to prove that documents produced from the new schema validate cleanly. The feature is a refactor — no new user-facing screens, no new business workflows. Its success is measured against the official validators, not the UI.

ETA Invoice tables (V48/V49) are verified aligned with the ETA Invoice SDK as of 2026-05-25 and are explicitly out of scope; no alignment doc and no migration are produced for them.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Validator-clean UBL output from existing ZATCA documents (Priority: P1)

A finance user submits a ZATCA Standard tax invoice that was created through the existing Wave 8 form. The platform generates UBL XML, signs it, and (in dev/staging where the offline validator is wired up) the same XML is run through the official Fatoora `-validate` command. The validator reports zero `BR-KSA-*` errors. The same is true for credit notes, debit notes, exempt-VAT lines, multi-currency invoices, and document-level allowances.

**Why this priority**: Today, ZATCA documents generated by the platform cannot be guaranteed to pass the schematron because the schema is missing or misstructured for several mandatory fields. This is the load-bearing reason for the feature; the production submission path depends on it.

**Independent Test**: Build a sample ZATCA Standard invoice from a Wave-8-style draft (with one or more lines, header allowances, and a VAT-exempt line carrying exemption reason). Serialise to UBL with the post-V58/V59/V60/V61 entity model. Run `fatoora -invoice sample.xml -validate`. Result: zero rule violations.

**Acceptance Scenarios**:

1. **Given** a Standard tax invoice with one S-rated line and one E-exempt line, **When** it is serialised to UBL and passed to `fatoora -validate`, **Then** zero rule violations are reported, including BR-KSA-EN16931-08 (one TaxTotal with subtotals) and BR-KSA-CL-04/05 (exemption reason code + text present).
2. **Given** a Standard credit note referencing an original invoice number, **When** it is serialised, **Then** the UBL `cac:BillingReference/cac:InvoiceDocumentReference/cbc:ID` carries the string from `billing_reference_id` and BR-KSA-56 passes.
3. **Given** a Standard invoice with a multi-line document-level allowance, **When** serialised, **Then** each `cac:AllowanceCharge` block carries the allowance row's BT-92..98 fields and BR-KSA-EN16931-03/04/05 pass.
4. **Given** a Standard invoice with multi-currency totals (document currency ≠ SAR), **When** serialised, **Then** both `cac:TaxTotal` elements appear (one in document currency, one in SAR via `tax_amount_accounting_currency`) and BR-KSA-EN16931-09 passes.

---

### User Story 2 - Validator-clean ZATCA Simplified output with cryptographic stamp (Priority: P1)

A finance user submits a ZATCA Simplified document. The platform generates the signed UBL with the XAdES envelope, computes the QR code, and persists the signed XML, the cryptographic stamp value, and the signing-certificate reference against the document. The same XML, run through `fatoora -validate` and `fatoora -sign`, validates cleanly and produces the expected `request.json` shape (per ZATCA's reporting API contract).

**Why this priority**: Simplified invoices are mandatory for B2C taxpayers (the majority of platform users at launch). They require the full Phase-2 signature artifact set, which today lives in a free-form `zatca_response_data` JSONB blob. Without first-class storage of the cryptographic stamp and signed XML, audit cannot retrieve the exact submitted artifact byte-for-byte, and re-validation of historic submissions is impossible.

**Independent Test**: Submit a Simplified invoice. Verify that `zatca_simplified_headers` now has populated `cryptographic_stamp_value`, `signed_xml_artifact_id`, `zatca_config_id`, and `signed_at`. Run `fatoora -invoice <signed.xml> -validate` against the persisted signed artifact: zero violations, BR-KSA-60 passes (signature present).

**Acceptance Scenarios**:

1. **Given** a Simplified document is submitted, **When** the submission completes, **Then** `cryptographic_stamp_value`, `signed_xml_artifact_id`, `zatca_config_id`, and `signed_at` are populated on the header row.
2. **Given** a persisted signed-XML artifact, **When** retrieved through the existing artifact-download path, **Then** the bytes are identical to what was sent to ZATCA at submission time.
3. **Given** the platform has multiple `zatca_configs` rows (e.g., one per environment), **When** a Simplified document is signed, **Then** `zatca_config_id` references the exact config row used at signing time and the link survives the artifact-immutability rules.

---

### User Story 3 - ETA Receipt v1.2 payload shape (Priority: P1)

A finance user submits an ETA receipt. The platform serialises it into the v1.2 JSON shape — `header` nested object, `documentType` nested object, seller carrying POS/activity fields, array-of-discount-objects on lines, `taxTotals` at root, `feesAmount`/`adjustment` populated as 0.0. The payload, sent to ETA pre-production, is accepted (or, if rejected, the rejection reason is not "shape mismatch with v1.2").

**Why this priority**: The ETA receipt flow was modelled before v1.2 was published. Without alignment, ETA pre-production will reject every receipt with structural errors before any business rule is even evaluated.

**Independent Test**: Submit a receipt to ETA pre-production. The HTTP response indicates the payload was understood (whether ETA accepts or rejects based on business content is a separate concern). The serialised JSON, captured before transmission, matches §10 of `Docs/eta-receipt-sdk-v1-2-alignment.md` byte-for-byte modulo whitespace.

**Acceptance Scenarios**:

1. **Given** a receipt with a single sales line and one tax type, **When** serialised, **Then** the JSON has a nested `header` object containing `receiptNumber`, `dateTimeIssued`, `uuid`, `previousUUID`, `currency`, and `exchangeRate` at the same level.
2. **Given** a receipt line with `unit_price = 50.00`, **When** serialised, **Then** the JSON `itemData[].unitPrice` field carries the scalar value (not the legacy `unit_value` object).
3. **Given** a receipt for anonymous retail (`buyer.type = "P"`, `id = null`, `name = null`, `totalAmount < 150,000`), **When** validated at the service layer, **Then** the conditional buyer-name/id rule is satisfied and the receipt proceeds.

---

### User Story 4 - Existing Wave 7/8 data round-trips through the new schema (Priority: P2)

A user opens a Wave-8-era ZATCA Standard invoice that existed before V58–V61 ran. The detail screen renders correctly: party fields read out of the promoted columns where they're now stored and out of `seller_data`/`buyer_data` JSONB where they remain. The header totals reconcile (with `allowance_total_amount` now computed from the child table). The chain snapshot is intact.

**Why this priority**: A refactor that breaks existing rows is worse than no refactor. Wave 7/8 left dev/staging environments populated with real-shaped test data. The migrations must backfill the new structure from the old JSONB and aggregate columns and must leave existing rows readable through the post-migration entity model.

**Independent Test**: Run the migrations on a copy of the staging database. After migration: existing rows have non-null `seller_vat_number`, `seller_postal_code`, `seller_country_code` (backfilled from JSONB); `zatca_*_allowances` rows exist for any header that had a non-zero `allowance_total_amount`; existing reads through the entity API return the same business values as before.

**Acceptance Scenarios**:

1. **Given** a pre-migration ZATCA Standard row with `seller_data->>'vatNumber' = '300000000003'`, **When** the migration completes, **Then** `seller_vat_number = '300000000003'` and the JSONB still contains the original blob.
2. **Given** a pre-migration row with `allowance_total_amount = 100.00` and no child rows, **When** the migration completes, **Then** one `zatca_standard_allowances` row exists for that header with `amount = 100.00`, and the header's BT-107 (computed on read) returns 100.00.
3. **Given** a pre-migration row with `unit_price = 25.50` on a line, **When** the migration completes, **Then** `item_net_price = 25.50` and `item_price_base_quantity = 1` on that line.

---

### User Story 5 - Schema is queryable on the promoted identity fields (Priority: P2)

An operations user filters the company list by VAT registration number. Where that field used to live only in JSONB (un-indexable, un-queryable except via slow JSONB path expressions), it is now a typed column with an index. The query returns in under 100 ms even on a table with tens of thousands of headers.

**Why this priority**: The schematron-validated fields (VAT number, postal code, additional number, party ID + scheme) are also the fields ops most often filters by. JSONB queries on these are correct but slow and don't enforce shape. Promotion gives both speed and DB-level enforcement.

**Independent Test**: Run `EXPLAIN ANALYZE` on `SELECT … FROM zatca_standard_headers WHERE seller_vat_number = '300000000003'` against a populated staging DB. Plan uses an index on `seller_vat_number`; runtime is under 100 ms.

**Acceptance Scenarios**:

1. **Given** a populated `zatca_standard_headers` table, **When** a query filters by `seller_vat_number`, **Then** an index is used and the query completes under 100 ms (p95).
2. **Given** an attempt to write a row with a malformed VAT number, **When** the write is dispatched through the service layer, **Then** the service-layer validation (Bean Validation `@Pattern` per deferred-validation.md §V58.A, or an equivalent service-layer assertion) rejects it with a clear error before the `INSERT` is issued to the DB. (Note: the DB itself does not enforce the shape — see Clarifications session 2026-05-26.)

---

### User Story 6 - ERP ingestion gateway can consume the schema (Priority: P3)

The downstream `specs/011-ingestion-gateway` feature, when it begins, finds a schema that matches the SDK-shaped DTOs from `Docs/sprint-1-ingestion-gateway-implementation-plan.md` Steps 7-10. The ingestion service maps DTO → entity field-for-field with no lossy translation, no `raw_payload` JSONB workaround, and no second-mapper layer.

**Why this priority**: This is the strategic reason the alignment is being done now rather than later, but it is consumed by a downstream feature — the value materialises when specs/011 lands, not on this feature's completion.

**Independent Test**: Cross-check between `Docs/sprint-1-ingestion-gateway-implementation-plan.md` Steps 7-10 DTO field list and the post-migration entity field list. Every DTO field maps to exactly one entity column (or one JSONB subkey for free-text-only fields). No DTO field requires "store as opaque blob" handling.

**Acceptance Scenarios**:

1. **Given** the four ingestion DTOs from specs/011 plan Steps 7-10, **When** mapped to the post-V58–V61 entity model, **Then** every DTO field has a deterministic target (column or JSONB key) and no field is silently dropped.
2. **Given** an SDK-shaped JSON payload posted to a hypothetical ingestion endpoint stub, **When** deserialised and persisted with a straightforward mapper, **Then** the resulting row contains all SDK fields and a round-trip serialisation produces back the same JSON shape.

---

### Edge Cases

- **Existing draft with NULL `zatca_uuid`**: V58 does **not** alter the column type — `zatca_uuid` stays `VARCHAR(255)` per the locked smart-app/dumb-DB decision. NULL remains valid for DRAFT documents; the service layer populates a UUID-shaped string at submission time (validation deferred — see deferred-validation.md §V58.E).
- **Existing row with `allowance_total_amount > 0` and no child detail**: V59 must synthesise one `zatca_*_allowances` row from the aggregate (with `reason = 'migrated-from-aggregate'`) before V60 drops the column; otherwise BT-107 would compute to zero post-migration.
- **Existing row with `unit_value` JSONB containing currency-mixed `amountEGP` ≠ `amountSold`**: V60 backfill must preserve the EGP equivalent in the new `unit_price` column for receipts where currency = EGP, and the `amountSold` for currency ≠ EGP, and the new header `exchange_rate` must be populated from the JSONB if present.
- **Existing row with seller VAT in JSONB but no 15-digit shape**: V58 backfill writes the raw value into `seller_vat_number` regardless of shape. The 15-digit shape rule is deferred to the service layer per deferred-validation.md §V58.A; the FR-021 audit emits a `RAISE NOTICE` for any backfilled value that does not match `^3[0-9]{13}3$`, but the row is not rejected.
- **Duplicate keys across the promoted-VAT column**: Backfill may surface latent duplicates that the JSONB structure hid; the migration must emit each duplicate to the Flyway migration log (per FR-021) rather than silently succeeding.
- **Pre-V46 rows with no `zatca_config_id` linkage at all**: Wave 7/8 submitted documents predate the column. Backfill performs a best-effort, two-pass lookup against `zatca_configs` for `(company_id, authority_environment_id)` — first the currently-active row, then (on miss) the most-recent historic/inactive row. Only when both passes return no match is `zatca_config_id` left NULL and the row id emitted to the migration log. The column is intentionally declared nullable in V61 (with a comment in the migration script) so unrecoverable legacy rows do not block the migration.
- **ETA Receipt rows with the legacy `unit_value` containing currency exchange rate**: Move the `currencyExchangeRate` value from line JSONB into the new header `exchange_rate` column; if multiple lines disagree, take the first non-null value and log a data-integrity warning.
- **`payment_means_code` of legacy receipts/invoices**: If `payment_data` JSONB or `payment_method` legacy column contained a value not in `{1, 10, 30, 42, 48}`, the new CHECK rejects it; backfill must map known synonyms (e.g. "CASH" → '1', "CARD" → '48') and report unmappable values.

## Requirements *(mandatory)*

### Functional Requirements

#### Migration sequencing and integrity

- **FR-001**: The migration MUST be applied as four ordered Flyway scripts — V58 (structural fixes + new columns), V59 (new header sub-tables for tax subtotals, allowances, payment-means already folded into V58 for cardinality=1), V60 (line-block + line-allowance child + unit-value drop on receipts), V61 (signature columns + signed-XML artifact links) — each independently runnable on dev/staging and each producing a database whose entity model still compiles and whose existing flows still operate.
- **FR-002**: Each migration MUST be reversible to a non-destructive degree: column adds are reversible by drop; renames are accompanied by a backfill that survives a roll-forward; only structural drops (e.g. `allowance_total_amount`, `unit_value`) are explicitly one-way and MUST be performed only after the parent migration has been verified on staging.
- **FR-003**: No migration MUST run a destructive operation (column drop, `NOT NULL` tightening on existing data, CHECK constraint addition) before the corresponding backfill has populated every existing row.
- **FR-004**: Each migration MUST be paired with the Wave 7/8 entity changes that consume its new columns; entity and migration ship in the same PR. The four migrations MUST be delivered as four separate PRs in strict merge sequence (V58 → V59 → V60 → V61); each PR carries exactly its migration SQL plus the entity, repository, service, and serialiser updates that consume that migration's columns, and each PR MUST be independently verified on staging before the next is opened. The repository MUST NOT contain a state in which a migration has been applied but the entity model does not yet read/write the new columns.

#### ZATCA schema conformance

- **FR-005**: The `zatca_standard_headers` and `zatca_simplified_headers` tables MUST carry: `business_process_code` (`DEFAULT 'reporting:1.0'`), `issuance_reason` (BT-25 reason, ≤127 chars), `billing_reference_id` (original invoice number string, ≤100 chars), `original_invoice_number` (Sprint 1 hybrid resolution), `erp_reference_id` (Sprint 1 ingestion), `tax_amount_accounting_currency` (BT-111, `DEFAULT 0`; `NOT NULL` deferred — see deferred-validation.md §V58.D), `rounding_amount` (BT-114, `DEFAULT 0`), `payment_means_code` + `payment_means_text` (BG-16 denormalised; payment-means-code enum deferred — see deferred-validation.md §V58.B).
- **FR-006**: The `zatca_*_headers` tables MUST carry promoted party columns (`seller_vat_number`, `seller_group_vat_number`, `seller_party_id`, `seller_party_id_scheme`, `seller_building_number`, `seller_additional_number`, `seller_postal_code`, `seller_country_code` and the buyer mirrors). The schematron-driven shape and enum rules for these columns (VAT 15-digit with first/last `3`, postal 5 digits, building/additional 4 digits, country='SA' on Standard, party-ID scheme enum) are **NOT enforced by V58 DB constraints** per the locked smart-application/dumb-database decision — see deferred-validation.md §V58.A. Free-text party fields (name, street, neighborhood, city) MUST remain in `seller_data`/`buyer_data` JSONB.
- **FR-007**: A new table `zatca_standard_tax_subtotals` (and its Simplified mirror) MUST persist the per-VAT-category aggregate (BG-23): `vat_category_code`, `vat_rate`, `taxable_amount`, `tax_amount`, `exemption_reason_code`, `exemption_reason_text`. BR-KSA-EN16931-08 requires exactly one TaxTotal block with these subtotals.
- **FR-008**: A new table `zatca_standard_allowances` (and its Simplified mirror) MUST persist UBL document-level allowances (BG-20): `amount`, `base_amount`, `percentage`, `vat_category_code`, `vat_rate`, `reason_code`, `reason`. The header column `allowance_total_amount` MUST be dropped; BT-107 is computed on read from this child table.
- **FR-009**: `zatca_standard_lines` and `zatca_simplified_lines` MUST add the UBL line-price block: `item_net_price` (BT-146, renamed from `unit_price`), `item_gross_price` (BT-148), `item_price_discount` (BT-147), `item_price_base_quantity` (BT-149, `DEFAULT 1`; the `> 0` and `NOT NULL` rules are deferred — see deferred-validation.md §V60.C), `item_price_base_quantity_unit` (BT-150), and `vat_inclusive_amount` (KSA-12; `NOT NULL` deferred).
- **FR-010**: A new table `zatca_standard_line_allowances` (and its Simplified mirror) MUST persist per-line allowances (BT-136..138) with `amount`, `base_amount`, `percentage`, `reason`. The flat `discount_amount` and `allowance_amount` columns MUST be migrated into this table and then dropped.
- **FR-011**: Each `zatca_*_headers` row MUST carry four signature-artifact columns: `cryptographic_stamp_value` (KSA-15 ECDSA signature value), `signed_xml_artifact_id` (plain `UUID` column; the FK to `invoice_artifacts` is deferred — see deferred-validation.md §V61.A), `zatca_config_id` (plain `UUID` column semantically referencing `zatca_configs(id)` (V46) but **without a DB-level FK**; the FK is deferred per deferred-validation.md §V61.A; V61 documents in an inline comment that the column is intentionally nullable to accommodate legacy Wave 7/8 rows whose config linkage cannot be reconstructed), and `signed_at` (timestamp).
- **FR-012**: `zatca_uuid` MUST remain `VARCHAR(255)` (nullable) in scope 010 — the type change to `UUID` is **abandoned** per Clarifications session 2026-05-26. The app layer is responsible for emitting UUID-shaped strings (matches `^[0-9a-fA-F-]{36}$`) at submission time; see deferred-validation.md §V58.E. No backfill of existing NULL rows.

#### ETA Receipt v1.2 conformance

- **FR-013**: `eta_receipt_headers.total_discount_amount` MUST be renamed in place to `total_commercial_discount`. All entity, service, repository, and serialiser references MUST be updated in the same migration PR.
- **FR-014**: `eta_receipt_headers` MUST gain `exchange_rate NUMERIC(18,5)` (moved from line `unit_value` JSONB), `previous_uuid TEXT`, `reference_old_uuid TEXT`, `s_order_name_code VARCHAR(200)`, `order_delivery_mode VARCHAR(30)`, `gross_weight NUMERIC(18,5)`, `net_weight NUMERIC(18,5)`, `tax_totals JSONB`, `extra_receipt_discount_data JSONB`, `contractor_data JSONB`, `beneficiary_data JSONB`, `fees_amount NUMERIC(18,5) DEFAULT 0`, `adjustment NUMERIC(18,5) DEFAULT 0`.
- **FR-015**: `eta_receipt_lines` MUST add a scalar `unit_price NUMERIC(18,5)`, backfill from `unit_value->>'amountSold'` for `currency != EGP` and `unit_value->>'amountEGP'` for `currency = EGP`, then drop the `unit_value` column.
- **FR-016**: `eta_receipt_lines` MUST replace the flat `discount_rate`/`discount_amount`/`items_discount` columns with `commercial_discount_data JSONB DEFAULT '[]'` and `item_discount_data JSONB DEFAULT '[]'` arrays; existing flat values MUST be migrated into the array form before dropping the flat columns.

#### Cross-cutting (Sprint 1 dependencies)

- **FR-017**: All four header tables (`eta_invoice_headers`, `eta_receipt_headers`, `zatca_standard_headers`, `zatca_simplified_headers`) MUST gain `erp_reference_id VARCHAR(100)` and `original_invoice_number VARCHAR(100)` columns to support the Sprint 1 ingestion gateway's hybrid original-invoice resolution.
- **FR-018**: The existing `original_*_id UUID` FK columns MUST remain populated by the existing internal-UI write paths; the new `original_invoice_number` string column is populated additionally by both internal-UI writes (mirror of the FK row's number) and by ingestion writes (always).

#### Manual verification (DEFERRED to a follow-up feature)

- **FR-019** *(deferred to a follow-up feature per Clarifications session 2026-05-26)*: The Fatoora-CLI validation script and its four canonical Standard test cases will be delivered in a follow-up feature. Out of scope for 010. The schema and serialiser changes in V58–V60 still ship in 010 and are verified by golden-file tests; external validator confirmation against the official Fatoora schematron is the follow-up feature's responsibility.
- **FR-020** *(deferred to a follow-up feature per Clarifications session 2026-05-26)*: The equivalent Simplified validation script and the ETA Receipt v1.2 pre-production submission script will be delivered in the same follow-up feature as FR-019. Out of scope for 010.
- **FR-021**: Backfill correctness MUST be verified by a one-shot row-level diff step inside each migration (V58–V61) that compares pre-migration JSONB-derived values with post-migration column values for every existing row. Findings (mismatches, duplicate promoted-VAT numbers, unresolved `zatca_configs` linkages) MUST be emitted as Flyway migration log lines (visible in `mvn flyway:migrate` output and the Flyway schema-history audit). No `migration_findings` table is created, no notification is sent, and promotion of a migration to staging or beyond is gated by a developer reading the Flyway log (no automated gate). This is a migration-internal check, distinct from FR-019/FR-020's external-validator scripts. Additionally, the diff step SHOULD also emit a `RAISE NOTICE` for any backfilled value that would have violated a deferred rule from deferred-validation.md (e.g. a `seller_vat_number` not matching `^3[0-9]{13}3$`, or a row whose promoted columns conflict with the would-be `UNIQUE` natural key) — to preserve audit visibility even though the DB itself does not reject the row.

#### Out of scope

- **FR-022**: ETA Invoice (`eta_invoice_headers`/`lines`/`line_taxes`, V48/V49) is explicitly out of scope for this feature. The verification done on 2026-05-25 confirms structural alignment with the ETA Invoice SDK; no migration is produced for these tables, and no entity changes are required.
- **FR-023**: No new user-facing screens, no new business workflows, no new authority endpoints, no new permission scopes, no new bulk operations are delivered by this feature. UI changes are limited to whatever is required to (a) read promoted columns instead of JSONB-only access where applicable and (b) display new persisted artifacts (cryptographic stamp value, signed-XML download link).

### Key Entities *(include if feature involves data)*

- **ZATCA Standard Header (revised)**: Adds business-process code, issuance reason, billing reference ID, ERP reference ID, original invoice number, tax-amount in accounting currency, rounding amount, payment means code + text. Adds promoted party columns (VAT, group VAT, party ID + scheme, building number, additional number, postal code, country code) — all without DB-level shape CHECKs. Adds signature artifacts (cryptographic stamp value, signed XML artifact link, ZATCA config link, signed-at) — `signed_xml_artifact_id` and `zatca_config_id` are plain `UUID` columns with **no** FK constraint (deferred). Removes `allowance_total_amount`. `zatca_uuid` remains `VARCHAR(255)` — the type change is abandoned in 010.
- **ZATCA Standard Tax Subtotal (new)**: One row per VAT category per header. Carries category code, rate, taxable amount, tax amount, exemption reason code, exemption reason text. Drives the UBL `cac:TaxTotal/cac:TaxSubtotal` block (BG-23).
- **ZATCA Standard Allowance (new)**: One row per document-level allowance (UBL `cac:AllowanceCharge` at header). Carries amount, base amount, percentage, VAT category, VAT rate, reason code, reason text (BG-20, BT-92..98).
- **ZATCA Standard Line (revised)**: Renames `unit_price` → `item_net_price`. Adds `item_gross_price`, `item_price_discount`, `item_price_base_quantity`, `item_price_base_quantity_unit`, `vat_inclusive_amount`. Removes flat `discount_amount`/`allowance_amount` (moved to child table).
- **ZATCA Standard Line Allowance (new)**: One row per line-level allowance. Carries amount, base amount, percentage, reason. Drives UBL `cac:InvoiceLine/cac:AllowanceCharge` (BT-136..138).
- **ZATCA Simplified entities**: Same structural changes as Standard counterparts, with the existing `buyer_data` nullability preserved and the cryptographic-stamp columns enforced at the service layer (per BR-KSA-60).
- **ETA Receipt Header (revised)**: Renames `total_discount_amount` → `total_commercial_discount`. Adds exchange_rate, previous_uuid, reference_old_uuid, s_order_name_code, order_delivery_mode, gross_weight, net_weight, tax_totals JSONB, extra_receipt_discount_data JSONB, contractor_data JSONB, beneficiary_data JSONB, fees_amount, adjustment, erp_reference_id, original_invoice_number.
- **ETA Receipt Line (revised)**: Adds scalar `unit_price`; replaces flat discount columns with JSONB arrays; removes `unit_value`.

### Out of Scope Entities

- **ETA Invoice Header / Line / Line Tax**: No change. Verified aligned.
- **ETA Customer / ETA Item / ZATCA Customer / ZATCA Item**: No change.
- **`zatca_configs`**: No change — referenced by the new `zatca_config_id` FK column but the table itself is not modified.
- **`zatca_chain_state`**: No change.
- **`invoice_artifacts`, `submission_attempts`, `audit_logs`**: No change.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001** *(deferred — moves to the follow-up feature with FR-019)*: Fatoora-CLI validation is not measured in this feature. Replaced for 010's purposes by **SC-001a**: for each of the four canonical ZATCA Standard test cases (single line, exempt line, credit note, multi-currency), the platform's existing golden-file serialiser tests pass with the post-V60 schema in place. No external Fatoora invocation is required.
- **SC-002** *(deferred — moves to the follow-up feature with FR-019)*: Fatoora-CLI re-validation of signed XML is not measured in this feature. Replaced for 010's purposes by **SC-002a**: for each of two canonical Simplified test cases (anonymous retail + summary invoice), the platform's existing golden-file tests pass AND `cryptographic_stamp_value`, `signed_xml_artifact_id`, `zatca_config_id`, `signed_at` are populated on the persisted header row after submission via the existing Wave 8 service flow.
- **SC-003** *(deferred — moves to the follow-up feature with FR-020)*: ETA pre-production structural acceptance is not measured in this feature. Replaced for 010's purposes by **SC-003a**: for at least one ETA Receipt v1.2 sample, the serialised JSON produced by `EtaReceiptSerialiser` matches `Docs/eta-receipt-sdk-v1-2-alignment.md` §10 byte-for-byte modulo whitespace, as asserted by an updated golden-file test.
- **SC-004**: Running each of V58, V59, V60, V61 on a staging-shaped database (with at least one row per table populated with Wave 7/8-era data) completes in under 30 seconds per migration and produces zero data-loss warnings from the backfill diff job (FR-021).
- **SC-005**: After all four migrations, all existing Wave 8 ZATCA submission tests pass with zero modifications to their fixture data — the entity model's read path returns the same business values as before, even for fields that moved between JSONB and typed columns.
- **SC-006**: Filtering `zatca_standard_headers` by `seller_vat_number` uses an index and returns in under 100 ms (p95) against a staging table with at least 10,000 rows.
- **SC-007**: The Sprint 1 ingestion-gateway DTO-to-entity cross-check (per User Story 6 acceptance) shows every DTO field has a deterministic target — zero fields land in a `raw_payload` JSONB catch-all.
- **SC-008**: After V61, every previously-submitted ZATCA document **whose `zatca_response_data` JSONB contains recoverable signature artifacts** has a non-null `cryptographic_stamp_value`, `signed_xml_artifact_id`, `signed_at`, and a best-effort `zatca_config_id` (active → historic `zatca_configs` lookup). Rows where the JSONB is empty *or* where both `zatca_configs` lookups miss are permitted to retain NULL signature columns and MUST appear in the V61 backfill log; the count of such rows is reported as part of FR-021's diff job and does not block migration promotion.
- **SC-009**: No new compiler warnings, no new failing tests, no new entity-mapping exceptions are introduced. The pre-existing CI / `mvn verify` pipeline passes on the post-migration branch with the same set of pass/fail outcomes it had pre-migration.

## Assumptions

- All resolved decisions in the §Clarifications section are locked and will not be revisited during plan.md or tasks.md drafting. Any subsequent change requires re-opening this spec.
- The constitutional principles (especially I, XI, XIII, XV, XVIII, XXIII) cover the alignment work without amendment; the small clarifications proposed in the earlier review (constitution v2.0.1 patch) are tracked separately and not blocking.
- `Docs/eta-receipt-sdk-v1-2-alignment.md`, `Docs/zatca-spec-alignment.md`, and `Docs/sprint-1-ingestion-gateway-implementation-plan.md` (in their 2026-05-25 locked-decision state) are the authoritative design inputs. plan.md MUST reference them rather than re-stating their content.
- The Fatoora SDK v2.0.3 (CLI + UBL XSDs + schematron) is the validation oracle for ZATCA. ETA's pre-production endpoint (`api.preproduction.invoicing.eta.gov.eg`) is the validation oracle for ETA Receipt v1.2.
- ETA Invoice (V48/V49) is aligned and out of scope; no further verification is planned in this feature beyond the 2026-05-25 spot check.
- Wave 8 code paths (ZATCA serialiser, signing engine, submission orchestrator) are open for modification in this feature. The PR scope explicitly includes them.
- specs/011-ingestion-gateway is a **downstream consumer**, not a co-feature. Its work begins only after the migrations in this feature have landed and been verified on staging.
- The platform is pre-production (no real-customer data). Backfills may be aggressive; CHECK constraints may be added in the same migration as the backfill (rather than `NOT VALID + VALIDATE CONSTRAINT` two-step) because dev/staging downtime is acceptable.
