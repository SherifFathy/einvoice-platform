# Phase 2 Baseline — Feature 010 Authority Spec Alignment

**Recorded**: 2026-05-26
**Branch**: `010-authority-spec-alignment`
**Maven reactor**: root-level `mvn verify` (no `backend/` module exists — project uses top-level `platform-*` modules)

---

## 1. mvn verify baseline (SC-009 gate)

### Checkstyle

77 errors in `platform-api` (pre-existing from feature 009):

- `ZatcaSubmissionOrchestrator.java` — MissingJavadocMethod (×5), Indentation (×5 case children)
- `ZatcaSubmissionController.java` — MissingJavadocMethod (×9)
- `BulkCheckStatusIT.java` — CustomImportOrder (×1), Indentation (×3 case children), VariableDeclarationUsageDistance (×1)

### Test results (with Docker PostgreSQL running)

Baseline run conditions: Docker Postgres up on `localhost:5433`, `-Dcheckstyle.skip=true`, full root reactor.

```
Tests run: 269, Failures: 4, Errors: 1, Skipped: 0
```

**Note on the "53 errors → 1 error" delta vs. the earlier dry-run:** an initial dry-run before Phase 2 was completed surfaced ~53 additional `Error` rows; those originated from `@SpringBootTest` classes failing JDBC connection setup (Postgres container not yet up) plus checkstyle violations being counted as errors. Both categories are excluded from the SC-009 numeric baseline by design — checkstyle is gated separately (see end of §1) and `@SpringBootTest` JDBC failures are environment problems, not test regressions. The 4 failures + 1 error below is the authoritative SC-009 baseline.

**Failures (4):**

| Test class | Count | Root cause |
|---|---|---|
| `EtaInvoiceLifecycleIntegrationTest` | 1 | JSON path `$.state` expected `VALID` but was `ACCEPTED` (lifecycle state assertion mismatch) |
| `EtaReceiptLifecycleIntegrationTest` | 1 | Same category of lifecycle state assertion |
| `EtaInvoiceCrossEnvIsolationTest` | 1 | ETA cross-environment isolation — TODO drill-in: capture the assertion message before Phase 3 closes, so SC-009 equal-or-better comparison post-V61 is unambiguous |
| `EtaReceiptCrossEnvIsolationTest` | 1 | Same category — TODO drill-in alongside the invoice variant |

**Errors (1):**

| Test class | Count | Root cause |
|---|---|---|
| `ArtifactDownloadContractTest` | 1 | `PSQLException: column "role_name" of relation "user_company_transaction_roles" does not exist` — test fixture references a column dropped/renamed in a prior migration |

**Reactor summary:**

```
E-Invoice Platform ................................. SUCCESS
Platform Core ...................................... SUCCESS
Platform Security .................................. SUCCESS
Platform ZATCA ..................................... SUCCESS
Platform ETA ....................................... SUCCESS
Platform API ....................................... FAILURE  (4 failures + 1 error)
Platform PDF ....................................... SKIPPED
Platform Jobs ...................................... SKIPPED
```

### SC-009 comparison rule

Each PR verification task (T016/T030/T043/T051) compares its post-migration `mvn verify -Dcheckstyle.skip=true` count against this baseline. **Equal-or-better passes; worse fails.** The checkstyle 77 errors are tracked separately; feature 010 must not increase them.

---

## 2. Database snapshot (T003 / SC-004)

### Local DB state

- PostgreSQL 16 running via Docker Compose on `localhost:5433`
- Flyway at V57 (`V57__zatca_configs_base_url.sql`) — latest applied migration
- **All 7 affected tables are EMPTY** (0 rows):

| Table | Rows |
|---|---|
| `zatca_standard_headers` | 0 |
| `zatca_simplified_headers` | 0 |
| `zatca_standard_lines` | 0 |
| `zatca_simplified_lines` | 0 |
| `eta_receipt_headers` | 0 |
| `eta_receipt_lines` | 0 |
| `eta_invoice_headers` | 0 |

### pg_dump snapshot

- **Path**: `C:\Users\RIS-BDGXLS3\AppData\Local\Temp\opencode\pre-v58-snapshot-20260526-161658.sql`
- **Size**: 188 KB (schema-only in effect)
- **Contents**: Full pre-V58 schema with all 30 public tables

### Deferral note

The Clarifications 2026-05-26 promotion gate is **staging migration runs**, not local. The local DB has no Wave 7/8 legacy data, so:

- **SC-004** (<30 s migration runtime): will be trivially satisfied on empty local tables. Meaningful measurement requires staging data.
- **FR-021** (backfill diff): no legacy rows exist locally to diff against. Backfill SQL will execute against 0 rows and produce no RAISE NOTICE output.
- **Per-PR staging verification** (per Phase Dependencies in tasks.md): timing and backfill verification at T016/T030/T043/T051 are **deferred to the per-PR staging run** as specified by the Clarifications promotion gate.

---

## 3. Project structure mapping

tasks.md uses `backend/src/main/java/...` and `backend/src/main/resources/db/migration/` as placeholders. The codebase splits across `platform-core/` (domain entities + JPA repositories), `platform-zatca/` and `platform-eta/` (authority-specific builders, signers, serializers, engines), and `platform-api/` (REST controllers + orchestration services). Resolved targets per task category:

### Flyway migrations (T004 / T017 / T031 / T044)

| Placeholder | Actual path |
|---|---|
| `backend/src/main/resources/db/migration/V*.sql` | `platform-core/src/main/resources/db/migration/V*.sql` |

