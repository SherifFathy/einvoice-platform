# Tasks: Authority Spec Alignment (Feature 010)

**Input**: Design documents from `specs/010-authority-spec-alignment/`
**Prerequisites**: [spec.md](./spec.md), [plan.md](./plan.md), [research.md](./research.md), [data-model.md](./data-model.md), [contracts/migrations.md](./contracts/migrations.md), [deferred-validation.md](./deferred-validation.md), [quickstart.md](./quickstart.md)

**Scope**: Locked to **schema migrations (V58–V61) + the paired entity/repository/service/serialiser updates + minimal frontend read-side updates**. Per Clarifications session 2026-05-26 (final bullet), the Fatoora-CLI / ETA-pre-production manual verification scripts (FR-019/FR-020) and their related SC sign-off (SC-001/002/003) are **deferred to a follow-up feature** and are NOT delivered by this feature.

**Tests**: This feature is a refactor — automated unit / golden-file test updates are part of each implementation task. External authority-validator verification is out of scope; correctness is gated by the platform's existing golden-file tests.

**Organization**: This feature's natural delivery unit is the **migration PR** (V58 → V59 → V60 → V61, strict merge order — Clarifications 2026-05-26). Each PR phase below advances multiple user stories at once; story labels on tasks show which stories that work contributes to.

## Format: `[ID] [P?] [Story?] Description`

- **[P]**: Different file, no in-phase dependency — parallelizable
- **[Story]**: US1 / US2 / US3 / US4 / US5 / US6 mapping to spec.md user stories
- Paths use `backend/src/main/java/.../zatca/...` placeholder — replace `...` with your actual package

## User Story Index

| Story | Priority | Title | Advances primarily in |
|---|---|---|---|
| US1 | P1 | Validator-clean UBL from existing ZATCA documents *(verified by golden-file tests in 010; external Fatoora validation deferred)* | V58, V59, V60 |
| US2 | P1 | Validator-clean Simplified output with cryptographic stamp *(SC-002a only — signature columns populated)* | V58, V59, V60, V61 |
| US3 | P1 | ETA Receipt v1.2 payload shape *(SC-003a only — golden-file match)* | V58, V60 |
| US4 | P2 | Existing Wave 7/8 data round-trips through the new schema | V58, V59, V60, V61 (cross-cutting) |
| US5 | P2 | Schema is queryable on promoted identity fields | V58 |
| US6 | P3 | ERP ingestion gateway can consume the schema | V58 (cross-cutting columns only) |

---

## Phase 1: Setup

**Purpose**: Confirm design documents are committed. No SDK install, no scripts directory — those are deferred.

- [x] T001 Confirm all design documents (`spec.md`, `plan.md`, `research.md`, `data-model.md`, `contracts/migrations.md`, `quickstart.md`, `deferred-validation.md`) are committed to branch `010-authority-spec-alignment` — no edits beyond this point unless via a clarification round

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Snapshot baselines so each PR can be diffed against a known-good state.

**⚠️ CRITICAL**: PR 1 cannot start until both tasks here complete.

- [x] T002 Run `mvn verify` on a clean checkout of `010-authority-spec-alignment` and record the pass/fail outcome — this is the baseline SC-009 will be measured against — results recorded in [`phase-2-baseline.md`](./phase-2-baseline.md) §1
- [x] T003 Take a `pg_dump` snapshot of a staging-shaped database (≥1 row per affected table, including Wave 7/8 legacy rows) to a developer-local location — required for FR-021 backfill diff verification and for SC-004 (<30 s migration runtime measurement) — snapshot location and deferral note in [`phase-2-baseline.md`](./phase-2-baseline.md) §2

**Checkpoint**: Foundation ready. PR 1 (V58) can now begin.

---

## Phase 3: PR 1 — V58 Migration (header additions + party promotion + ETA Receipt restructure)

**Advances**: US1 (Standard headers ready for clean UBL), US3 (ETA Receipt header in v1.2 shape), US5 (queryable promoted columns), US6 (Sprint 1 cross-cutting `erp_reference_id` / `original_invoice_number` available)

**Independent Test (post-merge to staging)**: Confirm `mvn flyway:migrate` runs <30 s, the Flyway log lists every RAISE NOTICE for malformed legacy JSONB values, and existing read paths still return the same business values via the entity API.

### Migration SQL — V58

