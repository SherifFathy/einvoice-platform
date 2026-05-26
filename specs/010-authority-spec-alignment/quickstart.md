# Quickstart — Feature 010 Authority Spec Alignment

## Audience

Backend developer about to pick up the V58–V61 PR sequence.

## Reading order before you touch code

1. **[`spec.md`](./spec.md)** — Locked feature spec with 24 clarifications. Pay attention to:
   - Clarifications session 2026-05-26 (smart-app/dumb-DB, manual scripts, 4 PRs, scope boundary to specs/011)
   - FR-001 to FR-021 (the migration contracts)
   - SC-001 to SC-009 (the measurable acceptance criteria)
2. **[`deferred-validation.md`](./deferred-validation.md)** — Catalogue of `CHECK` / `UNIQUE` / `FK` / `NOT NULL` deferred to the service layer. **Read this before writing any entity.**
3. **[`Docs/zatca-spec-alignment.md`](../../Docs/zatca-spec-alignment.md)** §10 + §12 — Target ZATCA Standard schema + UBL XPath ↔ column reference card.
4. **[`Docs/eta-receipt-sdk-v1-2-alignment.md`](../../Docs/eta-receipt-sdk-v1-2-alignment.md)** §10 + §12 — Target ETA Receipt v1.2 payload + JSON path ↔ column ↔ Java field reference card.
5. **[`data-model.md`](./data-model.md)** — Per-migration entity diffs.
6. **[`contracts/migrations.md`](./contracts/migrations.md)** — Per-file contracts and manual-script contracts.

## Per-PR workflow

### PR 1 — V58 (header additions + party promotion + ETA receipt restructure)

1. Branch from `010-authority-spec-alignment`.
2. Write `backend/src/main/resources/db/migration/V58__zatca_eta_header_additions.sql`. Use the inline-comment convention:
   ```sql
   ADD COLUMN seller_vat_number CHAR(15)  -- VALIDATION (deferred): seller_vat_number ~ '^3[0-9]{13}3$' — see deferred-validation.md §V58.A.1 (BR-KSA-40)
   ```
3. Update entities:
   - `ZatcaStandardHeader`: add 9 new scalar fields + 16 promoted party fields.
   - `ZatcaSimplifiedHeader`: parallel additions (buyer columns all nullable).
   - `EtaReceiptHeader`: rename `totalDiscountAmount` → `totalCommercialDiscount`; add 13 new fields.
4. Update serialisers:
   - `ZatcaUblSerialiser`: emit `cbc:ProfileID`, `cbc:Note`, payment means, the second `cac:TaxTotal/cbc:TaxAmount` (BT-111), `cbc:PayableRoundingAmount`.
   - `EtaReceiptSerialiser`: nest under `header {}`, move POS/activity fields into `seller`, remove the `payment` and `delivery` nested objects per the v1.2 SDK.
5. `mvn flyway:migrate` against local PostgreSQL. Confirm `RAISE NOTICE` lines for any malformed legacy JSONB values.
6. `mvn verify` — update golden-file tests with new expected UBL/JSON output.
7. Locally verify with the manual script (see **Verification** below).
8. Open PR. Reviewer checklist:
   - Backfill correctness (every `UPDATE` covers every existing row).
   - Entity-field naming matches mapping reference cards (no aliases).
   - Golden-file tests updated for both Standard and Simplified.
   - Inline-comment convention applied per deferred rule.
9. **Wait for staging deployment + smoke test** before opening PR 2.

### PR 2 — V59 (ZATCA sub-tables)