Last existing migration: **V57** (`V57__zatca_configs_base_url.sql`).

### Entities (T005, T006, T007, T018–T023, T032–T037, T045, T046)

All header / line / sub-table entities live in **`platform-core`**, not `platform-zatca` / `platform-eta`:

| Placeholder | Actual path |
|---|---|
| `.../zatca/standard/entity/ZatcaStandardHeader.java` | `platform-core/src/main/java/com/einvoice/core/domain/zatca/ZatcaStandardHeader.java` |
| `.../zatca/standard/entity/ZatcaStandardLine.java` | `platform-core/src/main/java/com/einvoice/core/domain/zatca/ZatcaStandardLine.java` |
| `.../zatca/simplified/entity/ZatcaSimplifiedHeader.java` | `platform-core/src/main/java/com/einvoice/core/domain/zatca/ZatcaSimplifiedHeader.java` |
| `.../zatca/simplified/entity/ZatcaSimplifiedLine.java` | `platform-core/src/main/java/com/einvoice/core/domain/zatca/ZatcaSimplifiedLine.java` |
| `.../eta/receipt/entity/EtaReceiptHeader.java` | `platform-core/src/main/java/com/einvoice/core/domain/eta/EtaReceiptHeader.java` |
| `.../eta/receipt/entity/EtaReceiptLine.java` | `platform-core/src/main/java/com/einvoice/core/domain/eta/EtaReceiptLine.java` |

New V59 / V60 sub-table entities (`ZatcaStandardTaxSubtotal`, `ZatcaSimplifiedTaxSubtotal`, `ZatcaStandardAllowance`, `ZatcaSimplifiedAllowance`, `ZatcaStandardLineAllowance`, `ZatcaSimplifiedLineAllowance`) must be created under the same `platform-core/.../domain/zatca/` package.

### Repositories (T008)

| Placeholder | Actual path |
|---|---|
| `.../zatca/standard/repository/ZatcaStandardHeaderRepository.java` | `platform-core/src/main/java/com/einvoice/core/repository/zatca/ZatcaStandardHeaderRepository.java` |
| `.../zatca/simplified/repository/ZatcaSimplifiedHeaderRepository.java` | `platform-core/src/main/java/com/einvoice/core/repository/zatca/ZatcaSimplifiedHeaderRepository.java` |
| `.../eta/receipt/repository/EtaReceiptHeaderRepository.java` | `platform-core/src/main/java/com/einvoice/core/repository/eta/EtaReceiptHeaderRepository.java` |

### Services (T009, T010, T024, T025, T036, T047, T048)

| Placeholder | Actual path |
|---|---|
| `.../zatca/standard/service/ZatcaStandardHeaderService.java` | `platform-api/src/main/java/com/einvoice/api/zatca/standard/service/ZatcaStandardService.java` (consolidated; no separate `HeaderService`) |
| `.../zatca/simplified/service/ZatcaSimplifiedSubmissionService.java` | `platform-api/src/main/java/com/einvoice/api/zatca/simplified/...` (verify in Phase 3 — Simplified service location to confirm) |
| `.../eta/receipt/service/EtaReceiptService.java` | `platform-api/src/main/java/com/einvoice/api/eta/receipt/...` (verify in Phase 3) |
| `.../zatca/standard/service/ZatcaSigningService.java` (or "submission orchestrator") | `platform-zatca/src/main/java/com/einvoice/zatca/sign/ZatcaSigningService.java` |
| `.../zatca/standard/service/ZatcaStandardLineService.java` | confirm in Phase 3 — likely consolidated into `ZatcaStandardService` |

### UBL builders / serializers (T011, T012, T026, T038, T039)

tasks.md says "Serialiser" (British) but the codebase uses "Serializer" (American) for ETA and "UblBuilder" for ZATCA. There is **no file literally named `ZatcaUblSerialiser.java`** — Phase 3 tasks should target:

| Placeholder | Actual path |
|---|---|
| `ZatcaUblSerialiser.java` (Standard) | `platform-zatca/src/main/java/com/einvoice/zatca/build/ZatcaUblBuilder.java` (likely shared by Standard + Simplified — confirm internal switch on document class before T011 starts) |
| `ZatcaSimplifiedUblSerialiser.java` | same `ZatcaUblBuilder.java` if shared; otherwise create / locate a Simplified variant in `platform-zatca/.../build/` |
| `EtaReceiptSerialiser.java` | `platform-eta/src/main/java/com/einvoice/eta/serialize/EtaReceiptSerializer.java` |
| Related: `ZatcaUblCanonicaliser.java` | `platform-zatca/src/main/java/com/einvoice/zatca/build/ZatcaUblCanonicaliser.java` (canonicalisation step — may need touch if BG-20 / BG-23 ordering changes) |

### Tests (T013–T015, T027–T029, T040–T042, T049, T050)

| Placeholder | Actual path |
|---|---|
| `backend/src/test/java/.../zatca/standard/` (golden-file) | `platform-zatca/src/test/resources/golden/standard/` (XML fixtures) + the JUnit class that reads them (locate in `platform-zatca/src/test/java/` during T013) |
| `backend/src/test/java/.../eta/receipt/` (golden-file) | `platform-eta/src/test/java/...` + `platform-eta/src/test/resources/...` (locate during T014) |
| `backend/src/test/java/.../zatca/V58RoundTripTest.java` and siblings | `platform-core/src/test/java/com/einvoice/core/migration/` is the natural home (Testcontainers + Flyway lives with the migrations); confirm during T015 |

---

*End of phase-2-baseline.md — Feature 010 Authority Spec Alignment.*
