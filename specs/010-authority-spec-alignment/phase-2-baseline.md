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

```
Tests run: 269, Failures: 4, Errors: 1, Skipped: 0
```

**Failures (4):**

| Test class | Count | Root cause |
|---|---|---|
| `EtaInvoiceLifecycleIntegrationTest` | 1 | JSON path `$.state` expected `VALID` but was `ACCEPTED` (lifecycle state assertion mismatch) |
| `EtaReceiptLifecycleIntegrationTest` | 1 | Same category of lifecycle state assertion |
| `EtaInvoiceCrossEnvIsolationTest` | 1 | ETA cross-environment isolation |
| `EtaReceiptCrossEnvIsolationTest` | 1 | ETA cross-environment isolation |

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

tasks.md uses `backend/src/main/java/...` and `backend/src/main/resources/db/migration/` as placeholders. Actual paths:

| Placeholder | Actual path |
|---|---|
| `backend/src/main/resources/db/migration/` | `platform-core/src/main/resources/db/migration/` |
| `backend/src/main/java/.../zatca/standard/` | `platform-api/src/main/java/com/einvoice/api/zatca/standard/` |
| `backend/src/main/java/.../zatca/simplified/` | `platform-api/src/main/java/com/einvoice/api/zatca/simplified/` |
| `backend/src/main/java/.../eta/receipt/` | `platform-api/src/main/java/com/einvoice/api/eta/receipt/` |
| `backend/src/test/java/.../zatca/` | `platform-api/src/test/java/com/einvoice/api/zatca/` |

Entity and repository classes live in `platform-zatca/` and `platform-eta/` respectively (discovery to be confirmed in Phase 3).

Last existing migration: **V57** (`V57__zatca_configs_base_url.sql`).

---

*End of phase-2-baseline.md — Feature 010 Authority Spec Alignment.*
