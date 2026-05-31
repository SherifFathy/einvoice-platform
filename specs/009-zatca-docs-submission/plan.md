# Implementation Plan: Wave 8 — ZATCA Document Tables & Submission Engine

**Branch**: `009-zatca-docs-submission` | **Date**: 2026-05-18 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/009-zatca-docs-submission/spec.md`

## Summary

Wave 8 builds the second **operational document layer** of the platform on top of Wave 7's ETA work, reusing the cross-authority operational substrate it left behind (`submission_attempts`, `invoice_artifacts`, `audit_logs`) and introducing the ZATCA-side equivalents of what ETA already has: **two ZATCA transaction-module table groups** — `zatca_standard_headers + zatca_standard_lines` (B2B, clearance flow) and `zatca_simplified_headers + zatca_simplified_lines` (B2C, reporting flow) — with VAT carried inline on the line row per Constitution XI.2 (no separate ZATCA tax table); the **ZATCA invoice hash chain** wired against the `zatca_chain_state` row Wave 6 already provisioned, with pessimistic `SELECT FOR UPDATE` acquisition (Constitution XII.3); UBL 2.1 XML generation + XAdES-BES signing + QR-TLV (Phase-2 nine-tag) encoding; and the clearance and reporting clients against the per-environment ZATCA endpoints.

Three new Flyway migrations (**V54–V56**) deliver the schema; **`platform-zatca`** becomes a populated module hosting `ZatcaStandardService`, `ZatcaSimplifiedService`, `ZatcaUblBuilder`, `ZatcaQrService`, `ZatcaHashService`, `ZatcaChainService`, and `ZatcaSigningService` (already partially present from a Wave 2 spike — refactored against the new schema); **`platform-api`** exposes ~22 REST endpoints (CRUD + submit/cancel/retry/check-status per Standard and per Simplified, plus per-document submissions and artifacts read endpoints) gated by Wave-5 `@RequiresPermission` against the STANDARD / SIMPLIFIED transaction-module permissions; two Angular feature folders (`standard/`, `simplified/`) ship list + detail + form screens, the bulk **Check status** action (this time with explicit no-cap pacing — see Q5), and the optimistic-concurrency conflict-resolution view reused from Wave 7.

Five clarifications recorded in `spec.md` shape this wave's implementation:

- **(Q1) Seven-state lifecycle, mirroring Wave 7.** `DRAFT → SUBMITTING → SUBMITTED → IN_REVIEW → ACCEPTED`, with terminal `REJECTED` and `CANCELLED`. Per-class authority status (`clearance_status` for Standard, `reporting_status` for Simplified) is a separate field on the header row, not a substitute for the platform status (FR-007b). This lets the Wave-7 `LifecycleTransitions` value object and the Angular permission-gated action buttons be reused with no behavioural changes.
- **(Q2) Bounded chain-lock wait (~30 s) with explicit error on timeout.** The submit endpoint blocks on `SELECT FOR UPDATE` against `zatca_chain_state` for up to ~30 s; on acquisition it proceeds; on timeout it returns `CHAIN_BUSY` with the document left in `DRAFT` (FR-009a). No `QUEUED` status, no background queue. JPA pessimistic-lock timeout is set per-transaction; a Postgres `lock_timeout` GUC fallback is configured on the submission transaction.
- **(Q3) One original document per credit/debit note.** `zatca_standard_headers.original_invoice_id` and `zatca_simplified_headers.original_invoice_id` are single-valued FKs; cross-class references are validation-rejected (FR-030). Consolidated (many-to-one) credit/debit notes are deferred to a later wave.
- **(Q4) Certificate validation: presence only.** `POST /submit` refuses only when no `zatca_configs` row exists for the active `(company, authorityEnvironment)`. Expiry and environment-binding checks are ZATCA-side (FR-011). The chain counter advances on every submission that reaches the authority, including rejections caused by expired certificates (Constitution XII.5 ↔ FR-010).
- **(Q5) No hard cap on bulk Check status.** The bulk endpoint accepts an unbounded `documentIds[]` list, processes them sequentially with a server-side rate-paced fan-out, reports per-document outcomes as they complete, and supports cancellation mid-run (FR-018a). The Angular dialog renders a progress bar with a Cancel button.

This wave is **the symmetric closure** of the operational layer: Wave 7 introduced the operational substrate and the first authority (ETA) onto it; Wave 8 plugs the second authority (ZATCA) into the same substrate without disturbing Wave 7. The constitutional posture is **enforce isolation everywhere a document is read or written** (Constitution II.5, XV.7), **make the lifecycle a true state machine** (Constitution X.1–4), **never overwrite a stored artifact or audit row** (Constitution IX, XXI), **keep the authority engine behind the shared `AuthorityEngine` adapter** so the Wave-7 `EtaSubmissionOrchestrator` is reused by the new `ZatcaSubmissionOrchestrator` with no orchestration-level branching (Constitution XVI), and **uphold ZATCA's hash-chain integrity rule under concurrent submissions** (Constitution XII.3–5).

## Technical Context

**Language/Version**: Java 17 (backend), TypeScript 5.x (frontend, Angular 19)

**Primary Dependencies**:
- Backend: Spring Boot 3.4.4 (web, data-jpa, security, validation, async), Spring Data JPA + Flyway (PostgreSQL 16), the Wave 5 `@RequiresPermission` AOP, `TenantContext`, `PermissionService`, `SessionContextAssembler`, the Wave 6 `ZatcaConfigRepository` (plain-text certificate + private key per Constitution XVIII), Wave 6 `ZatcaChainStateRepository` (single-row-per-context provisioned in V46), the Wave 7 shared substrate (`SubmissionAttempt`, `InvoiceArtifact`, `AuditLog`, `TransactionType` enum, `AuditService`), **xades4j 2.4.0** (ZATCA XAdES-BES signing — already on classpath from the Wave 2 spike), **BouncyCastle 1.80** (still required for the underlying crypto primitives — already present), **ZXing 3.5.x** (QR encoding — already present in `platform-zatca`'s `pom.xml`), Jackson, Lombok. No new third-party libraries introduced in this wave.
- Frontend: Angular 19, Angular Material, RxJS, the Wave 5 `SessionContextService`, `PermissionService`, `appHasPermission` directive, the Wave 7 `conflict-resolution.dialog`, `bulk-status-check.dialog`, `submission-history.component`, `artifact-download.component`, `line-items-editor.component`, `tax-breakdown.component`. No new third-party libraries.

**Storage**: PostgreSQL 16 (single instance via Docker Compose). Three new Flyway migrations:
- **V54** — `zatca_standard_headers` + `zatca_standard_lines`
- **V55** — `zatca_simplified_headers` + `zatca_simplified_lines`
- **V56** — Wave-8 compound indexes finalisation across the four new tables

`zatca_chain_state` was created in Wave 6's V46 and is reused unchanged. The Wave 7 `submission_attempts` / `invoice_artifacts` / `audit_logs` tables are reused unchanged — their `transaction_type` enum already accommodates `STANDARD` and `SIMPLIFIED` values (added in Wave 7's V52 alongside `INVOICE` and `RECEIPT`).

> Note on numbering: the Wave-8 section of `Docs/implementation-plan.md` references V53–V55. Wave 7 actually consumed V48–V53 (+ V53a hotfix during Phase 8 polish); Wave 8 therefore starts at V54 and runs through V56. The schema contents match the implementation-plan section verbatim aside from the version-number shift, with the additional polish noted under *Complexity Tracking* (FK to header table for `original_invoice_id` is enforced at the application layer per Constitution XI.6's pattern for cross-class FKs, not at the database layer — same self-reference handling as `eta_invoice_headers.original_document_id` in Wave 7).

**Testing**:
- Backend: JUnit 5 + Spring Boot Test + Testcontainers (`postgres:16-alpine`). Contract tests for every new endpoint group. Integration tests for: cross-environment isolation (Constitution XXIV.6 / SC-003), the full lifecycle state machine (Constitution X / SC-002), optimistic-concurrency conflict on drafts (FR-032), **concurrent submission chain integrity** (FR-009 / SC-005 — the chain-integrity test runs ≥ 100 concurrent-submission iterations against Testcontainers Postgres and asserts consecutive counters with correct previous-hash linkage), and rejection-still-advances-chain (FR-010 / SC-010). **Golden-file tests** for the ZATCA UBL 2.1 XML output (covering both Standard and Simplified including credit/debit variants) and for the QR-TLV nine-tag encoding (Constitution XXIV.1, mirrors Wave-8 Exit Criterion "Golden-file tests pass for ZATCA UBL XML and QR TLV").
- Frontend: Karma + Jasmine for components and services; component tests for the two new feature folders including the bulk-refresh selection model, the conflict-resolution view, and the chain-busy error toast (Q2).

**Target Platform**: On-prem Linux server (Docker Compose), browser (Chromium-based for UAT). Constitution Principle XIX.

**Project Type**: Multi-module Maven backend + Angular frontend. Existing layout from Wave 7: `platform-api`, `platform-core`, `platform-security`, `platform-eta` (populated in Wave 7), **`platform-zatca` (populated by this wave)**, `platform-jobs`, `platform-pdf`, `frontend/`.

**Performance Goals** (mirrors `spec.md` SC-001, SC-005, SC-008 and Constitution XXV):
- Single document submission p95 < 5 s end-to-end (form → chain-lock acquired → UBL built → signed → hashed → QR generated → ZATCA call → response stored → chain row updated) for a typical 5-line document, in nominal ZATCA conditions. Constitution XXV.1.
- Chain-lock contention: under 100 concurrent submissions against the same chain row, the first acquires within ~5 ms and each subsequent submission acquires within the bounded ~30 s window (Q2 ↔ FR-009a). Zero broken-chain outcomes across 100 iterations (SC-005).
- List screens for users with up to 10 assigned companies: first page p95 < 2 s; filter narrowing p95 < 500 ms. SC-008.
- Bulk **Check status** of an arbitrarily-large selection: server-side rate-pacing keeps the running average below ZATCA's documented per-minute call ceiling; per-document outcomes stream back to the client as they complete (no single deadline). FR-018a.
- All operational document queries hit a compound index on `(company_id, authority_environment_id, …)` per Constitution XXV.2.

**Constraints**:
- No outbound network call to ZATCA from any endpoint except `POST /submit`, `POST /cancel`, `POST /retry`, and `POST /check-status` (single + bulk).
- **Pessimistic chain lock with bounded wait** (Q2) — `SELECT FOR UPDATE` on `zatca_chain_state` with `lock_timeout = 30s` on the submission transaction; on timeout, throw `ChainBusyException` mapped to HTTP 503 `CHAIN_BUSY`. No advisory locks elsewhere; draft edits use optimistic `@Version` only (FR-032).
- **Chain counter advances on every submission that reaches the authority, including rejections** (Constitution X.5, XII.5 ↔ FR-010) — the counter increment and the `previous_invoice_hash` snapshot are part of the same DB transaction that opens the submission attempt; the ZATCA outbound call happens after the chain advance, so even a TIMEOUT or REJECT leaves a consistent chain.
- **No platform cancellation-window check** (carried over from Wave 7 Q3) — `POST /cancel` always forwards to ZATCA; the platform never short-circuits.
- **No background polling** (carried over from Wave 7 Q2) — `IN_REVIEW` reconciliation is user-driven via per-document and bulk Check status; no `@Scheduled` task in `platform-jobs`.
- **Rejected is terminal** (carried over from Wave 7 Q1) — `LifecycleTransitions` forbids any outgoing edge from `REJECTED`; clone-to-new-draft creates a fresh DRAFT row pre-populated from the rejected source.
- **Optimistic concurrency on drafts only** (Q-Wave-7 Q4 carry-over) — `@Version` on `ZatcaStandardHeader` and `ZatcaSimplifiedHeader`; pessimistic locking reserved strictly for the chain row.
- **English-only UI chrome** (carried over from Wave 7 Q5) — no i18n / RTL infrastructure introduced. Arabic data-field content is preserved byte-for-byte.
- **Append-only operational tables** (Constitution IX, XXI; FR-021, FR-023) — `invoice_artifacts` and `audit_logs` already enforce no-UPDATE/no-DELETE via repository discipline and the Wave-7 Postgres `BEFORE UPDATE OR DELETE` trigger (`enforce_append_only`). Wave 8 inherits this without changes.
- **Operational endpoints reject Admin Mode** with `COMPANY_CONTEXT_REQUIRED` (Constitution VII.4) — already enforced by the Wave 5 `TenantFilter`; reaffirmed here because Wave 8 is the second wave (after Wave 7) where Admin Mode users could attempt to load a document by ID.
- **Plain-text certificate storage** from Wave 6 is read by the signing path; certificates and private keys are loaded server-side only (Constitution VI.1–2, XVIII). Pre-submission cert-presence check (FR-011) only verifies a `zatca_configs` row exists; expiry/environment checks are ZATCA-side per Q4.
- **Two ZATCA-specific decimal regimes**: line quantity and unit price use NUMERIC(18,5) for source-of-truth calculation; header totals, line amounts, allowances, and VAT amounts use NUMERIC(18,2) per ZATCA spec (Constitution XIII.6 ↔ FR-005). A separate `ZatcaMoneyMath` helper enforces this — it does **not** share rounding logic with Wave 7's `EtaMoneyMath` (which is 5-decimal throughout).

**Scale/Scope**:
- 4 new tables, 3 Flyway migrations (V54–V56). `zatca_chain_state` reused from V46.
- ~24 new REST endpoints: list, create, read, update, delete (draft only), submit, cancel, retry, check-status (single), clone-to-new-draft, list submissions, get artifact (× 2 transaction types: Standard and Simplified = ~22 endpoints) plus 2 bulk **Check status** endpoints (one per transaction type).
- 2 new Angular feature folders (`standard/`, `simplified/`) with ~10 new components; most reuse the Wave 7 shared components in `invoices/shared/` (line-items editor, submission history timeline, conflict-resolution dialog, bulk-status-check dialog, artifact-download panel) — those shared components are promoted to `frontend/src/app/documents/shared/` in this wave to make the cross-authority reuse visible. Wave-7 imports are updated.
- 4 new domain entities + repositories (`ZatcaStandardHeader`, `ZatcaStandardLine`, `ZatcaSimplifiedHeader`, `ZatcaSimplifiedLine`) — no separate tax tables per Constitution XI.2.
- 4 new application services (`ZatcaStandardService`, `ZatcaSimplifiedService`, `ZatcaSubmissionOrchestrator`, `ZatcaChainService`) + 6 new authority-engine concerns in `platform-zatca` (`ZatcaAuthorityEngine`, `ZatcaUblBuilder`, `ZatcaQrService`, `ZatcaHashService`, `ZatcaSigningService` (refactored from Wave-2 spike), `ZatcaClearanceClient`, `ZatcaReportingClient`).
- Expected initial data per company per environment: 0–500k Standard documents/year, 0–2M Simplified documents/year, multiple submission attempts per document (bounded by retries), 1–5 artifacts per submitted document (UBL XML, signed XML, QR PNG, ZATCA request, ZATCA response, plus cleared XML for Standard).
- 33 functional requirements (FR-001 through FR-033), 6 user stories (2× P1, 3× P2, 1× P3), 10 success criteria.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

### Pre-Phase-0 Evaluation

| #     | Principle                                  | Status                | Notes |
|-------|--------------------------------------------|-----------------------|-------|
| I     | Compliance-First                           | ✅ Pass               | ZATCA Standard and Simplified documents serialized as UBL 2.1 XML and signed with XAdES-BES via xades4j (Constitution I.2). Five Standard transaction-type variants + three Simplified variants covered by spec FR-001/002. Golden-file tests required for UBL XML and QR TLV (Constitution XXIV.1 ↔ Wave-8 Exit Criterion). |
| II    | Multi-Tenant Isolation                     | ✅ Pass               | All 4 new tables carry `(company_id, authority_environment_id)` directly on the header (FR-003, FR-024). Lines reach the isolation key via FK to header. The Wave-6 `OperationalRepositorySupport` is extended with `ZatcaStandardSpecifications` and `ZatcaSimplifiedSpecifications`. |
| III   | Authority Environment Architecture         | ✅ Pass               | Every header table FK-references `authority_environments(id)`; JWT-bound `authorityEnvironmentId` is the single source of truth at read and write time (FR-003, FR-024). The three ZATCA environments (Production = 3, Simulation = 4, Sandbox = 5) each maintain an independent `zatca_chain_state` row per Constitution IV.5. Cross-environment leakage explicitly tested (SC-003 ↔ Constitution XXIV.6). |
| IV    | Branch + Environment Segregation           | ✅ Pass               | Each header table carries `branch_id` per Wave-8 SDK spec; submission_attempts and artifacts are segregated by environment via the header. Per Constitution IV.5 each ZATCA environment has its own `zatca_configs` row (Wave 6) and its own `zatca_chain_state` row (Wave 6). Production and non-production credentials never share runtime caches (Constitution IV.3). |
| V     | Stateless Service                          | ✅ Pass               | All new services and controllers are request-scoped. The `ZatcaChainService` opens a fresh JDBC transaction per submission, acquires `SELECT FOR UPDATE` within it, and never caches chain state outside the transaction (Constitution V.3). |
| VI    | Server-Side Cryptography                   | ✅ Pass               | All signing, hashing, QR generation happen in `platform-zatca`'s `ZatcaSigningService`, `ZatcaHashService`, `ZatcaQrService`; private keys are read from `zatca_configs` (plain-text per Wave-6 / Constitution XVIII) and never leave the JVM. The frontend never receives certificates, keys, or token values. |
| VII   | Admin Mode / Operational Mode Separation   | ✅ Pass               | All Wave 8 endpoints are operational and require non-null `companyId` from JWT. Admin Mode is rejected with `COMPANY_CONTEXT_REQUIRED` (FR-024 ↔ Constitution VII.4). Super User flag bypasses RBAC but still requires a company context for these endpoints. |
| VIII  | Transaction Module Architecture            | ✅ Pass               | Standard and Simplified are the two ZATCA transaction modules (Constitution VIII.1). Sidebar entries for both are added, each gated by the user's STANDARD / SIMPLIFIED permissions in the active `authority_environment_id`. List screens show all assigned companies with a Company column (Constitution VIII.5, XIV.5, FR-029). FR-028 enforces independent gating between the two classes. |
| IX    | Immutable Audit                            | ✅ Pass               | `audit_logs` reused unchanged from Wave 7. Every Wave-8 state-changing endpoint emits an audit row via the existing `AuditService` (FR-022). Audit rows carry `company_id` + `authority_environment_id` + the new `STANDARD` / `SIMPLIFIED` `transaction_type` (Constitution IX.5). |
| X     | Deterministic Document Lifecycle           | ✅ Pass               | The Wave-7 `LifecycleTransitions` value object is parameterised by transaction type and reused for Standard and Simplified; the seven-state machine from Q1 maps onto the existing `EtaInvoiceState` enum behavioural shape (the enum is renamed `DocumentState` and shared). Backend rejects invalid transitions even if the UI requests them (Constitution X.2). Every submission attempt is recorded as a separate row independent of canonical state (Constitution X.4 ↔ FR-014). Constitution X.5 ("even rejected invoices increment the chain counter") is structurally encoded by acquiring the chain row before the ZATCA call and committing the increment in the same transaction that opens the submission attempt (FR-010). |
| XI    | Physical Document Table Strategy           | ✅ Pass               | Per XI.1 explicit list: `zatca_standard_headers + zatca_standard_lines` and `zatca_simplified_headers + zatca_simplified_lines` — Wave 8 creates exactly these. Per XI.2 ZATCA lines have NO separate taxes table; VAT lives inline on the line. Per XI.4 environment is stored as `authority_environment_id` inside the header — no per-environment table groups. Per XI.5 `zatca_customers` and `zatca_items` (Wave 6) are shared across Standard and Simplified. Per XI.6 `submission_attempts` and `invoice_artifacts` reuse Wave 7's tables with the existing `transaction_type` discriminator extended to STANDARD/SIMPLIFIED values. |
| XII   | ZATCA Hash Chain Integrity                 | ✅ Pass               | This is the load-bearing principle of this wave. `zatca_chain_state` is reused from V46 (one row per `(company_id, authority_environment_id)` per XII.1). Both Standard and Simplified submissions share the same chain row per XII.2. Every submission acquires `SELECT FOR UPDATE` on the chain row per XII.3 (FR-009 ↔ ZatcaChainService.acquireForUpdate). Snapshot values (`invoice_counter_value`, `previous_invoice_hash`, `invoice_hash`, `qr_code_base64`) are persisted on the header at submission time per XII.4. Rejection still advances the counter per XII.5 (FR-010). The bounded chain-lock wait of ~30 s (Q2) is implemented as `SET LOCAL lock_timeout = '30s'` on the submission transaction, mapped to `CHAIN_BUSY` on timeout — no principle violation, this is a graceful degradation, not a bypass. |
| XIII  | Validation Layering                        | ✅ Pass               | Angular reactive form validators for guidance; Bean Validation + service-level domain rules on the backend (Standard requires buyer; VAT-exempt requires reason code + text; totals consistency at 2-decimal precision; original-document compatibility — same class, same company/environment; document-number uniqueness per company/environment); UBL builder enforces ZATCA-payload validation as the authority-compliance layer (Constitution XIII.1). Two-decimal rounding centralised in `ZatcaMoneyMath` (Constitution XIII.6; FR-005). |
| XIV   | Angular Engineering                        | ✅ Pass               | Reactive Forms only for both Standard and Simplified forms. Per-row and per-action buttons gated by permissions via `appHasPermission` (FR-028). List screens show records across all assigned companies with a Company column and Company filter (Constitution XIV.5; FR-029). Header chips from Wave 5 unchanged. The Wave-7 shared components are promoted to a cross-authority shared folder (`documents/shared/`); no business logic moves. |
| XV    | Spring Boot Engineering                    | ✅ Pass               | Layered (controller → application service → domain service → repository → authority engine adapter). Flyway V54–V56 are mandatory and additive (Constitution XV.5). `TenantContext` reused unchanged from Wave 5. Every operational repository query filters by `(company_id, authority_environment_id)` (Constitution XV.7) — extended `OperationalRepositorySupport`. `/api/session/context` returns the STANDARD and SIMPLIFIED permission sets per company per environment (Constitution XV.8). |
| XVI   | Authority Adapter                          | ✅ Pass               | The Wave-7 `AuthorityEngine` contract is implemented by a new `ZatcaAuthorityEngine` in `platform-zatca`. The Wave-7 `EtaSubmissionOrchestrator` is **promoted** to `SubmissionOrchestrator` (authority-agnostic, lives in `platform-api/submission/`); it accepts an `AuthorityEngine` strategy parameter resolved at runtime by transaction type. Adding ZATCA requires zero changes to the orchestrator (Constitution XVI.2–3). |
| XVII  | RBAC and Access Control                    | ✅ Pass               | STANDARD and SIMPLIFIED modules use the 8 action permissions from Constitution XVII.6: VIEW, CREATE, EDIT, DELETE, CANCEL, TRANSFER, REFRESH, SUBMIT. REFRESH maps to **Check status** (single + bulk). Default role permission sets from Constitution XVII.8 apply unchanged: COMPANY_ADMIN gets all 8 on both modules, ACCOUNTANT gets VIEW/CREATE/SUBMIT/REFRESH, VIEWER gets VIEW only. The clone-to-new-draft action requires CREATE. The conflict-resolution overwrite path requires EDIT. Distinct gating between Standard and Simplified (FR-028) is enforced both in the sidebar and at endpoint entry. |
| XVIII | Plain-Text Certificate Storage Policy      | ✅ Pass               | No new secret storage introduced in this wave. The signing path reads from `zatca_configs` (Wave 6 plain-text columns) and never persists secret material beyond what Wave 6 already stores. Phase-2 AES-256-GCM upgrade still lands at the storage layer with no schema migration. |
| XIX   | Secure On-Prem Deployment                  | ✅ Pass               | No deployment-topology change. HTTPS still mandatory; submission-bound traffic to ZATCA goes through the existing outbound proxy configuration. Per-environment ZATCA base URLs (`zatca_configs.base_url` populated in Wave 6) are used; runtime resolves them per submission. |
| XX    | Reliability and Recovery                   | ✅ Pass               | Document state is persisted before the outbound ZATCA call begins (Constitution XX.1) — the canonical row is in DB with status `SUBMITTING` and the chain counter is already advanced and persisted before the network call; the submission_attempt row is inserted in the same transaction. Retries are bounded (FR-017, retry produces a new attempt row, never re-advances the chain, never silently overwrites). Ambiguous results land in a reconcilable status (`IN_REVIEW`) per FR-017 ↔ Constitution XX.5. |
| XXI   | Document Preservation                      | ✅ Pass               | `invoice_artifacts` reused unchanged from Wave 7: append-only (Constitution XXI.4), distinguishes source-domain data (header/lines tables) from generated authority payloads (artifacts table). The artifact_type discriminator already includes UBL XML, signed XML, QR code, cleared XML, ZATCA request/response (the enum values were provisioned in Wave 7's V52 in anticipation). Regeneration after submission produces a new row rather than overwriting. |
| XXII  | Signed License Enforcement (Phase 2)       | N/A                   | No company-creation paths in this wave. |
| XXIII | Change Control                             | ✅ Pass               | spec.md → plan.md → tasks.md chain maintained. Five clarifications recorded with traceable IDs Q1–Q5. Constitution principles cited inline against each functional requirement. |
| XXIV  | Testing                                    | ✅ Pass               | Golden-file tests for ZATCA UBL XML across Standard + Simplified including credit/debit variants, and for the QR-TLV nine-tag encoding (Constitution XXIV.1). Unit tests on the seven-state lifecycle including the REJECTED-is-terminal rule and the chain-advances-on-rejection rule (Constitution XXIV.3 ↔ SC-010). Integration tests on tenant + authority-environment isolation (XXIV.5–6 ↔ SC-003). Contract tests on the new REST surface (XXIV.4 spirit). **Chain-integrity concurrency test** (≥ 100 concurrent submission iterations against Testcontainers Postgres — Constitution XII.3 ↔ SC-005) — this is the singular new test class introduced by this wave. |
| XXV   | Performance                                | ✅ Pass               | Submission target < 5 s nominal (Constitution XXV.1) — sized by SC-001 (4-minute end-to-end including data entry). Compound `(company_id, authority_environment_id, …)` indexes on every header table (Constitution XXV.2 ↔ V56). Bulk **Check status** runs asynchronously with progress reporting (Constitution XXV.3 ↔ FR-018a) — implemented as a server-side rate-paced fan-out within a single request with streaming response, not as background jobs. |
| XXVI  | Source of Truth                            | ✅ Pass               | spec.md is functional truth; this plan is implementation sequencing; tasks.md will be execution detail. |

**Gate Result**: ✅ PASS — two deliberate scope-narrowing decisions are flagged below in *Complexity Tracking* (Q3 and Q4), each tighter than the constitution's default would suggest but both explicitly justified. The remaining clarifications (Q1, Q2, Q5) are scope-clarifying rather than scope-narrowing and require no complexity-tracking entry.

### Post-Phase-1 Re-Evaluation

Re-evaluation after `data-model.md`, `contracts/`, and `quickstart.md` were generated:

- All 4 new entities in `data-model.md` carry `(companyId, authorityEnvironmentId)` directly on the header (Constitution II.5). Lines reach the isolation key transitively via FK to the header.
- The shared 7-state lifecycle (`DRAFT, SUBMITTING, SUBMITTED, IN_REVIEW, ACCEPTED, REJECTED, CANCELLED`) is modeled as the Wave-7 enum (renamed `DocumentState` and promoted to `platform-core/domain/shared/`); the transition matrix is the canonical source for backend rejection logic and Angular permission-gated action buttons. `REJECTED` and `CANCELLED` have no outgoing edges.
- The chain-acquisition contract is codified in `data-model.md`: `ZatcaChainService.acquireForUpdate(companyId, authorityEnvironmentId)` is the ONLY caller of `SELECT FOR UPDATE` on `zatca_chain_state`; the orchestrator depends on this method and never bypasses it.
- All endpoint contracts in `contracts/zatca-standard-api.openapi.yaml` and `contracts/zatca-simplified-api.openapi.yaml` pin the tenant scope to the JWT — no `companyId` query parameter is accepted on any list endpoint that the client could spoof. `authorityEnvironmentId` is never on the wire.
- `If-Match` and `ETag` semantics are codified in the contracts for the draft-edit endpoints (FR-032 optimistic concurrency). The conflict response is a structured 409 with both versions in the body — identical shape to Wave 7.
- The bulk **Check status** contract is unbounded in `documentIds[]` length per Q5; the response is streamed (NDJSON) with one line per document outcome. A `?cancel=true` follow-up call against the original request-id allows mid-run cancellation.
- `error-codes.md` extends the Wave-5/6/7 catalogue with `DUPLICATE_STANDARD_NUMBER`, `DUPLICATE_SIMPLIFIED_NUMBER`, `MISSING_BUYER_FOR_STANDARD`, `VAT_EXEMPTION_REASON_REQUIRED`, `CHAIN_BUSY`, `WRONG_ORIGINAL_CLASS`, and `ZATCA_VALIDATION_ERROR` (each tied to a constitution principle and an FR ID).
- Quickstart Phase D exercises the full happy path (create → submit → ZATCA clearance → artifact retrieval) for Standard on Sandbox; Phase E exercises Simplified on Sandbox; Phase F exercises the cross-environment isolation test (Constitution XXIV.6 ↔ SC-003); Phase G exercises **chain integrity under concurrent submissions** (Constitution XII.3 ↔ SC-005) — a scripted parallel-submit harness against two distinct draft Standard documents; Phase H exercises rejection-advances-chain + clone-to-new-draft (Q1 ↔ FR-010 ↔ SC-010); Phase I exercises the chain-busy timeout path (Q2 ↔ FR-009a).
- No new principle violations detected.

**Gate Result**: ✅ PASS.

## Project Structure

### Documentation (this feature)

```text
specs/009-zatca-docs-submission/
├── plan.md                                  # This file (/speckit.plan output)
├── research.md                              # Phase 0 output
├── data-model.md                            # Phase 1 output (entities + lifecycle + chain)
├── quickstart.md                            # Phase 1 output (smoke-test runbook)
├── contracts/
│   ├── zatca-standard-api.openapi.yaml      # /api/companies/{id}/zatca/standard/*
│   ├── zatca-simplified-api.openapi.yaml    # /api/companies/{id}/zatca/simplified/*
│   └── error-codes.md                       # Wave 8 additions to the catalogue
├── checklists/
│   └── requirements.md                      # Already created during /speckit.specify
└── tasks.md                                 # Will be created by /speckit.tasks
```

### Source Code (repository root)

Existing multi-module Maven backend layout from Wave 7. Wave 8 populates the previously-empty (or Wave-2-spike-only) `platform-zatca` module, adds files under `platform-core` (entities + Flyway), `platform-api` (REST controllers + application services), and two new Angular feature folders. No new Maven modules introduced; no files removed. The Wave-7 shared frontend components are promoted from `invoices/shared/` to `documents/shared/`.

```text
platform-core/
└── src/main/
    ├── java/com/einvoice/core/
    │   ├── domain/
    │   │   ├── zatca/
    │   │   │   ├── ZatcaStandardHeader.java               # NEW (@Version field — FR-032)
    │   │   │   ├── ZatcaStandardLine.java                 # NEW (VAT inline — Constitution XI.2)
    │   │   │   ├── ZatcaSimplifiedHeader.java             # NEW (@Version field — FR-032)
    │   │   │   ├── ZatcaSimplifiedLine.java               # NEW
    │   │   │   ├── ZatcaTransactionTypeCode.java          # NEW enum (Standard: 01.../ Simplified: 02...)
    │   │   │   ├── ZatcaVatCategory.java                  # NEW enum (S, Z, E, O)
    │   │   │   └── ZatcaDocumentClass.java                # NEW enum (STANDARD, SIMPLIFIED) — disambiguates from TransactionType
    │   │   └── shared/
    │   │       ├── DocumentState.java                     # RENAMED from EtaInvoiceState / EtaReceiptState (Wave 7) — single enum, 7 values
    │   │       └── LifecycleTransitions.java              # UPDATED — extended with STANDARD/SIMPLIFIED matrices
    │   ├── repository/
    │   │   ├── zatca/{ZatcaStandardHeaderRepository, ZatcaStandardLineRepository, ZatcaSimplifiedHeaderRepository, ZatcaSimplifiedLineRepository}.java
    │   │   └── support/
    │   │       ├── ZatcaStandardSpecifications.java       # NEW (extends OperationalRepositorySupport)
    │   │       └── ZatcaSimplifiedSpecifications.java     # NEW
    │   ├── lifecycle/
    │   │   ├── ZatcaStandardLifecycle.java                # NEW — pure-function state machine wrapper
    │   │   └── ZatcaSimplifiedLifecycle.java              # NEW
    │   ├── money/
    │   │   └── ZatcaMoneyMath.java                        # NEW — 2-decimal rounding helpers (Constitution XIII.6)
    │   └── error/
    │       ├── DuplicateStandardNumberException.java      # NEW (FR-006)
    │       ├── DuplicateSimplifiedNumberException.java    # NEW (FR-006)
    │       ├── MissingBuyerForStandardException.java      # NEW (FR-008)
    │       ├── VatExemptionReasonRequiredException.java   # NEW (FR-004)
    │       ├── ChainBusyException.java                    # NEW (FR-009a, Q2)
    │       ├── WrongOriginalClassException.java           # NEW (FR-030)
    │       └── (DocumentNotDraftException, InvalidLifecycleTransitionException, OptimisticLockConflictException, MissingOriginalDocumentException, TotalsInconsistentException are reused from Wave 7)
    └── resources/db/migration/
        ├── V54__zatca_standard_tables.sql                 # NEW
        ├── V55__zatca_simplified_tables.sql               # NEW
        └── V56__wave8_compound_indexes.sql                # NEW

