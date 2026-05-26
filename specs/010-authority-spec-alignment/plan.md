# Implementation Plan: Authority Spec Alignment

**Branch**: `010-authority-spec-alignment` | **Date**: 2026-05-26 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `specs/010-authority-spec-alignment/spec.md`

## Summary

Four sequential Flyway migrations (V58 → V59 → V60 → V61) and the paired Java entity / serialiser / service updates that align the platform's ZATCA Standard, ZATCA Simplified, and ETA Receipt document tables with the published authority specifications (ZATCA Fatoora SDK v2.0.3, ETA Receipt SDK v1.2). The output is a schema that produces UBL XML which passes the Fatoora `-validate` schematron with zero errors and an ETA Receipt JSON payload that ETA pre-production accepts at the structural validation layer.

Per the locked Clarifications session 2026-05-26:

- Migrations follow a **smart-application / dumb-database** pattern — only column adds/drops/renames, table creation with `PRIMARY KEY` only, indexes, and backfills are emitted. Every `CHECK`, `UNIQUE`, `FK`, and `NOT NULL` is catalogued in [`deferred-validation.md`](./deferred-validation.md) for a downstream constraint-layer spec.
- The four migrations ship as **four separate PRs in strict merge order**, each carrying the entity/serialiser changes that consume its columns.
- Verification is via **manual standalone scripts** (no CI gate, no scheduled job). Findings flow to Flyway log lines and a sibling `.log` file.
- ETA Invoice (V48/V49) is **out of scope** — verified aligned 2026-05-25.

## Technical Context

**Language/Version**: Java 17 (backend), TypeScript 5.x (frontend, Angular 19)
**Primary Dependencies**: Spring Boot 3.4.4 (web, data-jpa, security, validation), Spring Data JPA + Flyway 10.x, PostgreSQL JDBC driver, BouncyCastle 1.80 (CAdES), xades4j 2.4.0 (XAdES), ZXing (QR), Jackson, Lombok, Angular Material, RxJS
**Storage**: PostgreSQL 16 (single instance via Docker Compose). Four new Flyway migrations: V58 (header additions + party promotion + ETA receipt restructure), V59 (ZATCA sub-tables: `tax_subtotals`, `allowances`), V60 (line-block + line-allowance child + ETA receipt line restructure), V61 (signature artifacts + best-effort backfill)
**Testing**: JUnit 5 (backend), Karma + Jasmine (frontend), Flyway migration tests (Testcontainers-based), serialiser golden-file tests; manual standalone PowerShell scripts for Fatoora CLI / ETA pre-production validation (no CI integration per locked decision)
**Target Platform**: On-prem Linux server (Spring Boot backend), browser (Angular)
**Project Type**: Web application — backend + frontend monorepo (existing structure)
**Performance Goals**: SC-004 — each migration completes in under 30 s on staging-shaped data (≥1 row per affected table). SC-006 — VAT-number query under 100 ms p95 with ≥10,000 rows using promoted-column index. Single ZATCA submission still under 5 s end-to-end (Constitution XXV).
**Constraints**: No new user-facing screens (FR-023); UI changes limited to reading promoted columns and displaying signature artifacts. Pre-production environment — destructive operations paired with backfill in the same migration are acceptable. No automated CI gate for authority-validator verification (per locked decision).
**Scale/Scope**: 4 migrations, ~16 column additions and ~5 column drops on header tables, 6 new tables (4 ZATCA sub-tables in V59 + 2 line-allowance tables in V60), ~30 entity/repository/service/serialiser file edits across 4 PRs, 2 standalone manual verification scripts, 1 `deferred-validation.md` catalogue with ~40 deferred rules.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-evaluated after Phase 1 design — verdict unchanged.*