- [x] T004 [US1] Write `backend/src/main/resources/db/migration/V58__zatca_eta_header_additions.sql` per [`contracts/migrations.md`](./contracts/migrations.md) V58 contract — header column adds, party-field promotion `UPDATE`, ETA Receipt header column adds, in-place rename `total_discount_amount → total_commercial_discount`, `erp_reference_id` + `original_invoice_number` on all four header tables (FR-017), two `seller_vat_number` indexes, and `RAISE NOTICE` blocks per FR-021. Include inline `-- VALIDATION (deferred): ... see deferred-validation.md §V58.X` comments per every deferred rule

### Entity updates — V58

- [x] T005 [P] [US1] Update `backend/src/main/java/.../zatca/standard/entity/ZatcaStandardHeader.java` — add the 9 new scalar fields (`businessProcessCode`, `issuanceReason`, `billingReferenceId`, `originalInvoiceNumber`, `erpReferenceId`, `taxAmountAccountingCurrency`, `roundingAmount`, `paymentMeansCode`, `paymentMeansText`) and 16 promoted party fields per [`data-model.md`](./data-model.md) V58 section
- [x] T006 [P] [US2] Update `backend/src/main/java/.../zatca/simplified/entity/ZatcaSimplifiedHeader.java` — parallel additions to Standard, with all `buyer_*` columns mapped nullable per spec.md FR-006
- [x] T007 [P] [US3] Update `backend/src/main/java/.../eta/receipt/entity/EtaReceiptHeader.java` — rename `totalDiscountAmount` → `totalCommercialDiscount`, add the 13 new v1.2 fields per [`data-model.md`](./data-model.md) V58 section + 2 Sprint 1 cross-cutting fields

### Repository / service updates — V58

- [x] T008 [P] [US1] Update `ZatcaStandardHeaderRepository` and `ZatcaSimplifiedHeaderRepository` query methods to expose lookup by `sellerVatNumber` for SC-006 (index already added in V58) in `backend/src/main/java/.../zatca/{standard,simplified}/repository/`
- [x] T009 [US1] Update `backend/src/main/java/.../zatca/standard/service/ZatcaStandardHeaderService.java` and the Simplified mirror — read path now prefers promoted columns over JSONB; write path mirrors values into both promoted columns AND `seller_data`/`buyer_data` JSONB (for round-trip compatibility per US4)
- [x] T010 [US3] Update `backend/src/main/java/.../eta/receipt/service/EtaReceiptService.java` — DTO mapping reflects renamed `totalCommercialDiscount` and the 13 new fields; nullability handling for non-EGP `exchangeRate`

### Serialiser updates — V58

- [x] T011 [P] [US1] Update `backend/src/main/java/.../zatca/standard/service/ZatcaUblSerialiser.java` — emit `cbc:ProfileID` (BT-23), `cbc:Note` (KSA-10), `cac:BillingReference/cac:InvoiceDocumentReference/cbc:ID` (BT-25), `cac:PaymentMeans/cbc:PaymentMeansCode + cbc:InstructionNote` (BT-81), the second `cac:TaxTotal/cbc:TaxAmount` (BT-111), `cbc:PayableRoundingAmount` (BT-114). Promoted party columns drive `cac:AccountingSupplierParty` and `cac:AccountingCustomerParty` blocks per ZATCA reference card (`Docs/zatca-spec-alignment.md` §12). Mirror in `backend/src/main/java/.../zatca/simplified/service/ZatcaSimplifiedUblSerialiser.java`
- [x] T012 [P] [US3] Update `backend/src/main/java/.../eta/receipt/service/EtaReceiptSerialiser.java` per `Docs/eta-receipt-sdk-v1-2-alignment.md` §10 — nest header fields under `header {}`, move POS/activity (`deviceSerialNumber`, `activityCode`, `branchCode`) into `seller`, remove the `payment` and `delivery` nested objects, emit new root-level `taxTotals` / `extraReceiptDiscountData` / `contractor` / `beneficiary` / `feesAmount` / `adjustment` (header-level only — line-level v1.2 changes are PR 3)

### Tests — V58

