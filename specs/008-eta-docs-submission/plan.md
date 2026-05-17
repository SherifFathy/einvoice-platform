# Implementation Plan: Wave 7 — ETA Document Tables and Submission Engine

**Branch**: `008-eta-docs-submission` | **Date**: 2026-05-11 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/008-eta-docs-submission/spec.md`

## Summary

Wave 7 builds the first **operational document layer** of the platform on top of the Waves 5–6 foundation. It introduces the ETA invoice and receipt table groups (six invoice document types — `i`, `c`, `d`, `ei`, `ec`, `ed`; the full v1.2 receipt subtype set) with `(company_id, authority_environment_id)` isolation built in from day one (Constitution II.5, XI.1), and recreates the three cross-authority operational tables Wave 5 dropped — `submission_attempts`, `invoice_artifacts`, `audit_logs` — as the shared substrate Wave 8 will reuse for ZATCA (Constitution XI.6). Six Flyway migrations (**V48–V53**) deliver the schema; `platform-eta` becomes a populated module hosting `EtaInvoiceService`, `EtaReceiptService`, `EtaInvoiceSerializer`, `EtaReceiptSerializer`, and the existing token/signing/status concepts re-implemented against the new tables; `platform-api` exposes ~20 REST endpoints (CRUD + submit/cancel/retry/check-status per invoice and per receipt, plus per-document submissions and artifacts read endpoints) gated by Wave 5 `@RequiresPermission` against the INVOICE / RECEIPT transaction-module permissions; two Angular feature folders (`invoices/`, `receipts/`) ship list + detail + form screens, the bulk **Check status** action, and the optimistic-concurrency conflict-resolution view.

Five clarifications recorded in `spec.md` shape this wave's implementation:

- **(Q1) Rejection is terminal.** A rejected document stays read-only forever; users correct by invoking a "Create new draft from this document" action that produces a fresh draft (new document number) pre-populated from the rejected snapshot. The state machine therefore distinguishes `REJECTED` (terminal, FR-007) from `DRAFT` and forbids edits/deletes on rejected rows.
- **(Q2) No background polling.** Documents that ETA returns as "in review" sit in an explicit `IN_REVIEW` state. Users invoke a per-document **Check status** action, or — on the list screen — a bulk **Check status** over rows they've selected (per-row checkbox + select-all-matching-current-search; FR-012, FR-012a).
- **(Q3) No platform-side cancellation-window check.** Cancel is always available on accepted invoices; the platform forwards every cancellation request to ETA and treats ETA's response (including out-of-window rejection) as authoritative (FR-013).
- **(Q4) Optimistic concurrency on drafts.** Every save carries the document's last-seen version; stale saves are rejected and the user is shown a side-by-side conflict-resolution view (FR-025). No pessimistic locks. The `eta_invoice_headers` and `eta_receipt_headers` tables therefore carry a `version` column managed via JPA `@Version`.
- **(Q5) English-only UI chrome.** All field labels, validation messages, status text, table headings, action menus, and dialogs are English. Arabic UI / RTL is out of scope for this wave. Data-field content (party names, descriptions, references) accepts and preserves Arabic text without alteration (FR-026).

This wave is **the structural turn** of the platform: every preceding wave (3–6) prepared the ground (companies, branches, users, RBAC, authority environments, ETA master data and config); Wave 7 actually creates the first business documents users submit to a tax authority. The constitutional posture is **enforce isolation everywhere a document is read or written** (Constitution II.5, XV.7), **make the lifecycle a true state machine** (Constitution X.1–4), **never overwrite a stored artifact or audit row** (Constitution IX, XXI), and **keep the authority engine behind the shared `AuthorityEngine` adapter** (Constitution XVI) so Wave 8 can plug ZATCA in without touching `platform-api` orchestration.

## Technical Context

**Language/Version**: Java 17 (backend), TypeScript 5.x (frontend, Angular 19)

**Primary Dependencies**:
- Backend: Spring Boot 3.4.4 (web, data-jpa, security, validation, async), Spring Data JPA + Flyway (PostgreSQL 16), Jackson (ETA JSON serialization), Lombok, BouncyCastle 1.80 (CAdES-BES signing — already on classpath in `platform-eta`'s `pom.xml`), the Wave 5 `@RequiresPermission` AOP, `TenantContext`, `PermissionService`, `SessionContextAssembler`, and the Wave 6 `EtaConfigRepository` (read-only access to the company's ETA credentials and certificate). No new third-party libraries introduced in this wave.
- Frontend: Angular 19, Angular Material, RxJS, the Wave 5 `SessionContextService`, `PermissionService`, `appHasPermission` directive. No new third-party libraries.

**Storage**: PostgreSQL 16 (single instance via Docker Compose). Six new Flyway migrations: **V48** (`eta_invoice_headers`), **V49** (`eta_invoice_lines` + `eta_invoice_line_taxes`), **V50** (`eta_receipt_headers`), **V51** (`eta_receipt_lines` + `eta_receipt_line_taxes`), **V52** (`submission_attempts` + `invoice_artifacts` + `audit_logs` — recreating the three Wave-5 drops in their new shape), **V53** (compound indexes finalisation across all eight new tables).

> Note on numbering: the Wave-7 section of `Docs/implementation-plan.md` references V47–V52. Wave 6 actually consumed V47 (operational indexes) and V47a (master-data viewer-permission seed); Wave 7 therefore starts at V48 and runs through V53. The schema contents match the implementation-plan section verbatim aside from the version-number shift.

**Testing**:
- Backend: JUnit 5 + Spring Boot Test + Testcontainers (`postgres:16-alpine`). Contract tests for every new endpoint group. Integration tests for: cross-environment isolation (Constitution XXIV.6 / SC-003), the full lifecycle state machine (Constitution X / SC-008), and optimistic-concurrency conflict (FR-025). **Golden-file tests** for the ETA JSON serializer covering each of the six invoice document types and at least one receipt subtype (Constitution XXIV.1, mirrors Wave-7 Exit Criterion "Golden-file tests pass for ETA invoice JSON serialization").
- Frontend: Karma + Jasmine for components and services; component tests for the two new feature folders including the bulk-refresh selection model and the conflict-resolution view.

**Target Platform**: On-prem Linux server (Docker Compose), browser (Chromium-based for UAT). Constitution Principle XIX.

**Project Type**: Multi-module Maven backend + Angular frontend. Existing layout from Wave 6: `platform-api`, `platform-core`, `platform-security`, `platform-eta` (currently empty — populated in this wave), `platform-zatca`, `platform-jobs`, `platform-pdf`, `frontend/`.

**Performance Goals** (mirrors `spec.md` SC-001, SC-007 and Constitution XXV):
- Single document submission p95 < 5s end-to-end (form → signed → ETA call → response stored) for a typical 5-line invoice, in nominal ETA conditions. Constitution XXV.1.
- List screens for users with up to 10 assigned companies: first page p95 < 2s; filter narrowing p95 < 500ms. SC-007.
- Bulk **Check status** of up to 200 selected documents: p95 < 30s total (calls fan out concurrently; per-document outcomes reported back as they arrive).
- All operational document queries hit a compound index on `(company_id, authority_environment_id, …)` per Constitution XXV.2.

**Constraints**:
- No outbound network call to ETA from any endpoint except `POST /submit`, `POST /cancel`, `POST /retry`, and `POST /check-status` (single + bulk).
- **Rejected is terminal** (Q1) — the state machine forbids any transition out of `REJECTED`. The clone-to-new-draft action creates a *new* draft row; the rejected row stays read-only forever.
- **No background polling** (Q2) — there is no `@Scheduled` task in `platform-jobs` reconciling `IN_REVIEW` rows. ShedLock-backed schedulers are not used in this wave (they belong to Wave 9 reconciliation tooling).
- **No platform cancellation-window check** (Q3) — `POST /cancel` always forwards to ETA; the platform never short-circuits.
- **Optimistic concurrency only** (Q4) — `@Version` column on `eta_invoice_headers` and `eta_receipt_headers`; no row locks on drafts. The submission flow itself acquires no advisory lock either (no chain counter on ETA — that's ZATCA's concern in Wave 8).
- **English-only UI chrome** (Q5) — no i18n / RTL infrastructure introduced in this wave.
- **Append-only operational tables** (Constitution IX, XXI; FR-014, FR-015, FR-016, FR-017) — `invoice_artifacts` and `audit_logs` reject UPDATE/DELETE via repository-level enforcement and a Postgres `BEFORE UPDATE OR DELETE` trigger as a defence-in-depth measure.
- **Operational endpoints reject Admin Mode** with `COMPANY_CONTEXT_REQUIRED` (Constitution VII.4) — already enforced by the Wave 5 `TenantFilter`; reaffirmed here because Wave 7 is the first wave where Admin Mode users could plausibly try to load a document by ID.
- Plain-text certificate storage from Wave 6 is read by the signing path; certificates and private keys are loaded server-side only (Constitution VI.1–2, XVIII).

**Scale/Scope**:
- 8 new tables, 6 Flyway migrations (V48–V53).
- ~24 new REST endpoints: list, create, read, update, delete (draft only), submit, cancel, retry, check-status (single), clone-to-new-draft, list submissions, get artifact (× 2 transaction types: invoices and receipts = ~22 endpoints) plus 2 bulk **Check status** endpoints (one per transaction type).
- 2 new Angular feature folders (`invoices/`, `receipts/`) with ~14 new components (list, form, detail, conflict-resolution dialog, status-check progress dialog, line-items editor, tax-breakdown editor, submission-history timeline, artifact-download panel — shared between invoices and receipts where shape is identical).
- 6 new domain entities + repositories (`EtaInvoiceHeader`, `EtaInvoiceLine`, `EtaInvoiceLineTax`, `EtaReceiptHeader`, `EtaReceiptLine`, `EtaReceiptLineTax`) + 3 new shared entities (`SubmissionAttempt`, `InvoiceArtifact`, `AuditLog`).
- 4 new application services (`EtaInvoiceService`, `EtaReceiptService`, `EtaSubmissionOrchestrator`, `AuditService`) + 4 new authority-engine concerns in `platform-eta` (`EtaInvoiceSerializer`, `EtaReceiptSerializer`, `EtaSigningService`, `EtaTokenManager`).
- Expected initial data per company per environment: 0–100k invoices/year, 0–500k receipts/year, multiple submission attempts per document (bounded by retries), 1–4 artifacts per submitted document.
- 26 functional requirements (FR-001 through FR-026), 5 user stories (2× P1, 2× P2, 1× P3), 8 success criteria.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

### Pre-Phase-0 Evaluation

| #     | Principle                                  | Status                | Notes |
|-------|--------------------------------------------|-----------------------|-------|
| I     | Compliance-First                           | ✅ Pass               | ETA invoices serialized as ETA JSON; signed with CAdES-BES via BouncyCastle (Constitution I.3). Receipts follow ETA v1.2 receipt API including document_type, document_type_version, POS serial, receipt UUID (Constitution I.4). Six invoice document types and the v1.2 receipt subtype set covered by spec FR-001/002. Golden-file tests required (Constitution XXIV.1). |
| II    | Multi-Tenant Isolation                     | ✅ Pass               | All 8 new tables carry `(company_id, authority_environment_id)` either directly (headers, submission_attempts, invoice_artifacts) or transitively via FK to the header (lines, line_taxes). FR-003, FR-018. Repository support helper from Wave 6 (`OperationalRepositorySupport`) extended to cover the new entities. |
| III   | Authority Environment Architecture         | ✅ Pass               | Every header table FK-references `authority_environments(id)`; JWT-bound `authorityEnvironmentId` is the single source of truth at read and write time (FR-003, FR-018). Cross-environment leakage explicitly tested (SC-003 ↔ Constitution XXIV.6). |
| IV    | Branch + Environment Segregation           | ✅ Pass               | Each header table carries `branch_id` per Wave-7 SDK spec; submission_attempts and artifacts are segregated by environment via the header. ZATCA's separate-configs-per-environment rule is upheld today by Wave 6 and inherited by Wave 8. |
| V     | Stateless Service                          | ✅ Pass               | All new services and controllers are request-scoped; `EtaTokenManager` uses a bounded per-tenant in-memory cache keyed by `(companyId, authorityEnvironmentId)` with TTL ≤ token lifetime (Constitution V.3 explicitly permits bounded secure caches). |
| VI    | Server-Side Cryptography                   | ✅ Pass               | All signing happens in `platform-eta`'s `EtaSigningService`; private keys are read from `eta_configs` (plain-text per Wave-6 / Constitution XVIII) and never leave the JVM. The frontend never receives certificates, keys, or token values. |
| VII   | Admin Mode / Operational Mode Separation   | ✅ Pass               | All Wave 7 endpoints are operational and require non-null `companyId` from JWT. Admin Mode is rejected with `COMPANY_CONTEXT_REQUIRED` (FR-018 ↔ Constitution VII.4). Super User flag bypasses RBAC but still requires a company context for these endpoints. |
| VIII  | Transaction Module Architecture            | ✅ Pass               | Invoices and Receipts are the two ETA transaction modules (Constitution VIII.1). Sidebar entries for both are added, each gated by the user's INVOICE / RECEIPT permissions in the active `authority_environment_id`. List screens show all assigned companies with a Company column (Constitution VIII.5, XIV.5, FR-022). |
| IX    | Immutable Audit                            | ✅ Pass               | `audit_logs` table recreated in V52 carrying `(company_id, authority_environment_id, user_id, action, entity_type, entity_id, payload_before, payload_after, ip_address, created_at)`. Repository write-only (no UPDATE, no DELETE); database trigger as defence-in-depth (Constitution IX.3). Every Wave-7 state-changing endpoint emits an audit row via `AuditService` (FR-016). |
| X     | Deterministic Document Lifecycle           | ✅ Pass               | State machine codified in `EtaInvoiceLifecycle` and `EtaReceiptLifecycle` value objects; backend rejects invalid transitions even if the UI requests them (Constitution X.2). Every submission attempt is recorded as a separate row independent of canonical state (Constitution X.4 ↔ FR-009). Q1's "rejected is terminal" is encoded as: REJECTED → ∅ (no allowed transitions on the rejected row itself); the clone-to-new-draft API (FR-007a) creates a *new* row in DRAFT and leaves the rejected source untouched. |
| XI    | Physical Document Table Strategy           | ✅ Pass               | Per XI.1 explicit list: `eta_invoice_headers + eta_invoice_lines + eta_invoice_line_taxes` and `eta_receipt_headers + eta_receipt_lines + eta_receipt_line_taxes` — Wave 7 creates exactly these. Per XI.3 ETA lines have a separate taxes table to support multiple tax types per line. Per XI.4 environment is stored as `authority_environment_id` inside the header — no per-environment table groups. Per XI.6 `submission_attempts` and `invoice_artifacts` are shared with a `transaction_type` discriminator and `document_id` referencing the relevant header (FK enforced at application layer, not DB). |
| XII   | ZATCA Hash Chain Integrity                 | N/A                   | No ZATCA in Wave 7. Wave 8. |
| XIII  | Validation Layering                        | ✅ Pass               | Angular reactive form validators for guidance; Bean Validation + service-level domain rules on the backend (totals consistency, original-document compatibility, document-number uniqueness); ETA serializer enforces ETA-payload validation as the authority-compliance layer (Constitution XIII.1). Five-decimal rounding centralised in a shared `MoneyMath` helper (Constitution XIII.4–5; FR-005). |
| XIV   | Angular Engineering                        | ✅ Pass               | Reactive Forms only for both invoice and receipt forms. Per-row and per-action buttons gated by permissions via `appHasPermission` (FR-021). List screens show records across all assigned companies with a Company column and Company filter (Constitution XIV.5; FR-022). No secret handling in the frontend. Header chips from Wave 5 unchanged. |
| XV    | Spring Boot Engineering                    | ✅ Pass               | Layered (controller → application service → domain service → repository → authority engine adapter). Flyway V48–V53 are mandatory and additive (Constitution XV.5). `TenantContext` reused unchanged from Wave 5. Every operational repository query filters by `(company_id, authority_environment_id)` (Constitution XV.7) — `OperationalRepositorySupport` from Wave 6 is extended with `EtaInvoiceSpecifications` and `EtaReceiptSpecifications`. `/api/session/context` returns the INVOICE and RECEIPT permission sets per company per environment (Constitution XV.8). |
| XVI   | Authority Adapter                          | ✅ Pass               | `AuthorityEngine` interface introduced in `platform-eta`/`platform-zatca`-shared code path. `EtaAuthorityEngine` implements `submit`, `cancel`, `checkStatus`, `serialize`, `sign`. `EtaSubmissionOrchestrator` in `platform-api` composes the engine with `SubmissionAttemptRepository` + `InvoiceArtifactRepository` + `AuditService` — no authority-specific branching outside the engine (Constitution XVI.2). Wave 8 will add `ZatcaAuthorityEngine` without changing the orchestrator. |
| XVII  | RBAC and Access Control                    | ✅ Pass               | INVOICE and RECEIPT modules use the 8 action permissions from Constitution XVII.6: VIEW, CREATE, EDIT, DELETE, CANCEL, TRANSFER, REFRESH, SUBMIT. REFRESH maps to **Check status** (single + bulk). Default role permission sets from Constitution XVII.8 apply unchanged: COMPANY_ADMIN gets all 8, ACCOUNTANT gets VIEW/CREATE/SUBMIT/REFRESH, VIEWER gets VIEW only. The clone-to-new-draft action requires CREATE permission. The conflict-resolution overwrite path requires EDIT permission. |
| XVIII | Plain-Text Certificate Storage Policy      | ✅ Pass               | No new secret storage introduced in this wave. The signing path reads from `eta_configs` (Wave 6 plain-text columns) and never persists secret material beyond what Wave 6 already stores. Phase-2 AES-256-GCM upgrade still lands at the storage layer with no schema migration. |
| XIX   | Secure On-Prem Deployment                  | ✅ Pass               | No deployment-topology change. HTTPS still mandatory; submission-bound traffic to ETA goes through the existing outbound proxy configuration. |
| XX    | Reliability and Recovery                   | ✅ Pass               | Document state is persisted *before* the outbound ETA call begins (Constitution XX.1) — the canonical row is in DB with status `SUBMITTING` before the network call; the submission_attempt row is inserted with `attempt_number` in the same transaction. Retries are bounded (FR-011, retry produces a new attempt row, never silently overwrites). Ambiguous results land in `SUBMISSION_AMBIGUOUS` (FR-011 ↔ Constitution XX.5) — never silently lost. |
| XXI   | Document Preservation                      | ✅ Pass               | `invoice_artifacts` recreated in V52: append-only (Constitution XXI.4), distinguishes source-domain data (header/lines tables) from generated authority payloads (artifacts table, with `artifact_type` discriminator: `SIGNED_JSON`, `SIGNED_XML`, `QR_CODE`, `CLEARED_XML`, `ETA_RESPONSE`, `ZATCA_RESPONSE`). Regeneration after submission produces a *new* row rather than overwriting. |
| XXII  | Signed License Enforcement (Phase 2)       | N/A                   | No company-creation paths in this wave. |
| XXIII | Change Control                             | ✅ Pass               | spec.md → plan.md → tasks.md chain maintained. Five clarifications recorded with traceable IDs Q1–Q5. Constitution principles cited inline against each functional requirement. |
| XXIV  | Testing                                    | ✅ Pass               | Golden-file tests for all six ETA invoice document types and at least one receipt subtype (Constitution XXIV.1). Unit tests on lifecycle transitions including the REJECTED-is-terminal rule (Constitution XXIV.3). Integration tests on tenant + authority-environment isolation across the new endpoints (Constitution XXIV.5–6 ↔ SC-003). Contract tests on the new REST surface (XXIV.4 spirit). |
| XXV   | Performance                                | ✅ Pass               | Submission target < 5s nominal (Constitution XXV.1) — sized by SC-001 (4-minute end-to-end including data entry). Compound `(company_id, authority_environment_id, …)` indexes on every header table (Constitution XXV.2 ↔ V53). Bulk **Check status** runs asynchronously with progress reporting (Constitution XXV.3 ↔ FR-012a) — implemented as a server-side parallel fan-out within a single request, not as background jobs (the latter belongs to Wave 9). |
| XXVI  | Source of Truth                            | ✅ Pass               | spec.md is functional truth; this plan is implementation sequencing; tasks.md will be execution detail. |

**Gate Result**: ✅ PASS — three deliberate scope-narrowing decisions are flagged below in *Complexity Tracking* (Q1, Q2, Q3 clarifications), each tighter than the constitution's default would suggest but all explicitly justified.

### Post-Phase-1 Re-Evaluation

Re-evaluation after `data-model.md`, `contracts/`, and `quickstart.md` were generated:

- All 8 new entities in `data-model.md` carry `(companyId, authorityEnvironmentId)` (Constitution II.5). The shared operational tables (`submission_attempts`, `invoice_artifacts`, `audit_logs`) reach the isolation key directly on the row; line and line-tax tables reach it via the FK to the header.
- The `state` field on `EtaInvoiceHeader` and `EtaReceiptHeader` is modeled as a typed enum with an explicit transition matrix; the matrix is the canonical source for the backend state-machine rejection logic and for the Angular permission-gated action buttons.
- All endpoint contracts in `contracts/eta-invoices-api.openapi.yaml` and `contracts/eta-receipts-api.openapi.yaml` pin the tenant scope to the JWT — no `companyId` query parameter is accepted on any list endpoint that the client could spoof. `authorityEnvironmentId` is never on the wire.
- `If-Match` and `ETag` semantics are codified in the contracts for the draft-edit endpoints (FR-025 optimistic concurrency). The conflict response is a structured 409 with both versions in the body.
- The bulk **Check status** contract takes `documentIds[]` and returns a per-id status array with completion timestamps and outcomes (FR-012a). A single document ID over the same endpoint is allowed for symmetry with the per-row "Check status" button.
- `error-codes.md` extends the Wave-5/6 catalogue with `DUPLICATE_INVOICE_NUMBER`, `DUPLICATE_RECEIPT_NUMBER`, `MISSING_ORIGINAL_DOCUMENT`, `TOTALS_INCONSISTENT`, `NO_CERTIFICATE_CONFIGURED`, `DOCUMENT_NOT_DRAFT`, `INVALID_LIFECYCLE_TRANSITION`, `OPTIMISTIC_LOCK_CONFLICT`, `SUBMISSION_AMBIGUOUS`, `ETA_VALIDATION_ERROR`.
- Quickstart Phase D exercises the full happy path (create → submit → ETA accept → artifact retrieval) on Pre-Production; Phase E exercises the cross-environment isolation test (Constitution XXIV.6 ↔ SC-003); Phase F exercises optimistic-concurrency conflict (FR-025); Phase G exercises rejected-is-terminal + clone-to-new-draft (Q1 ↔ FR-007).
- No new principle violations detected.

**Gate Result**: ✅ PASS.

## Project Structure

### Documentation (this feature)

```text
specs/008-eta-docs-submission/
├── plan.md                                  # This file (/speckit.plan output)
├── research.md                              # Phase 0 output
├── data-model.md                            # Phase 1 output (entities + lifecycle)
├── quickstart.md                            # Phase 1 output (smoke-test runbook)
├── contracts/
│   ├── eta-invoices-api.openapi.yaml        # /api/companies/{id}/eta/invoices/*
│   ├── eta-receipts-api.openapi.yaml        # /api/companies/{id}/eta/receipts/*
│   └── error-codes.md                       # Wave 7 additions to the catalogue
├── checklists/
│   └── requirements.md                      # Already created during /speckit.specify
└── tasks.md                                 # Will be created by /speckit.tasks
```

### Source Code (repository root)

Existing multi-module Maven backend layout from Wave 6. Wave 7 adds files under `platform-core` (entities + Flyway), `platform-api` (REST controllers + application services + the submission orchestrator + audit service), `platform-eta` (newly populated — authority engine, serializers, signing, token manager, status service), and two new Angular feature folders. No new modules introduced; no files removed.

```text
platform-core/
└── src/main/
    ├── java/com/einvoice/core/
    │   ├── domain/
    │   │   ├── eta/
    │   │   │   ├── EtaInvoiceHeader.java               # NEW (@Version field — FR-025)
    │   │   │   ├── EtaInvoiceLine.java                 # NEW
    │   │   │   ├── EtaInvoiceLineTax.java              # NEW
    │   │   │   ├── EtaReceiptHeader.java               # NEW (@Version field — FR-025)
    │   │   │   ├── EtaReceiptLine.java                 # NEW
    │   │   │   ├── EtaReceiptLineTax.java              # NEW
    │   │   │   ├── lifecycle/
    │   │   │   │   ├── EtaInvoiceState.java            # NEW enum (DRAFT/SUBMITTING/IN_REVIEW/VALID/REJECTED/SUBMISSION_AMBIGUOUS/CANCELLED)
    │   │   │   │   ├── EtaReceiptState.java            # NEW enum (same shape)
    │   │   │   │   └── LifecycleTransitions.java       # NEW — single source for allowed transitions
    │   │   │   └── document/
    │   │   │       ├── EtaInvoiceDocumentType.java     # NEW enum (i, c, d, ei, ec, ed)
    │   │   │       └── EtaReceiptDocumentType.java     # NEW enum (full v1.2 set)
    │   │   └── shared/
    │   │       ├── SubmissionAttempt.java              # NEW (no UPDATE/DELETE)
    │   │       ├── SubmissionResult.java               # NEW enum (SUCCESS/REJECTED/ERROR/TIMEOUT/AMBIGUOUS)
    │   │       ├── InvoiceArtifact.java                # NEW (no UPDATE/DELETE)
    │   │       ├── ArtifactType.java                   # NEW enum
    │   │       ├── AuditLog.java                       # NEW (no UPDATE/DELETE)
    │   │       └── TransactionType.java                # NEW enum (INVOICE/RECEIPT/STANDARD/SIMPLIFIED)
    │   ├── repository/
    │   │   ├── eta/{EtaInvoiceHeaderRepository, EtaInvoiceLineRepository, EtaInvoiceLineTaxRepository, EtaReceiptHeaderRepository, EtaReceiptLineRepository, EtaReceiptLineTaxRepository}.java
    │   │   ├── shared/{SubmissionAttemptRepository, InvoiceArtifactRepository, AuditLogRepository}.java
    │   │   └── support/
    │   │       ├── EtaInvoiceSpecifications.java       # NEW (extends OperationalRepositorySupport)
    │   │       └── EtaReceiptSpecifications.java       # NEW (extends OperationalRepositorySupport)
    │   ├── lifecycle/
    │   │   ├── EtaInvoiceLifecycle.java                # NEW — pure-function state machine
    │   │   └── EtaReceiptLifecycle.java                # NEW
    │   ├── money/
    │   │   └── EtaMoneyMath.java                       # NEW — 5-decimal rounding helpers (Constitution XIII.5)
    │   └── error/
    │       ├── DuplicateInvoiceNumberException.java    # NEW (FR-006)
    │       ├── DuplicateReceiptNumberException.java    # NEW (FR-006)
    │       ├── MissingOriginalDocumentException.java   # NEW (FR-023)
    │       ├── TotalsInconsistentException.java        # NEW (FR-024)
    │       ├── NoCertificateConfiguredException.java   # NEW (FR-008 ↔ Wave-6 eta_configs)
    │       ├── DocumentNotDraftException.java          # NEW (FR-007)
    │       ├── InvalidLifecycleTransitionException.java # NEW (FR-007, Q1)
    │       └── OptimisticLockConflictException.java    # NEW (FR-025)
    └── resources/db/migration/
        ├── V48__eta_invoice_headers.sql                # NEW
        ├── V49__eta_invoice_lines_and_taxes.sql        # NEW
        ├── V50__eta_receipt_headers.sql                # NEW
        ├── V51__eta_receipt_lines_and_taxes.sql        # NEW
        ├── V52__shared_operational_tables.sql          # NEW — submission_attempts + invoice_artifacts + audit_logs + immutability trigger
        └── V53__wave7_compound_indexes.sql             # NEW