platform-zatca/
└── src/main/java/com/einvoice/zatca/
    ├── engine/
    │   └── ZatcaAuthorityEngine.java                      # NEW — implements AuthorityEngine contract (submit, cancel, checkStatus)
    ├── build/
    │   ├── ZatcaUblBuilder.java                           # NEW — maps ZatcaStandardHeader/ZatcaSimplifiedHeader → UBL 2.1 XML
    │   └── ZatcaUblCanonicaliser.java                     # NEW — C14N canonicalisation used by hash + signature
    ├── sign/
    │   └── ZatcaSigningService.java                       # REFACTORED — XAdES-BES via xades4j, reads cert + key from zatca_configs
    ├── hash/
    │   └── ZatcaHashService.java                          # NEW — SHA-256 of canonicalised signed UBL
    ├── chain/
    │   └── ZatcaChainService.java                         # NEW — SELECT FOR UPDATE on zatca_chain_state, with 30s lock_timeout
    ├── qr/
    │   └── ZatcaQrService.java                            # NEW — TLV encoder for Phase-2 9-tag QR; PNG render via ZXing
    ├── client/
    │   ├── ZatcaClearanceClient.java                      # REFACTORED from Wave-2 spike — POST /invoices/clearance/single
    │   ├── ZatcaReportingClient.java                      # REFACTORED — POST /invoices/reporting/single
    │   └── ZatcaHttpClient.java                           # NEW — thin HTTP client; per-environment base URL from zatca_configs
    └── status/
        └── ZatcaStatusService.java                        # NEW — single + bulk "Check status" against ZATCA