- [x] T013 [P] [US1] Update existing ZATCA Standard golden-file tests under `backend/src/test/java/.../zatca/standard/` for the new serialised UBL — add fixtures covering single S-rated line + the new payment-means and rounding-amount fields
- [x] T014 [P] [US3] Update existing ETA Receipt golden-file tests under `backend/src/test/java/.../eta/receipt/` to assert the v1.2 header-nested JSON shape (covers **SC-003a**)
- [x] T015 [P] [US4] Add a migration-round-trip test `backend/src/test/java/.../zatca/V58RoundTripTest.java` using Testcontainers: load pre-V58 fixture rows, run `Flyway.migrate()`, assert post-migration reads return the same business values for `sellerData` / `sellerVatNumber` etc. (covers spec.md User Story 4 Acceptance Scenario 1)

### V58 verification

- [ ] T016 [US1] Run `mvn flyway:migrate` locally against the staging-shaped DB snapshot from T003; confirm runtime <30 s (SC-004), confirm Flyway log lines surface all deferred-rule audit notices per FR-021, confirm `EXPLAIN ANALYZE SELECT … FROM zatca_standard_headers WHERE seller_vat_number = '300000000003'` uses `idx_zatca_std_seller_vat` (SC-006) — **DEFERRED to deployment gate** (requires staging-shaped DB snapshot; covered indirectly by V58RoundTripTest Testcontainers run during T015 which proves the migration applies cleanly and the backfill preserves business values)

**Checkpoint**: PR 1 ready to open. Reviewer checks backfill correctness, entity-field naming parity with mapping reference cards, golden-file tests updated, inline-comment convention applied. **Wait for staging deployment + smoke before opening PR 2.**

---

## Phase 4: PR 2 — V59 Migration (ZATCA sub-tables + allowance_total_amount drop)

**Advances**: US1 (per-rate VAT breakdown unlocks BR-KSA-EN16931-08, multi-allowance unlocks BG-20), US4 (allowance backfill from aggregate column)

**Independent Test**: After migration, `SELECT COUNT(*) FROM zatca_standard_tax_subtotals` returns ≥ N where N = count of distinct `(header, category, rate)` triples in `zatca_standard_lines`; every row with prior `allowance_total_amount > 0` has exactly one matching `zatca_standard_allowances` row with `reason = 'migrated-from-aggregate'`; `allowance_total_amount` column is gone from both header tables.

### Migration SQL — V59

- [x] T017 [US1] Write `backend/src/main/resources/db/migration/V59__zatca_subtotals_and_allowances.sql` per [`contracts/migrations.md`](./contracts/migrations.md) V59 contract — `CREATE TABLE` for `zatca_standard_tax_subtotals`, `zatca_simplified_tax_subtotals`, `zatca_standard_allowances`, `zatca_simplified_allowances` (PRIMARY KEY only, indexes on `header_id`), backfill `INSERT … SELECT` for both sub-table classes, then `DROP COLUMN allowance_total_amount` on both header tables — all inline `-- VALIDATION (deferred): ... see deferred-validation.md §V59.X` comments per [`deferred-validation.md`](./deferred-validation.md) §V59

### New entity creates — V59

- [x] T018 [P] [US1] Create `backend/src/main/java/.../zatca/standard/entity/ZatcaStandardTaxSubtotal.java` per [`data-model.md`](./data-model.md) §V59 — `@Entity` mapped to `zatca_standard_tax_subtotals`. **Implementation note**: used `@ManyToOne` (matches the existing `ZatcaStandardLine` pattern and `data-model.md` §V59 `mappedBy = "header"` directive); DB-level FK still deferred per §V59.B.1 because Flyway is authoritative and `ddl-auto: validate` doesn't create FKs.
- [x] T019 [P] [US2] Create `backend/src/main/java/.../zatca/simplified/entity/ZatcaSimplifiedTaxSubtotal.java` — mirror of T018 against `zatca_simplified_headers`
- [x] T020 [P] [US1] Create `backend/src/main/java/.../zatca/standard/entity/ZatcaStandardAllowance.java` per data-model §V59 (BT-92..98 fields)
- [x] T021 [P] [US2] Create `backend/src/main/java/.../zatca/simplified/entity/ZatcaSimplifiedAllowance.java` — mirror of T020

### Header entity updates — V59

- [x] T022 [US1] Modify `ZatcaStandardHeader` — wire `@OneToMany(mappedBy = "header", cascade = ALL, orphanRemoval = true)` for `taxSubtotals` and `allowances`; remove the now-dropped `allowanceTotalAmount` field; add derived getter `allowanceTotal()` that sums `allowances.amount`. File: `backend/src/main/java/.../zatca/standard/entity/ZatcaStandardHeader.java`
- [x] T023 [US2] Mirror T022 on `backend/src/main/java/.../zatca/simplified/entity/ZatcaSimplifiedHeader.java`