platform-eta/
└── src/main/java/com/einvoice/eta/
    ├── engine/
    │   └── EtaAuthorityEngine.java                     # NEW — implements AuthorityEngine contract
    ├── serialize/
    │   ├── EtaInvoiceSerializer.java                   # NEW — maps EtaInvoiceHeader → ETA JSON (6 doc types)
    │   └── EtaReceiptSerializer.java                   # NEW — maps EtaReceiptHeader → ETA receipt JSON (v1.2 subtypes)
    ├── sign/
    │   └── EtaSigningService.java                      # NEW — CAdES-BES via BouncyCastle, reads from eta_configs
    ├── token/
    │   └── EtaTokenManager.java                        # NEW — OAuth token cache, per (companyId, authorityEnvironmentId)
    ├── status/
    │   └── EtaStatusService.java                       # NEW — single + bulk "Check status" against ETA
    └── client/
        └── EtaHttpClient.java                          # NEW — thin HTTP client; per-environment base URL from authority_environments

platform-api/
└── src/main/java/com/einvoice/api/
    ├── eta/
    │   ├── invoice/
    │   │   ├── EtaInvoiceController.java               # NEW
    │   │   └── service/
    │   │       ├── EtaInvoiceService.java              # NEW — CRUD + clone-to-new-draft
    │   │       └── EtaInvoiceFormMapper.java           # NEW — DTO ↔ entity
    │   ├── receipt/
    │   │   ├── EtaReceiptController.java               # NEW
    │   │   └── service/
    │   │       ├── EtaReceiptService.java              # NEW
    │   │       └── EtaReceiptFormMapper.java           # NEW
    │   └── submission/
    │       ├── EtaSubmissionController.java            # NEW — submit/cancel/retry/check-status (single + bulk)
    │       └── service/
    │           └── EtaSubmissionOrchestrator.java      # NEW — composes engine + attempts + artifacts + audit
    ├── audit/
    │   └── service/
    │       └── AuditService.java                       # NEW — single write-only emitter
    ├── error/
    │   └── GlobalExceptionHandler.java                 # UPDATE: map ten new exceptions to error codes
    └── (existing admin/, auth/, session/, config/, master-data folders untouched)