1. Write `V59__zatca_subtotals_and_allowances.sql`.
2. Create new entities: `ZatcaStandardTaxSubtotal`, `ZatcaSimplifiedTaxSubtotal`, `ZatcaStandardAllowance`, `ZatcaSimplifiedAllowance`. JPA `@OneToMany` on headers (`cascade = ALL`, `orphanRemoval = true`).
3. Update `ZatcaUblSerialiser` to emit `cac:TaxTotal/cac:TaxSubtotal` blocks (one per VAT category) and document-level `cac:AllowanceCharge` blocks.
4. Add `allowanceTotal()` derived getter on header entities. Remove the now-unused stored field setter.
5. Run migration locally. Confirm:
   - Existing rows have non-zero `tax_subtotals` aggregated from lines.
   - Rows with `allowance_total_amount > 0` have a synthesised allowance row with `reason = 'migrated-from-aggregate'`.
   - `allowance_total_amount` column is gone from both header tables.
6. **Add unit tests** asserting service-layer enforcement of `UNIQUE(header_id, vat_category_code, vat_rate)` per `deferred-validation.md` §V59.A.1 — the test creates two subtotals with the same key and expects a service-layer rejection (not a DB error).
7. Open PR after staging-verify of PR 1.

### PR 3 — V60 (line-block + line-allowance + ETA receipt line restructure)

1. Write `V60__line_block_and_allowances.sql`.
2. Update `ZatcaStandardLine` / `ZatcaSimplifiedLine`:
   - Rename `unitPrice` → `itemNetPrice` (mind the wide blast radius — every controller / test / serialiser reference).
   - Add `itemGrossPrice`, `itemPriceDiscount`, `itemPriceBaseQuantity`, `itemPriceBaseQuantityUnit`, `vatInclusiveAmount`.
   - Drop `discountAmount`, `allowanceAmount`.
3. Create `ZatcaStandardLineAllowance` / `ZatcaSimplifiedLineAllowance` entities.
4. Update `ZatcaUblSerialiser.line()`:
   - `cac:Price/cbc:PriceAmount` (BT-146)
   - `cac:Price/cbc:BaseQuantity` (BT-149)
   - `cac:Price/cac:AllowanceCharge` (BT-147/148)
   - Line-level `cac:AllowanceCharge` blocks from the new child table.
   - `cac:TaxTotal/cbc:RoundingAmount` (KSA-12).
5. Update `EtaReceiptLine`:
   - Drop `unitValue`, `discountRate`, `discountAmount`, `itemsDiscount`.
   - Add `unitPrice` scalar + `commercialDiscountData`, `itemDiscountData` JSONB.
6. Update `EtaReceiptSerialiser.line()` to emit `unitPrice` scalar + discount-data arrays per v1.2 SDK §10.
7. Open PR after staging-verify of PR 2.

### PR 4 — V61 (signature artifacts + best-effort backfill)

1. Write `V61__signature_artifacts.sql` including the mandatory inline comment (see `contracts/migrations.md` V61 contract item 3).
2. Add 4 fields to `ZatcaStandardHeader` and `ZatcaSimplifiedHeader`. Use plain `UUID`/`String` Java types — there is **no JPA `@ManyToOne`** to `ZatcaConfig` or `InvoiceArtifact` (no FK).
3. Update `ZatcaSigningService` (or equivalent submission orchestrator):
   - On successful submission, persist `cryptographicStampValue`, `signedXmlArtifactId`, `zatcaConfigId`, `signedAt`.
   - State-machine guard for `ZatcaSimplifiedHeader` enforcing BR-KSA-60.
4. After migration, verify the Flyway log lists the count of rows where `zatca_config_id` remained NULL (legacy Wave 7/8 unresolvable).
5. Open PR after staging-verify of PR 3.

## Verification (manual, post-PR-merge)

### Fatoora CLI

**Prerequisite:** Fatoora SDK v2.0.3 installed locally; `fatoora` on PATH.

```powershell
# Serialise a known-good DB row to UBL XML using the platform's serialiser
mvn -pl backend exec:java `
    -Dexec.mainClass='com.<your-package>.verify.SerialiseStandardInvoice' `
    -Dexec.args='<header-id>' > sample.xml

# Validate
.\scripts\verify\fatoora-validate.ps1 -Invoice sample.xml
# Expected: "Zero BR-KSA-* errors. See sibling .log file for full output."
```