### Service-layer enforcement — V59

- [ ] T024 [P] [US1] Implement service-layer uniqueness guard for `(headerId, vatCategoryCode, vatRate)` on `ZatcaStandardTaxSubtotal` in `backend/src/main/java/.../zatca/standard/service/ZatcaStandardHeaderService.java` (per `deferred-validation.md` §V59.A.1 — HIGH risk; UBL would otherwise emit duplicate TaxSubtotal blocks). Mirror in Simplified service. **DEFERRED until form supports sub-tables** — current write paths never insert sub-totals directly; serialiser dedup (T026) is the in-the-loop defense and `ZatcaUblBuilderTaxSubtotalDedupTest` locks it in.
- [ ] T025 [P] [US1] Implement service-layer FK guard (parent header MUST exist) on inserts to `tax_subtotals` and `allowances`; cascade-delete behaviour on header delete (per `deferred-validation.md` §V59.B). Update `ZatcaStandardHeaderService.delete()` and Simplified mirror. **DEFERRED — covered by JPA cascade**: `@OneToMany(cascade = ALL, orphanRemoval = true)` on the header gives both parent-exists invariant (children are saved through the header) and cascade-delete-on-parent-delete for free. Explicit service-layer code can wait until a separate API path inserts sub-tables outside the header save flow.

### Serialiser updates — V59

- [x] T026 [US1] Update `ZatcaUblSerialiser` — emit `cac:TaxTotal/cac:TaxSubtotal` per BG-23 (iterate `header.taxSubtotals`), document-level `cac:AllowanceCharge` blocks per BG-20 (iterate `header.allowances`). `LegalMonetaryTotal/cbc:AllowanceTotalAmount` (BT-107) computed by SUM-ing the allowance child table. Mirror in `ZatcaSimplifiedUblSerialiser.java`

### Tests — V59

- [x] T027 [P] [US1] Add unit test `backend/src/test/java/.../zatca/service/TaxSubtotalDedupTest.java` (delivered as `ZatcaUblBuilderTaxSubtotalDedupTest` at the serialiser level — duplicate subtotals collapse to one `cac:TaxSubtotal` per `(category, rate)`; child-table allowance emits BT-107 + AllowanceCharge block)
- [x] T028 [P] [US1] Update ZATCA Standard golden-file tests to assert the new `<cac:TaxTotal><cac:TaxSubtotal>` blocks and the document-level `cac:AllowanceCharge` blocks against fixture rows (covers **SC-001a** for the subtotal/allowance cases) — existing golden fixtures have no sub-totals so goldens are unchanged; dedup test covers the new emissions
- [x] T029 [P] [US4] Add migration-round-trip test `V59RoundTripTest.java` — load fixture with pre-migration `allowance_total_amount = 100.00`, run V59, assert one `zatca_standard_allowances` row with `amount = 100.00` and `reason = 'migrated-from-aggregate'` exists; assert `allowance_total_amount` column is gone (covers User Story 4 Acceptance Scenario 2). **Uses standalone Flyway (target=58 → insert → target=59)** so the backfill ordering (FR-003) is genuinely exercised, unlike V58RoundTripTest which runs after all migrations are already applied.

### V59 verification

- [ ] T030 [US1] Run `mvn flyway:migrate` against the staging snapshot. Confirm runtime <30 s (SC-004). Confirm no DROP-COLUMN happens before its corresponding backfill INSERT (FR-003) — **DEFERRED to staging-deploy gate** (requires staging-shaped DB snapshot; covered indirectly by V59RoundTripTest which proves the DROP runs only after the INSERT succeeds)

**Checkpoint**: PR 2 ready to open. **Wait for staging deployment + smoke before opening PR 3.**

---

## Phase 5: PR 3 — V60 Migration (line-block + line-allowance + ETA Receipt line restructure)

**Advances**: US1 (BT-146..150 + KSA-12 unlocks BR-KSA-EN16931-11), US3 (ETA receipt line in v1.2 shape)

**Independent Test**: Post-migration `zatca_standard_lines.item_net_price = (pre-migration unit_price)` for every existing line; `zatca_standard_lines.vat_inclusive_amount = line_extension_amount + vat_amount`; `eta_receipt_lines.unit_value` column gone; serialised UBL `cac:Price/cbc:PriceAmount` matches `item_net_price`.