platform-api/
└── src/main/java/com/einvoice/api/
    ├── zatca/
    │   ├── standard/
    │   │   ├── ZatcaStandardController.java               # NEW
    │   │   └── service/
    │   │       ├── ZatcaStandardService.java              # NEW — CRUD + clone-to-new-draft
    │   │       └── ZatcaStandardFormMapper.java           # NEW — DTO ↔ entity
    │   ├── simplified/
    │   │   ├── ZatcaSimplifiedController.java             # NEW
    │   │   └── service/
    │   │       ├── ZatcaSimplifiedService.java            # NEW
    │   │       └── ZatcaSimplifiedFormMapper.java         # NEW
    │   └── submission/
    │       ├── ZatcaSubmissionController.java             # NEW — submit/cancel/retry/check-status (single + bulk)
    │       └── service/
    │           └── ZatcaSubmissionOrchestrator.java       # NEW — composes ZatcaAuthorityEngine + ZatcaChainService + attempts + artifacts + audit
    ├── submission/                                        # UPDATE — promote Wave-7 EtaSubmissionOrchestrator → authority-agnostic SubmissionOrchestrator (delegated to via engines)
    ├── error/
    │   └── GlobalExceptionHandler.java                    # UPDATE: map seven new exceptions to error codes
    └── (existing admin/, auth/, session/, config/, master-data/, eta/ folders untouched)