| Principle | Verdict | Notes |
|---|---|---|
| I. Compliance-First | PASS | Feature exists *to* enforce ZATCA Fatoora schematron and ETA Receipt v1.2 compliance at the schema level. FR-019/FR-020 manual scripts prove validator-clean output. |
| II. Multi-Tenant Isolation | PASS | Isolation key `(company_id, authority_environment_id)` unchanged; new columns/tables inherit existing isolation. |
| III. Authority Environment Architecture | PASS | No change to `authority_environments` registry. |
| IV. Branch + Environment Segregation | PASS | New `zatca_config_id` linkage respects existing per-environment `zatca_configs` (V46). |
| V. Stateless Service | PASS | Migrations are schema-only; no runtime state added. |
| VI. Server-Side Cryptography | PASS | V61 adds first-class storage for the KSA-15 cryptographic stamp; private keys remain in `zatca_configs` server-side. |
| VII. Admin / Operational Mode Separation | PASS | No change to mode model. |
| VIII. Transaction Module Architecture | PASS | Per-module table groups preserved (Standard, Simplified, ETA Receipt). |
| IX. Immutable Audit | PASS | `audit_logs` explicitly out of scope (no change). |
| X. Deterministic Document Lifecycle | PASS | Lifecycle states unchanged. |
| XI. Physical Document Table Strategy | PASS | Per-module table groups extended with sub-tables inside the same physical group; ETA Receipt vs. ETA Invoice separation preserved. |
| XII. ZATCA Hash Chain Integrity | PASS | `zatca_chain_state` explicitly out of scope. |
| **XIII. Validation Layering** | **PASS — load-bearing** | The smart-app / dumb-DB decision (Clarifications 2026-05-26) is *consistent* with XIII, which mandates "backend is the source of truth for all final validation." The DB layer is intentionally minimal; all schematron rules (`CHECK`, `UNIQUE`, `FK`, `NOT NULL`) are catalogued in `deferred-validation.md` for service-layer enforcement. Constitution XIII does not require DB-level enforcement. |
| XIV. Angular Engineering | PASS | UI changes limited to reading promoted columns + displaying signature artifacts (FR-023). |
| XV. Spring Boot Engineering | PASS | Flyway migrations versioned (V58–V61, all above the V37 floor); `TenantContext` unchanged; repository queries continue to filter by `(company_id, authority_environment_id)`. |
| XVI. Authority Adapter | PASS | `AuthorityEngine` contract unchanged. |
| XVII. RBAC and Access Control | PASS | No new permissions or roles. |
| XVIII. Plain-Text Certificate Storage Policy | PASS | `zatca_configs` (V46) is reused unchanged. |
| XIX. Secure On-Prem Deployment | PASS | No deployment topology change. |
| XX. Reliability and Recovery | PASS | V61 best-effort backfill never aborts the migration; unresolved rows are logged. FR-021 backfill diff provides audit trail. |
| XXI. Document Preservation | PASS — with caveat | V61 adds first-class `signed_xml_artifact_id` linkage; the column is a plain UUID (no FK) per the locked smart-app / dumb-DB pattern. Service layer enforces referential integrity per `deferred-validation.md` §V61.A. |
| XXII. Signed License Enforcement | N/A | Phase 2 only. |
| XXIII. Change Control | PASS | Feature is traceable to ZATCA Fatoora SDK v2.0.3 and ETA Receipt SDK v1.2 via the design-input docs referenced in spec.md §Design inputs. |
| XXIV. Testing | PASS — with caveat | Golden-file tests for serialisers (Constitution XXIV.1) and validation tests for new domain rules. **No automated authority-validator CI** per locked clarification — replaced by manual standalone scripts (FR-019/FR-020). Constitution XXIV.4 is interpreted as "integration tests against mocked authority responses"; the new manual scripts complement, not replace, those existing tests. |
| XXV. Performance | PASS | SC-006 + new index on promoted `seller_vat_number` back the <100 ms target. |
| XXVI. Source of Truth | PASS | This plan honours the locked spec.md. |