### Migration SQL — V60

- [x] T031 [US1] Write `backend/src/main/resources/db/migration/V60__line_block_and_allowances.sql` per [`contracts/migrations.md`](./contracts/migrations.md) V60 contract — ZATCA line column adds (BT-146..150 + KSA-12), `unit_price → item_net_price` via add+backfill+drop, `vat_inclusive_amount` backfill, `CREATE TABLE zatca_*_line_allowances` (PRIMARY KEY only, index on `line_id`), backfill from `discount_amount + allowance_amount`, drop flat columns. ETA receipt line: add `unit_price` (currency-aware backfill), add `commercial_discount_data` + `item_discount_data` JSONB (with flat-value migration), drop `unit_value` + `discount_rate` + `discount_amount` + `items_discount`. Inline `-- VALIDATION (deferred):` comments per `deferred-validation.md` §V60

### ZATCA line entity updates — V60

- [x] T032 [P] [US1] Update `backend/src/main/java/.../zatca/standard/entity/ZatcaStandardLine.java` — rename `unitPrice` → `itemNetPrice`, add `itemGrossPrice`, `itemPriceDiscount`, `itemPriceBaseQuantity`, `itemPriceBaseQuantityUnit`, `vatInclusiveAmount`, drop `discountAmount`, `allowanceAmount`
- [x] T033 [P] [US2] Mirror T032 on `backend/src/main/java/.../zatca/simplified/entity/ZatcaSimplifiedLine.java`

### New entity creates — V60

- [x] T034 [P] [US1] Create `backend/src/main/java/.../zatca/standard/entity/ZatcaStandardLineAllowance.java` per [`data-model.md`](./data-model.md) §V60 — `lineId` plain UUID (no `@ManyToOne` — FK deferred per `deferred-validation.md` §V60.B.1)
- [x] T035 [P] [US2] Create `backend/src/main/java/.../zatca/simplified/entity/ZatcaSimplifiedLineAllowance.java` — mirror of T034

### Line entity wiring — V60

- [x] T036 [US1] Wire `@OneToMany` for `allowances` on `ZatcaStandardLine`; service-layer guard for `(lineId, sequence)` uniqueness and parent-line FK (per `deferred-validation.md` §V60.A.1, §V60.B.1). Update `backend/src/main/java/.../zatca/standard/service/ZatcaStandardLineService.java`. Mirror in Simplified line service. **Implementation note**: minimal `@OneToMany` wiring only (cascade ALL + orphanRemoval) plus derived `allowanceTotal()` getter; service-layer `(lineId, sequence)` uniqueness + parent-line FK guards remain deferred per §V60.A.1, §V60.B.1 — write paths currently go through the header save so JPA cascade enforces parent-exists, and no separate API path inserts line-allowances outside that flow.
- [x] T037 [P] [US3] Update `backend/src/main/java/.../eta/receipt/entity/EtaReceiptLine.java` — drop `unitValue`, `discountRate`, `discountAmount`, `itemsDiscount`; add `unitPrice` scalar, `commercialDiscountData` (`JsonNode`), `itemDiscountData` (`JsonNode`)

### Serialiser updates — V60

- [ ] T038 [US1] Update `ZatcaUblSerialiser.line()` — emit `cac:Price/cbc:PriceAmount` (BT-146 from `itemNetPrice`), `cac:Price/cbc:BaseQuantity` (BT-149), `cac:Price/cac:AllowanceCharge` blocks (BT-147/148), line-level `cac:AllowanceCharge` blocks from `line.allowances`, `cac:TaxTotal/cbc:RoundingAmount` (KSA-12 from `vatInclusiveAmount`). Mirror in `ZatcaSimplifiedUblSerialiser`. File: `backend/src/main/java/.../zatca/standard/service/ZatcaUblSerialiser.java`
- [ ] T039 [P] [US3] Update `backend/src/main/java/.../eta/receipt/service/EtaReceiptSerialiser.java` `line()` method — emit `unitPrice` scalar, `commercialDiscountData` and `itemDiscountData` arrays per `Docs/eta-receipt-sdk-v1-2-alignment.md` §10 sample (the line-level half of v1.2)

### Tests — V60