frontend/src/app/
├── documents/                                             # NEW — cross-authority shared components (promoted from Wave-7 invoices/shared/)
│   └── shared/
│       ├── line-items-editor.component.{ts,html,scss}     # MOVED from invoices/shared (Wave 7)
│       ├── submission-history.component.{ts,html,scss}    # MOVED
│       ├── artifact-download.component.{ts,html,scss}     # MOVED
│       ├── conflict-resolution.dialog.{ts,html,scss}      # MOVED
│       └── bulk-status-check.dialog.{ts,html,scss}        # MOVED (extended: no cap, cancel button, NDJSON stream consumer per Q5)
├── standard/                                              # NEW feature folder
│   ├── zatca-standard-list.component.{ts,html,scss}
│   ├── zatca-standard-form.component.{ts,html,scss}
│   ├── zatca-standard-detail.component.{ts,html,scss}    # shows QR image, chain snapshot, clearance status
│   ├── services/zatca-standard.service.ts
│   └── standard-routing.module.ts
├── simplified/                                            # NEW feature folder
│   ├── zatca-simplified-list.component.{ts,html,scss}
│   ├── zatca-simplified-form.component.{ts,html,scss}
│   ├── zatca-simplified-detail.component.{ts,html,scss}  # shows QR image, chain snapshot, reporting status
│   ├── services/zatca-simplified.service.ts
│   └── simplified-routing.module.ts
├── app.routes.ts                                          # UPDATE: add /standard and /simplified routes
├── layout/sidebar/sidebar.component.ts                    # UPDATE: add Standard and Simplified entries gated by STANDARD/SIMPLIFIED permissions
├── invoices/                                              # UPDATE: import shared components from new documents/shared path
└── receipts/                                              # UPDATE: same import path migration
```

**Structure Decision**: Wave 8 finally populates `platform-zatca` (which has held only Wave-2-spike code so far) — the authority engine (`ZatcaAuthorityEngine`, UBL builder, signing, hashing, QR, chain service, clearance and reporting clients, status service) lives there per Constitution XVI. Cross-cutting orchestration (the submit/cancel/retry/check-status flow that wires the engine to `submission_attempts`, `invoice_artifacts`, the chain service, and the audit log) lives in `platform-api`'s `submission/` package as a new `ZatcaSubmissionOrchestrator`, with the **promotion** of Wave-7's `EtaSubmissionOrchestrator` to an authority-agnostic `SubmissionOrchestrator` happening incrementally — the simplest first step is to ship `ZatcaSubmissionOrchestrator` alongside `EtaSubmissionOrchestrator`, with both delegating shared behaviour through a new `AuthorityEngine`-shaped extension point. A full merger of the two orchestrators is **out of scope for this wave** and is captured as a tasks-template polish item. Domain entities, repositories, lifecycle wrappers, and the Flyway migrations all live in `platform-core`. The two frontend feature folders (`standard/`, `simplified/`) reuse the Wave-7 shared components, which are promoted from `invoices/shared/` to a new cross-authority `documents/shared/` folder; Wave-7 imports are updated as part of this wave's polish. No code lands in `platform-jobs` (still Wave 9 reconciliation territory), `platform-pdf` (Wave 9 PDF rendering), or `platform-security` (Wave 5 RBAC reused unchanged).

## Complexity Tracking

> Two deliberate scope-narrowing decisions are flagged. Each is tighter than the constitution's default and explicitly justified.

| Decision (Tension)                                                                                                                                                                | Why Needed                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                       | Simpler / Standard Alternative Rejected Because                                                                                                                                                                                                                                                                                                                                                                                                                                                          |
|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Q3: Exactly one original document per credit/debit note.** `original_invoice_id` is single-valued (FK self-reference), even though ZATCA UBL 2.1 BillingReference supports a list. | Wave 8's customer base — Phase-1 on-prem deployments — does not yet have a credit-note workflow that consolidates against multiple originals. Shipping the single-valued model now lets the form be a simple picker, the validation rule be a same-class same-context FK check, and the audit chain be straightforward. Adding consolidated notes later requires an additive `zatca_credit_note_original_refs` join table — no schema migration to the header. The single-valued shape covers ≥ 95% of credit/debit notes seen at ZATCA Phase-2 go-live. | Many-to-one from the start (Option B of Q3) was rejected because it forces a join table, a multi-select UI, a cross-document totals-consistency validation rule, and a per-original audit linkage — all infrastructure the wave does not yet need. The forward-compatibility variant (Option C) was rejected because explicit deferral with an additive plan is cleaner than a "we'll widen this later" footnote in the schema.                                                                          |
| **Q4: Certificate validity = presence only.** `POST /submit` refuses solely when no `zatca_configs` row exists; expiry and environment-binding checks are ZATCA-side, even though Constitution XIII.1 calls for three layers of validation (UI, domain, authority-compliance).      | ZATCA itself is the authoritative oracle for "is this certificate valid right now?" — both the production certificate (1 year typical) and the compliance certificate (1 year typical) have ZATCA-defined renewal windows and revocation rules that the platform cannot mirror without duplicating ZATCA's state. A client-side expiry check that disagrees with ZATCA is a worse user experience than letting ZATCA reject and surfacing the rejection verbatim. The chain counter advancing on those rejections (Constitution XII.5) is the correct behaviour: it preserves the chain even when the cert is bad. This is symmetric with Wave-7 Q3's "no cancellation-window check". | Client-side expiry check (Option C of Q4) was rejected because it splits truth between the platform's certificate parser and ZATCA's certificate validation rules — every divergence produces a misleading error. Full validation (Option A) compounds the divergence with environment-binding rules the platform does not natively know. The chosen model treats ZATCA as the authoritative oracle for cert validity — symmetric with how every other ZATCA validation result is treated by the platform. |

The remaining clarifications (Q1 seven-state lifecycle, Q2 bounded 30 s chain-lock wait, Q5 uncapped bulk Check status with rate pacing) are **scope-clarifying**, not scope-narrowing — they pin behaviours the constitution already gestures at without overriding any principle. They are documented inline in the Summary and the Constitution Check, and require no complexity-tracking entry.