Run for each of the four canonical test cases:
1. Standard with single S-rated line
2. Standard with E-exempt line + exemption reason
3. Standard credit note with original-invoice reference
4. Standard with multi-currency totals

Plus one Simplified test case with cryptographic stamp (BR-KSA-60).

### ETA Receipt v1.2 pre-production

**Prerequisite:** ETA pre-production credentials in your local env (`ETA_PREPROD_CLIENT_ID`, `ETA_PREPROD_CLIENT_SECRET`).

```powershell
.\scripts\verify\eta-receipt-submit.ps1 -Sample scripts\verify\samples\receipt-single-line.json
# Expected: HTTP 200 (or business-content rejection — NOT structural rejection per SC-003).
# Response written to sibling .log file.
```

## Common pitfalls

1. **Forgetting to drop `unit_value` JSONB in V60** — causes ETA Receipt v1.2 SDK rejection because the same logical field exists in two shapes. Ensure the DROP runs AFTER the `unit_price` backfill within V60.
2. **Renaming `unit_price` → `item_net_price` without updating Wave 8 tests** — golden-file XML tests reference the field. Update test fixtures in the same PR.
3. **Backfilling `tax_amount_accounting_currency` blindly across all rows** — the V58 backfill explicitly handles only `currency = 'SAR'`. For non-SAR rows, the service layer is responsible (per `deferred-validation.md` §V58.D.2). Don't `UPDATE … SET tax_amount_accounting_currency = tax_amount` for non-SAR rows in the migration.
4. **Service-layer dedupe for `tax_subtotals`** — the `UNIQUE` constraint is deferred. Two rows with the same `(header, category, rate)` will silently land in the DB and Fatoora WILL reject the resulting UBL. The service MUST dedupe before insert.
5. **`zatca_config_id` is a plain UUID column with no FK** — do not assume PostgreSQL will reject orphan references. The service MUST validate the lookup at submission time.
6. **JPA `@ManyToOne` on signature columns** — the V61 columns are plain `UUID`. Do **not** map them as `@ManyToOne ZatcaConfig zatcaConfig` — that would imply a DB FK which is explicitly deferred.
7. **Migration "estimated runtime <30s"** — verify on a staging-shaped dataset (≥1 row per affected table). If your local DB is empty, the migration is misleadingly fast.

## Out of scope (do NOT touch in this feature)

- `eta_invoice_headers` / `eta_invoice_lines` / `eta_invoice_line_taxes` — verified aligned per spec.md FR-022.
- `zatca_configs`, `zatca_chain_state`, `audit_logs`, `invoice_artifacts`, `submission_attempts` — unchanged.
- ZATCA hash chain mechanism — V61 only adds signature artifact storage; the chain itself is untouched.
- Any new UI screen or business workflow — UI changes are limited to reading promoted columns and surfacing the signed-XML link (FR-023).

## Reference

- Spec: [`./spec.md`](./spec.md)
- Design inputs:
  - [`Docs/zatca-spec-alignment.md`](../../Docs/zatca-spec-alignment.md)
  - [`Docs/eta-receipt-sdk-v1-2-alignment.md`](../../Docs/eta-receipt-sdk-v1-2-alignment.md)
  - [`Docs/sprint-1-ingestion-gateway-implementation-plan.md`](../../Docs/sprint-1-ingestion-gateway-implementation-plan.md) §10
- Deferred rules: [`./deferred-validation.md`](./deferred-validation.md)
- Per-file contracts: [`./contracts/migrations.md`](./contracts/migrations.md)
- Per-migration entity diffs: [`./data-model.md`](./data-model.md)
- Constitution: [`../../.specify/memory/constitution.md`](../../.specify/memory/constitution.md) (Principle XIII: Validation Layering)