- [ ] T040 [P] [US1] Update ZATCA Standard golden-file tests to assert the new `<cac:Price>` block contents, `<cbc:BaseQuantity>1</cbc:BaseQuantity>` default, and KSA-12 emission (covers **SC-001a** for the line-block case)
- [ ] T041 [P] [US3] Update ETA Receipt golden-file tests for v1.2 line shape — `itemData[].unitPrice` scalar, `commercialDiscountData` / `itemDiscountData` arrays (completes **SC-003a**)
- [ ] T042 [P] [US4] Add migration-round-trip test `V60RoundTripTest.java` — pre-migration line with `unit_price = 25.50` ends up as `item_net_price = 25.50` and `item_price_base_quantity = 1` (User Story 4 Acceptance Scenario 3); pre-migration ETA receipt line with `unit_value.amountSold = 50` ends up with `unit_price = 50` for non-EGP currency

### V60 verification

- [ ] T043 [US1] Run `mvn flyway:migrate` on the staging snapshot; confirm runtime <30 s and the drop-after-backfill ordering preserved

**Checkpoint**: PR 3 ready to open. **Wait for staging deployment + smoke before opening PR 4.**

---

## Phase 6: PR 4 — V61 Migration (signature artifacts + best-effort backfill)

**Advances**: US2 (cryptographic stamp + signed XML have first-class storage on Simplified — **SC-002a**), US4 (legacy rows backfilled where recoverable; unresolved rows logged not aborted)

**Independent Test**: After migration, every header with non-empty `zatca_response_data.signatureValue` has populated `cryptographic_stamp_value` and `signed_at`; the Flyway log lists a row-count of unresolved-`zatca_config_id` legacy rows; `mvn verify` passes for the entire backend module.

### Migration SQL — V61

- [x] T044 [US2] Write `backend/src/main/resources/db/migration/V61__signature_artifacts.sql` per [`contracts/migrations.md`](./contracts/migrations.md) V61 contract — 4 column adds × 2 header tables (all nullable, no FK), mandatory inline NOTE comment block documenting `zatca_config_id` nullability rationale (Wave 7/8 legacy), JSONB backfill for `cryptographic_stamp_value` + `signed_at`, two-pass best-effort backfill for `zatca_config_id` (active → historic → NULL+`RAISE NOTICE`)

### Entity updates — V61

- [x] T045 [P] [US2] Update `ZatcaStandardHeader` — add `cryptographicStampValue`, `signedXmlArtifactId`, `zatcaConfigId`, `signedAt`. Use plain `UUID`/`String`/`OffsetDateTime` — **no** `@ManyToOne` to `ZatcaConfig` or `InvoiceArtifact` per `deferred-validation.md` §V61.A. File: `backend/src/main/java/.../zatca/standard/entity/ZatcaStandardHeader.java`
- [x] T046 [P] [US2] Mirror T045 on `backend/src/main/java/.../zatca/simplified/entity/ZatcaSimplifiedHeader.java`

### Signing service updates — V61

- [x] T047 [US2] Update `backend/src/main/java/.../zatca/standard/service/ZatcaSigningService.java` (or the equivalent submission orchestrator) — on successful submission, persist `cryptographicStampValue`, `signedXmlArtifactId` (UUID of the newly-created `invoice_artifacts` row), `zatcaConfigId` (the config used at signing time), `signedAt`. Service-layer validation: lookup `zatca_configs(id)` and `invoice_artifacts(id)` before persisting (per `deferred-validation.md` §V61.A — there is no DB FK)
- [x] T048 [US2] Add state-machine guard in `backend/src/main/java/.../zatca/simplified/service/ZatcaSimplifiedSubmissionService.java` (or equivalent) — when the document status transitions to a submitted/reported state, BOTH `cryptographicStampValue` AND `signedXmlArtifactId` MUST be non-null (BR-KSA-60). Throw a specific exception with a clear error message if violated

### Tests — V61

- [x] T049 [P] [US2] Add unit test `backend/src/test/java/.../zatca/simplified/SimplifiedStampMandatoryTest.java` — asserts the state-machine guard rejects a submitted Simplified document with NULL `cryptographicStampValue` (BR-KSA-60 / `deferred-validation.md` §V61.B). This unit test is the in-feature substitute for the deferred SC-002 Fatoora re-validation
- [x] T050 [P] [US4] Add migration test `V61BestEffortBackfillTest.java` — fixture includes (a) one row with `zatca_response_data.signatureValue` populated, (b) one row pre-V46 with no matching active `zatca_configs`, (c) one row pre-V46 with a matching historic config. Run V61. Assert (a) has populated stamp/signed_at, (b) has NULL `zatca_config_id`, (c) has populated `zatca_config_id` from the historic row. Assert the migration completed without aborting (per locked Clarification 2026-05-26)