frontend/src/app/
├── invoices/                                           # NEW feature folder (Wave-5 stub replaced)
│   ├── eta/
│   │   ├── eta-invoice-list.component.{ts,html,scss}
│   │   ├── eta-invoice-form.component.{ts,html,scss}
│   │   ├── eta-invoice-detail.component.{ts,html,scss}
│   │   └── services/eta-invoice.service.ts
│   ├── shared/
│   │   ├── line-items-editor.component.{ts,html,scss}      # shared by invoices and receipts
│   │   ├── tax-breakdown.component.{ts,html,scss}
│   │   ├── submission-history.component.{ts,html,scss}
│   │   ├── artifact-download.component.{ts,html,scss}
│   │   ├── conflict-resolution.dialog.{ts,html,scss}       # FR-025 side-by-side
│   │   └── bulk-status-check.dialog.{ts,html,scss}         # FR-012a progress + per-doc outcomes
│   └── invoice-routing.module.ts
├── receipts/                                           # NEW feature folder
│   ├── eta/
│   │   ├── eta-receipt-list.component.{ts,html,scss}
│   │   ├── eta-receipt-form.component.{ts,html,scss}
│   │   ├── eta-receipt-detail.component.{ts,html,scss}
│   │   └── services/eta-receipt.service.ts
│   └── receipt-routing.module.ts
├── app.routes.ts                                       # UPDATE: add /invoices and /receipts routes
├── layout/sidebar/sidebar.component.ts                 # UPDATE: add Invoices and Receipts entries gated by INVOICE/RECEIPT permissions
└── (existing admin/, auth/, dashboard/, customers/, items/, config/, shared/ untouched)
```

**Structure Decision**: Wave 7 is the first wave that populates the previously-empty `platform-eta` module — the authority engine (`EtaAuthorityEngine`, serializers, signing, token, status, HTTP client) lives there per Constitution XVI. Cross-cutting orchestration (the submit/cancel/retry/check-status flow that wires the engine to `submission_attempts`, `invoice_artifacts`, and the audit log) lives in `platform-api`'s `submission/` package, **not** in `platform-eta`, so that Wave 8's `platform-zatca` work can reuse the same orchestrator without changes. Domain entities, repositories, the lifecycle state machines, and the immutability-trigger Flyway migration all live in `platform-core`. The two frontend feature folders (`invoices/`, `receipts/`) co-locate their list/form/detail components with shared sub-components (line editor, submission history timeline, conflict dialog, bulk status check dialog) under `invoices/shared/` for reuse from receipts. No code lands in `platform-zatca` (Wave 8), `platform-jobs` (Wave 9 reconciliation), `platform-pdf` (Wave 9 PDF rendering), or `platform-security` (Wave 5 RBAC is reused unchanged).

## Complexity Tracking

> Three deliberate scope-narrowing decisions are flagged. Each is tighter than the constitution's default and explicitly justified.

| Decision (Tension)                                                                                                                  | Why Needed                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                            | Simpler / Standard Alternative Rejected Because                                                                                                                                                                                                                                                                                                                                                                                            |
|--------------------------------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Q1: Rejection is terminal.** The state machine treats `REJECTED` as a leaf state with no outgoing transitions, even though Constitution X.1 only requires that the lifecycle be "explicit and modeled as a state machine." | Auditors and ETA both treat a rejected document as a historical record of the failed submission attempt. Allowing a rejected document to be edited and resubmitted would either (a) mutate the snapshot ETA saw — destroying audit symmetry, or (b) require a "revision" sub-row concept that pollutes the schema. The clone-to-new-draft action (FR-007 second sentence) preserves the rejected snapshot byte-for-byte and creates a fresh row with a new number — clean separation, clean audit. | Editable rejection (Option B of Q1) was rejected because it forces every artifact ever attached to the rejected attempt to carry an implicit version number. Restoring rejection to DRAFT (Option A) was rejected because the rejected snapshot then becomes mutable, breaking Constitution IX.3 and XXI.4 by proxy. The terminal model adds zero schema cost and one extra endpoint (`POST /clone-as-draft`).                              |
| **Q2: No background polling.** `IN_REVIEW` reconciliation is user-driven (per-document and bulk **Check status**), not a scheduled job, even though Constitution XX.5 says ambiguous results "MUST land in a reconcilable status, never silently disappear." | The reconcilable status exists (`IN_REVIEW`, `SUBMISSION_AMBIGUOUS`) and the user-invoked refresh covers the reconciliation requirement. A scheduler would add ShedLock + a `platform-jobs` task + tenant-aware polling cadence + retry-on-error semantics — all infrastructure that doesn't pay back inside Wave 7's scope. Wave 9 explicitly owns the reconciliation tooling; this clarification simply moves the work there. | Background polling (Option B of Q2) was rejected because ShedLock-distributed scheduling is a non-trivial dependency that this wave doesn't need for any other purpose; introducing it just for ETA review-state reconciliation creates a single-use scheduler that Wave 9 will refactor. The bulk **Check status** on a multi-row selection delivers the same UX outcome with far less infrastructure.                                  |
| **Q3: No platform-side cancellation-window check.** `POST /cancel` forwards every request to ETA regardless of elapsed time, even though Constitution XIII.1 calls for three layers of validation (UI, domain, authority-compliance). | Cancellation-window length is an ETA-side operational decision that can change without notice. Hard-coding 48 hours (or any value) in the platform either over-blocks users when ETA has extended their window, or under-blocks them when ETA has shortened it, with no operational benefit either way. Forwarding the request and surfacing ETA's response verbatim is both more correct and one less divergence to keep in sync. Domain validation still applies (the document must be in an accepted state to attempt cancellation; that's a state-machine check, not a clock check). | UI-only window hint with a confirmation prompt was rejected because it still requires the platform to know the window length (same maintenance burden, no enforcement benefit). Strict platform-side enforcement was rejected for the same reason. The chosen model treats ETA as the authoritative oracle for "is this cancellable right now?" — symmetric with how every other ETA validation result is treated by the platform.        |

These three decisions narrow the implementation scope **inward**, not outward — they make Wave 7 smaller and more focused. The constitution permits each via the explicit-deviation pattern of `spec.md` clarifications, and each clarification cites the Constitution principle it interacts with.