**No unjustified violations.** Two principles are noted "PASS — load-bearing" (XIII) or "PASS — with caveat" (XXI, XXIV); the caveats are explicitly grounded in locked Clarifications, not departures from the constitution.

## Project Structure

### Documentation (this feature)

```text
specs/010-authority-spec-alignment/
├── spec.md                       # Locked feature spec with 24 clarifications
├── deferred-validation.md        # Catalogue of CHECK/UNIQUE/FK/NOT NULL rules deferred to a future constraint-layer spec
├── plan.md                       # This file
├── research.md                   # Phase 0 — consolidates the two design-input docs in /Docs
├── data-model.md                 # Phase 1 — entity-level diffs per migration
├── quickstart.md                 # Phase 1 — per-PR developer flow + manual script invocation
├── contracts/
│   └── migrations.md             # The four migration file contracts (V58–V61) + manual-script contracts
└── tasks.md                      # Phase 2 — generated by /speckit-tasks (NOT created here)
```

### Source Code (repository root)

```text
backend/
├── src/main/resources/db/migration/
│   ├── V58__zatca_eta_header_additions.sql          # PR 1
│   ├── V59__zatca_subtotals_and_allowances.sql      # PR 2
│   ├── V60__line_block_and_allowances.sql           # PR 3
│   └── V61__signature_artifacts.sql                 # PR 4
├── src/main/java/.../eta/receipt/
│   ├── entity/EtaReceiptHeader.java                 # rename + JSONB additions (PR 1, PR 3)
│   ├── entity/EtaReceiptLine.java                   # unit_price + discount arrays (PR 3)
│   ├── service/EtaReceiptSerialiser.java            # v1.2 JSON shape (PR 1, PR 3)
│   └── dto/*                                        # DTO updates aligned to v1.2
├── src/main/java/.../zatca/standard/
│   ├── entity/ZatcaStandardHeader.java              # promoted party columns + signature fields
│   ├── entity/ZatcaStandardLine.java                # BT-146..150 + KSA-12
│   ├── entity/ZatcaStandardTaxSubtotal.java         # NEW (PR 2)
│   ├── entity/ZatcaStandardAllowance.java           # NEW (PR 2)
│   ├── entity/ZatcaStandardLineAllowance.java       # NEW (PR 3)
│   ├── service/ZatcaUblSerialiser.java              # consume new entities
│   └── service/ZatcaSigningService.java             # persist signature artifacts (PR 4)
├── src/main/java/.../zatca/simplified/
│   ├── entity/ZatcaSimplifiedHeader.java            # parallel to Standard
│   ├── entity/ZatcaSimplifiedLine.java
│   ├── entity/ZatcaSimplifiedTaxSubtotal.java       # NEW (PR 2)
│   ├── entity/ZatcaSimplifiedAllowance.java         # NEW (PR 2)
│   └── entity/ZatcaSimplifiedLineAllowance.java     # NEW (PR 3)
└── src/test/java/.../zatca/                         # serialiser golden-file tests updated per PR

frontend/
└── src/app/                                         # minor: read new promoted columns, display signed-xml link

scripts/verify/
├── fatoora-validate.ps1                             # Manual script (FR-019/FR-020) — invokes Fatoora CLI
├── eta-receipt-submit.ps1                           # Manual script — submits one ETA Receipt to pre-prod
└── samples/                                         # Canonical test-case fixtures
```

**Structure Decision**: Existing two-app layout (`backend/`, `frontend/`) is retained — this feature is a refactor with no new top-level modules. New manual verification scripts live in a sibling `scripts/verify/` directory (created in PR 1) to keep them out of the CI build path per the locked manual-only verification decision.

## Complexity Tracking

> No Constitution Check violations require justification. Two principles (XIII and XXI/XXIV) are noted as load-bearing or carrying caveats, but each caveat is explicitly grounded in locked Clarifications — not departures from the constitution.

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| *(none)* | *(n/a)* | *(n/a)* |