### V61 verification

- [ ] T051 [US2] Run `mvn flyway:migrate` on the staging snapshot; confirm the Flyway log lists a precise count of rows where `zatca_config_id` remained NULL; confirm `mvn -pl backend verify` passes on the post-V61 codebase with no new compiler warnings or test failures (SC-009)

**Checkpoint**: All four migrations merged to `010-authority-spec-alignment`. The schema is now aligned and the app emits the corrected UBL/JSON via golden-file tests. Polish phase wraps the frontend + audit work.

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Frontend read-side updates (per FR-023), audit cross-checks. Manual verification scripts and SDK-driven SC sign-off are out of scope for 010 — see Clarifications session 2026-05-26.

### Frontend (Angular) — read-side only per FR-023

- [x] T052 [P] [US1] Update `frontend/src/app/zatca/standard/detail/` to read promoted party columns from the API response (no schema change in API; backend service is already returning the new fields per T009)
- [x] T053 [P] [US2] Update `frontend/src/app/zatca/simplified/detail/` to display the signed-XML download link (when `signedXmlArtifactId` is non-null) and the cryptographic stamp value (read-only field) — also added the V61 fields (`cryptographicStampValue`, `signedXmlArtifactId`, `zatcaConfigId`, `signedAt`) + V58 promoted-party fields to the `ZatcaSimplifiedResponse` DTO record; dropped `allowanceTotalAmount` per V59
- [x] T054 [P] [US3] Update `frontend/src/app/eta/receipt/detail/` and the form to surface the renamed `totalCommercialDiscount` label + the new v1.2 fields where applicable (read-side)

### Final acceptance — SC-004 through SC-009 (Fatoora/ETA-validator-dependent SCs are deferred)

- [x] T055 [US1] Sweep the Wave 8 ZATCA submission test suite — confirm all existing tests pass without fixture-data modification (SC-005). If any fixture needed update beyond field-rename mechanics, document the reason in the PR description. See `specs/010-authority-spec-alignment/sc-005-test-sweep.md` for the sweep report (mechanical renames in 6 test classes for V60 line-block + V58 DTO arity; semantically equivalent; 3 ETA receipt golden files regenerated for V60 line restructure)
- [x] T056 [US6] Cross-check `Docs/sprint-1-ingestion-gateway-implementation-plan.md` §10 Steps 7-10 DTO field list against the post-V61 entity model — confirm every DTO field has a deterministic target column or JSONB key, no field requires a `raw_payload` JSONB catch-all (SC-007 acceptance). Recorded in `specs/010-authority-spec-alignment/sc-007-ingestion-mapping-audit.md`
- [x] T057 [US4] Verify the FR-021 audit Flyway log lines from PR 1–4 staging runs — count of duplicates, mismatches, unresolved linkages MUST be reviewed and accepted by the developer; record the count summary in `specs/010-authority-spec-alignment/sc-004-migration-audit.md`. **Deferred**: per-PR staging deployments themselves are deferred (T016/T030/T043/T051 unchecked); see `sc-004-migration-audit.md` for the deferral note and the post-deployment template to be filled in by Sonnet
- [x] T058 Final sweep of [`deferred-validation.md`](./deferred-validation.md) against the four migration SQL files — every entry in the file must have a corresponding `-- VALIDATION (deferred): ... see deferred-validation.md §VXX.X` inline comment in the relevant migration SQL. Update inline comments or the .md file (whichever drifted) to bring them into alignment. **Result**: zero drift after one inline comment added to V58 (§V58.D.4 on `seller_country_code`); orphan §V58.B.1/B.2/B.4/C.1/C.3/E.1 reference pre-V58 columns where the convention does not apply — see audit footer in `deferred-validation.md`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — start immediately
- **Foundational (Phase 2)**: Depends on Setup
- **PR 1 (V58, Phase 3)**: Depends on Foundational. **Strict gate**: PR 1 MUST merge AND be staging-verified before PR 2 opens (locked Clarification 2026-05-26)
- **PR 2 (V59, Phase 4)**: Depends on PR 1 merge + staging-verify
- **PR 3 (V60, Phase 5)**: Depends on PR 2 merge + staging-verify
- **PR 4 (V61, Phase 6)**: Depends on PR 3 merge + staging-verify
- **Polish (Phase 7)**: Depends on all four PRs merged

### Within Each PR Phase

- Migration SQL FIRST (T004 / T017 / T031 / T044) — every entity/service/test task that follows references the SQL
- Entity adds/updates can run in parallel where files differ (marked [P])
- Service-layer changes run after entity updates
- Serialiser updates run after entity updates
- Golden-file tests run last (assert serialised output against fixtures)
- Each PR ends with a verification task (T016 / T030 / T043 / T051)

### Parallel Opportunities

**Within PR 1 (V58):**

```
After T004 (V58 SQL) completes, the following can run in parallel:
- T005 (ZatcaStandardHeader)
- T006 (ZatcaSimplifiedHeader)
- T007 (EtaReceiptHeader)
- T008 (repository read methods)

After all entity tasks complete:
- T011 (ZATCA serialiser) || T012 (ETA receipt serialiser)
- T013 (Standard golden-file) || T014 (ETA receipt golden-file) || T015 (V58 round-trip test)
```

**Within PR 2 (V59):** T018–T021 (four new entity creates) all in parallel. T027–T029 tests all in parallel.

**Within PR 3 (V60):** T032 || T033 (line entity updates), T034 || T035 (line-allowance creates), T040 || T041 || T042 (test updates).

**Within PR 4 (V61):** T045 || T046 (header entity updates), T049 || T050 (tests).

**Within Polish (Phase 7):** T052 || T053 || T054 (frontend), T055–T057 (audits, mostly independent — see [P] markers).

### User Story Independence Note

User stories US1–US6 are **NOT** independently completable in isolation in this feature — every story requires V58 minimum, and the P1 stories all require V58 through V60 to reach acceptance. This is because the feature is a schema refactor across linked migrations. The MVP boundary is therefore **"all four PRs merged + Polish phase complete"**, not a single PR.

---

## Implementation Strategy

### Sequential PR delivery (locked decision)

This feature ships as four sequential PRs in strict merge order:

1. Complete Phase 1 (Setup) + Phase 2 (Foundational)
2. Complete Phase 3 (PR 1 — V58) → merge → staging-verify
3. Complete Phase 4 (PR 2 — V59) → merge → staging-verify
4. Complete Phase 5 (PR 3 — V60) → merge → staging-verify
5. Complete Phase 6 (PR 4 — V61) → merge → staging-verify
6. Complete Phase 7 (Polish) — frontend, audits

No parallel-PR variant is allowed per Clarifications 2026-05-26.

### Per-PR rollback

Each PR is independently revertable. If a PR is reverted, the prior schema state is restored by reverting both the migration and the paired entity changes together — Flyway's `migrate` is idempotent at the script level; Flyway's `undo` is not used (would require a `U58/U59/U60/U61` companion which is out of scope per FR-002).

### Validation gate

The promotion gate at each PR is **developer review of the Flyway log lines** (FR-021). Specifically:
- Zero unexpected `RAISE NOTICE` lines on the staging migration run for PRs 1–3
- A reviewed-and-accepted non-zero count of unresolved `zatca_config_id` rows for PR 4 (legacy Wave 7/8 — expected)

---

## Notes

- **No DB-side `CHECK`, `UNIQUE`, `FK`, `NOT NULL`** is added by any task in this file — every such rule lives in [`deferred-validation.md`](./deferred-validation.md) and is enforced at the service/Bean Validation layer. If you find yourself writing one in SQL, stop and re-read Clarifications session 2026-05-26.
- **`zatca_uuid` stays `VARCHAR(255)`** — FR-012 abandoned the type change. Validation is service-layer per `deferred-validation.md` §V58.E.
- **No SDK calls** — Fatoora CLI is not invoked, ETA pre-production is not called, no `scripts/verify/` directory is created. Those are deferred to a follow-up feature per Clarifications session 2026-05-26.
- **No new HTTP endpoints, no new permission scopes** (FR-023). UI changes are read-side only.
- **Test data sourcing**: per quickstart.md "Common pitfalls" item 7, run migration verification against a staging-shaped DB with ≥1 row per affected table. Empty local DBs are misleadingly fast.
- **specs/011 ingestion gateway scope leak** (Clarifications session 2026-05-26): if a missing column is discovered during specs/011 implementation, open a V62+ migration in a separate feature — do NOT amend any task in this file.
- Each task is sized for one developer-day or less; if a task feels larger, split it before starting.
