---
description: "Task list for Wave 8 — ZATCA Document Tables & Submission Engine"
---

# Tasks: Wave 8 — ZATCA Document Tables & Submission Engine

**Input**: Design documents from `/specs/009-zatca-docs-submission/`
**Prerequisites**: `plan.md`, `spec.md`, `research.md`, `data-model.md`, `contracts/`, `quickstart.md`

**Tests**: Included. Constitution XXIV (Testing) requires golden-file tests for authority engines, lifecycle transition tests, tenant/authority-environment isolation tests, and (for this wave specifically) ZATCA chain-integrity concurrency tests. Spec SC-005 and SC-010 mandate these as success criteria.

**Organization**: Phases 1 and 2 are shared infrastructure that both P1 user stories depend on (chain service, signing, UBL skeleton, orchestrator, shared substrate plumbing). Each user-story phase then adds the per-class or per-concern code on top, with its own contract and integration tests. The shared-infrastructure approach matches the Plan §Structure Decision and avoids artificial duplication between US1 (Standard) and US2 (Simplified) — both are P1 MVP stories and share most of the new code.

## Format: `[TaskID] [P?] [Story?] Description with file path`

- **[P]**: Different file, no dependency on incomplete tasks — parallelisable.
- **[Story]**: `[US1]`–`[US6]` maps the task to a user story. Setup, Foundational, and Polish phases carry no story label.
- All paths are repository-relative.

---

## Phase 1: Setup

**Purpose**: Confirm shared third-party dependencies and module wiring before any new code lands.

- [x] T001 Confirm `platform-zatca/pom.xml` declares xades4j 2.4.0, BouncyCastle 1.80, and ZXing 3.5.x as runtime dependencies; if missing, add — these are the only third-party libs introduced/relied upon in this wave (research §2, §3, §5)
- [x] T002 [P] Confirm `platform-core/pom.xml` exposes `platform-core`'s `domain.shared.*` package (the future home of `DocumentState`, `LifecycleTransitions`, `AuthorityEngine`) to `platform-zatca` and `platform-api` via Maven module dependencies; no new modules added
- [x] T003 [P] Add `frontend/src/app/documents/shared/index.ts` re-export barrel file as the future cross-authority component home; empty barrel ok for now

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Schema, shared domain, shared authority-engine infrastructure, and shared backend plumbing that both Standard and Simplified depend on. No user story can begin until this phase is complete.

**⚠️ CRITICAL**: Includes the chain service, signing service, hashing, QR, HTTP client, orchestrator, and the `AuthorityEngine` SPI. These are intentionally placed here so US1 and US2 are reduced to per-class glue.

### Shared domain enum / lifecycle refactor (research §7)

- [ ] T004 Rename `platform-core/src/main/java/com/einvoice/core/domain/eta/lifecycle/EtaInvoiceState.java` → `platform-core/src/main/java/com/einvoice/core/domain/shared/DocumentState.java` (move package, keep 7 values: `DRAFT, SUBMITTING, SUBMITTED, IN_REVIEW, ACCEPTED, REJECTED, CANCELLED`); delete the parallel `EtaReceiptState.java`. Wave-7 tests update their imports.
- [ ] T005 Update `platform-core/src/main/java/com/einvoice/core/domain/eta/lifecycle/LifecycleTransitions.java` → move to `platform-core/src/main/java/com/einvoice/core/domain/shared/LifecycleTransitions.java`; parameterise the transition matrix by `TransactionType` (`INVOICE, RECEIPT, STANDARD, SIMPLIFIED`); preserve Wave-7 INVOICE / RECEIPT matrices unchanged; STANDARD and SIMPLIFIED matrices to follow in T037 once the entities exist
- [ ] T006 [P] Update Wave-7 imports across `platform-core`, `platform-eta`, `platform-api`, `frontend/src/app/invoices`, `frontend/src/app/receipts` to reference the new `DocumentState` and `LifecycleTransitions` locations. Search-and-replace; no behavioural change

### Frontend shared component promotion (research §13)

- [ ] T007 [P] Move `frontend/src/app/invoices/shared/line-items-editor.component.{ts,html,scss}` → `frontend/src/app/documents/shared/line-items-editor.component.{ts,html,scss}`
- [ ] T008 [P] Move `frontend/src/app/invoices/shared/submission-history.component.{ts,html,scss}` → `frontend/src/app/documents/shared/`
- [ ] T009 [P] Move `frontend/src/app/invoices/shared/artifact-download.component.{ts,html,scss}` → `frontend/src/app/documents/shared/`
- [ ] T010 [P] Move `frontend/src/app/invoices/shared/conflict-resolution.dialog.{ts,html,scss}` → `frontend/src/app/documents/shared/`
- [ ] T011 [P] Move `frontend/src/app/invoices/shared/bulk-status-check.dialog.{ts,html,scss}` → `frontend/src/app/documents/shared/` (file move only; the uncapped/NDJSON/Cancel behavioural changes are owned by T113 in Phase 9)
- [ ] T012 Update Wave-7 `frontend/src/app/invoices/**` and `frontend/src/app/receipts/**` imports to point at `documents/shared/`

### Flyway migrations (data-model.md)

- [ ] T013 [P] Create `platform-core/src/main/resources/db/migration/V54__zatca_standard_tables.sql` — `zatca_standard_headers` + `zatca_standard_lines` with `CHECK (vat_exempt_reason)` on lines, unique `(company_id, authority_environment_id, invoice_number)` on headers, FK self-reference on `original_invoice_id`, `version` bigint column for optimistic concurrency
- [ ] T014 [P] Create `platform-core/src/main/resources/db/migration/V55__zatca_simplified_tables.sql` — same shape as V54 with: `buyer_data` nullable, `transaction_type_code` constrained to `02` prefix at app layer (not DB), `reporting_status` column replacing `clearance_status`
- [ ] T015 [P] Create `platform-core/src/main/resources/db/migration/V56__wave8_compound_indexes.sql` — eight indexes per data-model.md §Indexes (V56) including partial indexes on `original_invoice_id`

### Domain entities + repositories

- [ ] T016 [P] Create `platform-core/src/main/java/com/einvoice/core/domain/zatca/ZatcaStandardHeader.java` (JPA `@Entity`, `@Version` field, mapped to `zatca_standard_headers`, JSONB seller_data/buyer_data/zatca_response_data via `@Type` or Hibernate JsonType)
- [ ] T017 [P] Create `platform-core/src/main/java/com/einvoice/core/domain/zatca/ZatcaStandardLine.java` (JPA entity, FK to header with `@ManyToOne`, inline VAT fields per Constitution XI.2)
- [ ] T018 [P] Create `platform-core/src/main/java/com/einvoice/core/domain/zatca/ZatcaSimplifiedHeader.java` (`@Version` field, `buyerData` field nullable)
- [ ] T019 [P] Create `platform-core/src/main/java/com/einvoice/core/domain/zatca/ZatcaSimplifiedLine.java`
- [ ] T020 [P] Create `platform-core/src/main/java/com/einvoice/core/domain/zatca/ZatcaVatCategory.java` enum (`S, Z, E, O`)
- [ ] T021 [P] Create `platform-core/src/main/java/com/einvoice/core/domain/zatca/ZatcaDocumentClass.java` enum (`STANDARD, SIMPLIFIED`)
- [ ] T022 Extend `platform-core/src/main/java/com/einvoice/core/domain/shared/TransactionType.java` (from Wave 7) — add `STANDARD` and `SIMPLIFIED` values if not already present
- [ ] T023 [P] Create `platform-core/src/main/java/com/einvoice/core/repository/zatca/ZatcaStandardHeaderRepository.java` extends `JpaRepository<ZatcaStandardHeader, UUID>, JpaSpecificationExecutor`
- [ ] T024 [P] Create `platform-core/src/main/java/com/einvoice/core/repository/zatca/ZatcaStandardLineRepository.java`
- [ ] T025 [P] Create `platform-core/src/main/java/com/einvoice/core/repository/zatca/ZatcaSimplifiedHeaderRepository.java`
- [ ] T026 [P] Create `platform-core/src/main/java/com/einvoice/core/repository/zatca/ZatcaSimplifiedLineRepository.java`
- [ ] T027 [P] Create `platform-core/src/main/java/com/einvoice/core/repository/support/ZatcaStandardSpecifications.java` — extends Wave-6 `OperationalRepositorySupport`; canonical `(companyId, authorityEnvironmentId)` predicate plus status/date/company filters
- [ ] T028 [P] Create `platform-core/src/main/java/com/einvoice/core/repository/support/ZatcaSimplifiedSpecifications.java` — parallel of T027

### Shared utility / exception classes

- [ ] T029 [P] Create `platform-core/src/main/java/com/einvoice/core/money/ZatcaMoneyMath.java` — 2-decimal HALF_EVEN rounding helpers (research §11)
- [ ] T030 [P] Create `platform-core/src/main/java/com/einvoice/core/error/DuplicateStandardNumberException.java` (FR-006)
- [ ] T031 [P] Create `platform-core/src/main/java/com/einvoice/core/error/DuplicateSimplifiedNumberException.java` (FR-006)
- [ ] T032 [P] Create `platform-core/src/main/java/com/einvoice/core/error/MissingBuyerForStandardException.java` (FR-008)
- [ ] T033 [P] Create `platform-core/src/main/java/com/einvoice/core/error/VatExemptionReasonRequiredException.java` (FR-004)
- [ ] T034 [P] Create `platform-core/src/main/java/com/einvoice/core/error/ChainBusyException.java` (FR-009a, Q2)
- [ ] T035 [P] Create `platform-core/src/main/java/com/einvoice/core/error/WrongOriginalClassException.java` (FR-030)
- [ ] T036 Update `platform-api/src/main/java/com/einvoice/api/error/GlobalExceptionHandler.java` — map the seven new exceptions (T030–T035 plus `OptimisticLockException` reuse) to their HTTP status + `error-codes.md` codes; `ChainBusyException` → 503

### Lifecycle wiring (depends on T005 and T016–T019)

- [ ] T037 Update `platform-core/src/main/java/com/einvoice/core/domain/shared/LifecycleTransitions.java` — append STANDARD and SIMPLIFIED transition matrices per data-model.md §Transition rules; both classes share the same shape (terminal `REJECTED` and `CANCELLED`); enforce in `assertAllowed(from, to, txType)`
- [ ] T038 [P] Create `platform-core/src/main/java/com/einvoice/core/lifecycle/ZatcaStandardLifecycle.java` — thin wrapper around `LifecycleTransitions` for callers that pass a `ZatcaStandardHeader`
- [ ] T039 [P] Create `platform-core/src/main/java/com/einvoice/core/lifecycle/ZatcaSimplifiedLifecycle.java` — parallel wrapper

### Authority-engine SPI (research §9)

- [ ] T040 Create `platform-core/src/main/java/com/einvoice/core/domain/shared/AuthorityEngine.java` — interface declaring `submit(document)`, `cancel(document, reason)`, `checkStatus(document)`; result types come from the existing `SubmissionOutcome` (Wave 7) and `SubmissionAttempt` entity
- [ ] T041 Update `platform-api/src/main/java/com/einvoice/api/eta/submission/service/EtaSubmissionOrchestrator.java` — extract method signatures so the engine-dependent parts of the flow accept an `AuthorityEngine` strategy. Acceptance: both `EtaSubmissionOrchestrator` and the new `ZatcaSubmissionOrchestrator` import `AuthorityEngine` from `platform-core/domain/shared/`, contain zero authority-specific branching outside `engine.submit/cancel/checkStatus` calls, and a static check (`grep -nE "(eta|zatca)\." platform-api/src/main/java/.../*Orchestrator.java` filtered to non-comment lines) returns no authority-name occurrences other than `engine` calls and class names. Full orchestrator merger remains deferred per research §9
- [ ] T042 [P] Create `platform-api/src/main/java/com/einvoice/api/config/AuthorityEngineRegistry.java` — Spring `@Configuration` exposing a `Map<TransactionType, AuthorityEngine>` resolved at startup

### Shared backend plumbing — ZATCA engine internals (research §1–§6)

- [ ] T043 Create `platform-zatca/src/main/java/com/einvoice/zatca/chain/ZatcaChainService.java` — single class permitted to call `SELECT FOR UPDATE` on `zatca_chain_state`; opens its own `@Transactional(propagation = REQUIRES_NEW)`; `SET LOCAL lock_timeout = '30s'`; method `acquireForUpdate(companyId, authorityEnvironmentId)` returns the locked row; method `advance(chainRow, newHash)` increments counter + updates previous_invoice_hash; PSQL `55P03` translated to `ChainBusyException`
- [ ] T044 [P] Create `platform-zatca/src/main/java/com/einvoice/zatca/build/ZatcaUblCanonicaliser.java` — wraps Apache Santuario `Canonicalizer11_OmitComments`; returns canonical bytes for hashing and signing input (research §4)
- [ ] T045 [P] Refactor `platform-zatca/src/main/java/com/einvoice/zatca/sign/ZatcaSigningService.java` (Wave-2 spike) — XAdES-BES via xades4j 2.4.0; reads cert + private key from `zatca_configs` via `ZatcaConfigRepository` per request; never caches key material (Constitution V.3, VI.2)
- [ ] T046 [P] Create `platform-zatca/src/main/java/com/einvoice/zatca/hash/ZatcaHashService.java` — `SHA-256(canonicaliser.canonicalise(signedUblBytes))`; returns hex string
- [ ] T047 [P] Create `platform-zatca/src/main/java/com/einvoice/zatca/qr/ZatcaQrService.java` — TLV encoder for the nine Phase-2 tags (research §5); Base64 output for storage; PNG render via ZXing `QRCodeWriter` at 300×300, error-correction L
- [ ] T048 [P] Create `platform-zatca/src/main/java/com/einvoice/zatca/client/ZatcaHttpClient.java` — thin Spring `RestClient` wrapper; per-request base URL from `zatca_configs.base_url`; connect timeout 10 s, read timeout 30 s (research §6)
- [ ] T049 [P] Create `platform-zatca/src/main/java/com/einvoice/zatca/build/ZatcaUblBuilder.java` SKELETON — accepts either `ZatcaStandardHeader` or `ZatcaSimplifiedHeader`, returns JAXB-generated UBL `Invoice` element; the Standard- and Simplified-specific branch implementations land in US1 (T067) and US2 (T086)
- [ ] T050 Create `platform-zatca/src/main/java/com/einvoice/zatca/engine/ZatcaAuthorityEngine.java` — implements `AuthorityEngine`; orchestrates `ZatcaUblBuilder` → `ZatcaSigningService` → `ZatcaHashService` → `ZatcaQrService` → clearance or reporting client based on `TransactionType` from the document; depends on T043–T049 plus the client beans introduced in US1/US2

### Shared backend plumbing — submission orchestrator and audit extension

- [ ] T051 Create `platform-api/src/main/java/com/einvoice/api/zatca/submission/service/ZatcaSubmissionOrchestrator.java` — composes `ZatcaChainService.acquireForUpdate` → entity `transitionStatus(SUBMITTING)` → `ZatcaChainService.advance` (counter + previous_hash) → `submission_attempts.save` (with `chain_counter_snapshot`) → `ZatcaAuthorityEngine.submit` → persist response artifact → `submission_attempts` finalise → entity `transitionStatus(<outcome>)` → `AuditService.recordAction`. All steps before the outbound call are in a single `@Transactional`; the outbound call is outside the transaction; the finalisation runs in a fresh short transaction (data-model §App-layer invariants 2 + 6)
- [ ] T052 Update `platform-api/src/main/java/com/einvoice/api/audit/service/AuditService.java` (Wave 7) — extend `EntityType` enum to include `ZATCA_STANDARD` and `ZATCA_SIMPLIFIED`; payload-before / payload-after JSON serialisation for the new entities
- [ ] T053 [P] Update `platform-core/src/main/java/com/einvoice/core/domain/shared/InvoiceArtifact.java` (Wave 7) — extend `ArtifactType` enum to include `UBL_XML`, `SIGNED_UBL_XML`, `QR_PNG`, `CLEARED_XML`, `ZATCA_REQUEST`, `ZATCA_RESPONSE` if not already present

### Backend session-context permission extension

- [ ] T054 Update `platform-api/src/main/java/com/einvoice/api/session/SessionContextAssembler.java` (Wave 5) — emit STANDARD and SIMPLIFIED permission objects per company per environment in the `/api/session/context` payload (Constitution XV.8, FR-028)

### Foundational tests

- [ ] T055 [P] Create `platform-core/src/test/java/com/einvoice/core/lifecycle/LifecycleTransitionsTest.java` — assert STANDARD and SIMPLIFIED matrices match data-model.md §Transition rules; assert `REJECTED` and `CANCELLED` have no outgoing edges; assert any other transition raises `InvalidLifecycleTransitionException`
- [ ] T056 [P] Create `platform-core/src/test/java/com/einvoice/core/money/ZatcaMoneyMathTest.java` — banker's-rounding HALF_EVEN behaviour at 2 decimals; ZATCA-spec edge cases (0.005 → 0.00, 0.015 → 0.02)

**Checkpoint**: Foundation ready — Standard and Simplified work can now proceed in parallel.

---

## Phase 3: User Story 1 — Create, sign, clear, and persist a ZATCA Standard (B2B) (Priority: P1) 🎯 MVP

**Goal**: A finance user creates a ZATCA Standard tax document end-to-end through the Angular UI, signs it, advances the chain, transmits it through ZATCA clearance, and observes the cleared status, chain snapshot, QR code, and ZATCA UUID stored against the document.

**Independent Test**: Phase D of `quickstart.md` — from a fresh ZATCA Sandbox session, create a Standard document, click Submit, confirm `status = ACCEPTED`, `clearanceStatus = CLEARED`, chain counter advanced by 1, QR PNG downloadable. No Simplified, retry, cancel, or bulk-status code required.

### Tests for User Story 1

- [ ] T057 [P] [US1] Create `platform-zatca/src/test/java/com/einvoice/zatca/build/ZatcaUblBuilderStandardGoldenFileTest.java` — golden-file UBL XML for each Standard transaction-type variant (`0100000` standard, `0100001` self-billed, `0100010` third-party, `0100100` export, `381` credit, `383` debit); golden files committed under `platform-zatca/src/test/resources/golden/standard/`
- [ ] T058 [P] [US1] Create `platform-zatca/src/test/java/com/einvoice/zatca/qr/ZatcaQrTlvGoldenFileTest.java` — golden-file TLV byte sequence covering all nine Phase-2 tags (research §5)
- [ ] T059 [P] [US1] Create `platform-api/src/test/java/com/einvoice/api/zatca/standard/ZatcaStandardControllerContractTest.java` — exercises every endpoint in `contracts/zatca-standard-api.openapi.yaml` against a `@SpringBootTest`-wired controller; asserts request/response shape conformance and ETag/If-Match handling
- [ ] T060 [P] [US1] Create `platform-api/src/test/java/com/einvoice/api/zatca/standard/ZatcaStandardHappyPathIT.java` — Testcontainers Postgres + WireMock ZATCA Sandbox; create → submit → assert ACCEPTED, chain counter = 1, QR PNG artifact present, CLEARED_XML artifact present
- [ ] T061 [P] [US1] Create `platform-core/src/test/java/com/einvoice/core/repository/zatca/ZatcaStandardSpecificationsTest.java` — every query path filters by `(companyId, authorityEnvironmentId)` (Constitution XV.7)

### Implementation for User Story 1

- [ ] T062 [P] [US1] Create `platform-api/src/main/java/com/einvoice/api/zatca/standard/service/ZatcaStandardFormMapper.java` — DTO ↔ entity mapping; computes line totals via `ZatcaMoneyMath`; populates `seller_data` / `buyer_data` JSONB
- [ ] T063 [US1] Create `platform-api/src/main/java/com/einvoice/api/zatca/standard/service/ZatcaStandardService.java` — `create(dto)`, `update(id, dto, expectedVersion)`, `delete(id)`, `findById(id)`, `findByContext(filters, pageable)`, `cloneAsDraft(sourceId, newNumber)`, `transitionStatus(id, to)`. Enforces FR-006 (uniqueness via DB constraint catch), FR-007 (DRAFT-only edits), FR-008 (buyer required), FR-030 (original same-class same-context), FR-031 (totals consistency), FR-032 (`@Version` mismatch → `OptimisticLockException` → 409); depends on T016, T017, T023, T024, T027, T037, T062
- [ ] T064 [US1] Create `platform-api/src/main/java/com/einvoice/api/zatca/standard/ZatcaStandardController.java` — implements every path in `contracts/zatca-standard-api.openapi.yaml` except `/submit`, `/cancel`, `/retry`, `/check-status` (those land in US3); `@RequiresPermission(STANDARD.VIEW/CREATE/EDIT/DELETE)` per FR-028 + Constitution XVII
- [ ] T065 [US1] Create `platform-zatca/src/main/java/com/einvoice/zatca/client/ZatcaClearanceClient.java` — `POST {base_url}/invoices/clearance/single`; returns cleared XML + clearance status + ZATCA UUID
- [ ] T066 [US1] Wire `ZatcaAuthorityEngine.submit(...)` (T050) — when `transactionType = STANDARD`, route to `ZatcaClearanceClient`; map response into `SubmissionOutcome` and the per-class `clearance_status` field
- [ ] T067 [US1] Implement Standard branch of `ZatcaUblBuilder` (T049) — populate UBL `Invoice` with mandatory buyer block, Standard transaction-type code, Standard credit/debit references; verify against ZATCA-published UBL XSDs
- [ ] T068 [US1] Wire `ZatcaSubmissionOrchestrator` (T051) into a new `ZatcaSubmissionController.submitStandard(...)` endpoint in `platform-api/src/main/java/com/einvoice/api/zatca/submission/ZatcaSubmissionController.java` — endpoint path `/api/companies/{id}/zatca/standard/{docId}/submit`; sole entry to the orchestrator's Standard flow

### Frontend for User Story 1

- [ ] T069 [P] [US1] Create `frontend/src/app/standard/services/zatca-standard.service.ts` — Angular service for `/zatca/standard/*` endpoints; ETag/If-Match plumbing for `update()`
- [ ] T070 [P] [US1] Create `frontend/src/app/standard/zatca-standard-list.component.{ts,html,scss}` — list screen per FR-025 (status filter, date range, company filter, paged); columns per FR-025
- [ ] T071 [P] [US1] Create `frontend/src/app/standard/zatca-standard-form.component.{ts,html,scss}` — Reactive Form (Constitution XIV.1) with seller (pre-filled), required buyer, lines via `documents/shared/line-items-editor`, optimistic-concurrency `version` field; uses `conflict-resolution.dialog` on 409
- [ ] T072 [P] [US1] Create `frontend/src/app/standard/zatca-standard-detail.component.{ts,html,scss}` — renders header, lines, totals, chain snapshot (counter + previous-hash + this-hash), QR-PNG image preview, `clearanceStatus` chip, `documents/shared/submission-history`, `documents/shared/artifact-download`, and a **Create new draft from this document** action visible on `REJECTED` documents (FR-007a, Q1) that prompts for the new `invoiceNumber` and calls `POST /zatca/standard/{id}/clone-as-draft`
- [ ] T073 [P] [US1] Create `frontend/src/app/standard/standard-routing.module.ts` and register under `app.routes.ts`
- [ ] T074 [US1] Update `frontend/src/app/layout/sidebar/sidebar.component.ts` — add **Standard** entry under ZATCA sessions, gated by `STANDARD.VIEW` permission via `appHasPermission` (Constitution XIV.4, FR-028)
- [ ] T075 [P] [US1] Create `frontend/src/app/standard/zatca-standard.service.spec.ts` and `zatca-standard-form.component.spec.ts` — Karma + Jasmine coverage for service calls and form validators

**Checkpoint**: Standard B2B is fully functional end-to-end. MVP achievable. Run `quickstart.md` Phase D.

---

## Phase 4: User Story 2 — Create, sign, report, and persist a ZATCA Simplified (B2C) (Priority: P1)

**Goal**: A retail / B2C user creates a ZATCA Simplified document end-to-end, signs it, advances the same chain Standard uses (Constitution XII.2), transmits it through ZATCA reporting, and observes the reported status, chain snapshot, QR code, and ZATCA UUID.

**Independent Test**: Phase E of `quickstart.md` — create a Simplified document with no buyer block, submit, confirm `status = ACCEPTED`, `reportingStatus = REPORTED`, chain counter advanced again (now 2 if run after US1), QR PNG present, no CLEARED_XML produced.

### Tests for User Story 2

- [ ] T076 [P] [US2] Create `platform-zatca/src/test/java/com/einvoice/zatca/build/ZatcaUblBuilderSimplifiedGoldenFileTest.java` — golden-file UBL XML for `0200000` standard simplified, `381` credit, `383` debit; verifies no buyer block when omitted
- [ ] T077 [P] [US2] Create `platform-api/src/test/java/com/einvoice/api/zatca/simplified/ZatcaSimplifiedControllerContractTest.java` — every endpoint in `contracts/zatca-simplified-api.openapi.yaml`
- [ ] T078 [P] [US2] Create `platform-api/src/test/java/com/einvoice/api/zatca/simplified/ZatcaSimplifiedHappyPathIT.java` — Testcontainers + WireMock ZATCA Sandbox; create with empty buyer → submit → assert ACCEPTED, reportingStatus = REPORTED, no CLEARED_XML artifact emitted
- [ ] T079 [P] [US2] Create `platform-core/src/test/java/com/einvoice/core/repository/zatca/ZatcaSimplifiedSpecificationsTest.java`

### Implementation for User Story 2

- [ ] T080 [P] [US2] Create `platform-api/src/main/java/com/einvoice/api/zatca/simplified/service/ZatcaSimplifiedFormMapper.java`
- [ ] T081 [US2] Create `platform-api/src/main/java/com/einvoice/api/zatca/simplified/service/ZatcaSimplifiedService.java` — parallel of T063 with: optional buyer accepted, `02`-prefixed transaction-type code validation, original-document FK to `zatca_simplified_headers` (FR-030 + Q3 single-valued)
- [ ] T082 [US2] Create `platform-api/src/main/java/com/einvoice/api/zatca/simplified/ZatcaSimplifiedController.java` — every path in `zatca-simplified-api.openapi.yaml` except lifecycle (`/submit`, `/cancel`, `/retry`, `/check-status` — those land in US3)
- [ ] T083 [US2] Create `platform-zatca/src/main/java/com/einvoice/zatca/client/ZatcaReportingClient.java` — `POST {base_url}/invoices/reporting/single`; returns reporting status + ZATCA UUID; no cleared XML
- [ ] T084 [US2] Wire `ZatcaAuthorityEngine.submit(...)` — when `transactionType = SIMPLIFIED`, route to `ZatcaReportingClient`; map response into the per-class `reporting_status` field
- [ ] T085 [US2] Implement Simplified branch of `ZatcaUblBuilder` — same JAXB tree as Standard but with optional buyer, `02`-prefix transaction type, and Simplified credit/debit references

### Frontend for User Story 2

- [ ] T086 [P] [US2] Create `frontend/src/app/simplified/services/zatca-simplified.service.ts`
- [ ] T087 [P] [US2] Create `frontend/src/app/simplified/zatca-simplified-list.component.{ts,html,scss}` — per FR-026 (Reporting Status column, "Simplified" labelling throughout)
- [ ] T088 [P] [US2] Create `frontend/src/app/simplified/zatca-simplified-form.component.{ts,html,scss}` — Reactive Form; buyer section toggleable / optional
- [ ] T089 [P] [US2] Create `frontend/src/app/simplified/zatca-simplified-detail.component.{ts,html,scss}` — renders `reportingStatus` chip (vs Standard's `clearanceStatus`), reuses `documents/shared/` components, and exposes a **Create new draft from this document** action on `REJECTED` documents (FR-007a, Q1) that calls `POST /zatca/simplified/{id}/clone-as-draft`
- [ ] T090 [P] [US2] Create `frontend/src/app/simplified/simplified-routing.module.ts` and register
- [ ] T091 [US2] Update `frontend/src/app/layout/sidebar/sidebar.component.ts` — add **Simplified** entry gated by `SIMPLIFIED.VIEW`; FR-028 forbids reusing the STANDARD permission

**Checkpoint**: Standard and Simplified are both functional. Run `quickstart.md` Phases D + E.

---

## Phase 5: User Story 3 — Track submission history, retry, cancel, download artifacts (Priority: P2)

**Goal**: A user opens the detail page for a submitted document, reviews the submission timeline, downloads any artifact, retries an ambiguous submission, and cancels an accepted document — all without affecting Wave 7 (ETA) flows or breaking US1/US2 status.

**Independent Test**: For any document submitted in US1 or US2, the detail page shows the per-attempt timeline, every artifact downloads byte-for-byte, an ambiguous submission's retry produces a new attempt row without re-advancing the chain, and Cancel on an accepted document forwards verbatim to ZATCA and reflects ZATCA's response in `status`.

### Tests for User Story 3

- [ ] T092 [P] [US3] Create `platform-api/src/test/java/com/einvoice/api/zatca/submission/ZatcaRetryIdempotencyIT.java` — submit a Standard, force an AMBIGUOUS outcome (WireMock 504), retry, assert a new `submission_attempts` row appears, `zatca_chain_state.invoice_counter` is unchanged from after the first submit
- [ ] T093 [P] [US3] Create `platform-api/src/test/java/com/einvoice/api/zatca/submission/ZatcaCancelForwardingIT.java` — submit + accept a Standard, cancel with a reason, assert ZATCA call body carries reason verbatim, assert response status string is reflected in `clearance_status` and `status` transitions to `CANCELLED`
- [ ] T094 [P] [US3] Create `platform-api/src/test/java/com/einvoice/api/zatca/artifacts/ArtifactDownloadContractTest.java` — exercises `/zatca/{class}/{id}/artifacts/{type}` for each artifact type; asserts content-type and byte equality with the stored row

### Implementation for User Story 3

- [ ] T095 [US3] Extend `platform-api/src/main/java/com/einvoice/api/zatca/submission/ZatcaSubmissionController.java` (created in T068) with `/cancel`, `/retry`, `/check-status` (single) endpoints for both Standard and Simplified; routes through `ZatcaSubmissionOrchestrator`
- [ ] T096 [US3] Extend `ZatcaSubmissionOrchestrator.cancel(...)` — forwards verbatim per FR-019; no time-window check; transition `ACCEPTED → CANCELLED` on ZATCA success; record `submission_attempts` row with `result = SUCCESS` or `REJECTED`
- [ ] T097 [US3] Extend `ZatcaSubmissionOrchestrator.retry(...)` — opens a new `submission_attempts` row against the same document; does NOT call `ZatcaChainService.advance` (FR-017); document `status` transitions are AMBIGUOUS → re-resolve via authority response
- [ ] T098 [US3] Create `platform-zatca/src/main/java/com/einvoice/zatca/status/ZatcaStatusService.java` — single-document Check Status against ZATCA (paths: `GET {base_url}/invoices/reporting/single/{uuid}` for Simplified, ZATCA equivalent for Standard); maps response → `clearance_status` / `reporting_status` → `DocumentState` per data-model.md §Per-class authority status
- [ ] T099 [US3] Create `platform-api/src/main/java/com/einvoice/api/zatca/artifacts/ArtifactController.java` — `GET /zatca/standard/{id}/artifacts/{type}` and `/zatca/simplified/{id}/artifacts/{type}`; reads from `invoice_artifacts`; sets `Content-Type` per artifact_type; rejects with 404 for artifact types that don't apply (e.g. `CLEARED_XML` for Simplified)
- [ ] T100 [US3] Update Angular detail components (T072, T089) — wire Retry, Cancel-with-reason, Check Status buttons via `appHasPermission` (REFRESH/CANCEL); render submission timeline from `/submissions` endpoint via `documents/shared/submission-history`

**Checkpoint**: Full operational lifecycle works. Run `quickstart.md` Phases F (parts of), G (cancel), and partial H.

---

## Phase 6: User Story 4 — Authority-environment isolation (Priority: P2)

**Goal**: A document created in ZATCA Sandbox is 100% invisible from ZATCA Production for the same user and company, across list, search, direct-link access, and chain state.

**Independent Test**: Phase F of `quickstart.md` — Sandbox document does not appear in Production list; direct-link GET returns 404 indistinguishable from non-existent; chain counter is independent per environment.

### Tests for User Story 4

- [ ] T101 [P] [US4] Create `platform-api/src/test/java/com/einvoice/api/zatca/isolation/ZatcaStandardEnvironmentIsolationIT.java` — same company, two JWTs (Sandbox, Production); create doc in Sandbox; assert Production list returns 0 (or unaffected count); assert direct-link GET in Production returns 404; assert `zatca_chain_state` has separate rows for env 3 and env 5
- [ ] T102 [P] [US4] Create `platform-api/src/test/java/com/einvoice/api/zatca/isolation/ZatcaSimplifiedEnvironmentIsolationIT.java` — parallel of T101
- [ ] T103 [P] [US4] Create `platform-api/src/test/java/com/einvoice/api/zatca/isolation/ZatcaCrossClassIsolationIT.java` — assert a `STANDARD` document is invisible from `/zatca/simplified` endpoints and vice versa; covers FR-028 endpoint-level gating

### Implementation for User Story 4

User Story 4 is structurally enforced by Foundational-phase work (T027, T028 Specifications) and by every controller's permission check (T064, T068, T082, T095, T099). No additional code is required to satisfy the FRs; the tests above are the deliverable. Should any test fail, the fix lands in the appropriate Specification or controller.

**Checkpoint**: Environment isolation verified.

---

## Phase 7: User Story 5 — Sequential, conflict-free chain under concurrent submissions (Priority: P2)

**Goal**: Two concurrent submissions for the same company/environment produce consecutive counters with correct previous-hash linkage; zero broken-chain outcomes across 100 iterations. The chain-busy timeout (Q2, FR-009a) is also exercised here.

**Independent Test**: Phase G + Phase I of `quickstart.md`. The `ZatcaChainIntegrityIT` test class is the canonical regression.

### Tests for User Story 5

- [ ] T104 [P] [US5] Create `platform-zatca/src/test/java/com/einvoice/zatca/chain/ZatcaChainIntegrityIT.java` — 100 iterations of two-thread parallel submission against Testcontainers Postgres; asserts (a) consecutive counters, (b) previous-hash linkage, (c) chain state reflects latest, (d) no document is dropped/duplicated (research §14, SC-005)
- [ ] T105 [P] [US5] Create `platform-zatca/src/test/java/com/einvoice/zatca/chain/ZatcaChainBusyTimeoutIT.java` — hold a manual `SELECT FOR UPDATE` in one transaction; submit from another; assert (a) request returns 503 `CHAIN_BUSY` within ~30 s, (b) document remains in `DRAFT`, (c) no `submission_attempts` row inserted, (d) `zatca_chain_state.invoice_counter` unchanged (FR-009a, Q2)
- [ ] T106 [P] [US5] Create `platform-zatca/src/test/java/com/einvoice/zatca/chain/ZatcaChainAdvanceOnRejectionIT.java` — submit a document crafted to be rejected by WireMock'd ZATCA; assert (a) `status = REJECTED`, (b) `zatca_chain_state.invoice_counter` advanced by exactly one, (c) next successful submission's `previous_invoice_hash` equals the rejected document's `invoice_hash` (FR-010, SC-010, Constitution XII.5)

### Implementation for User Story 5

Implementation work for US5 was completed in Phase 2 (T043 `ZatcaChainService` with `SET LOCAL lock_timeout = '30s'`) and Phase 3 (T051 `ZatcaSubmissionOrchestrator` placing the chain advance + `submission_attempts` insert inside the same transaction as the chain-row update, before the outbound call). The tests above are the deliverable. Any failure of T104 is a Sev-1 chain-integrity defect.

**Checkpoint**: Chain integrity verified under concurrency. Run `quickstart.md` Phases G, H, I.

---

## Phase 8: User Story 6 — Immutable artifacts and audit trail (Priority: P3)

**Goal**: Stored signed XML, QR PNG, and ZATCA responses are byte-for-byte stable from submission onward; every state-changing action emits an audit-log row; no user role (including Super User) can mutate or delete artifacts or audit rows.

**Independent Test**: Direct DB `UPDATE` against `invoice_artifacts` / `audit_logs` for a ZATCA row raises the Wave-7 trigger (`APPEND_ONLY_VIOLATION`); every API state-change is reflected in a matching audit row.

### Tests for User Story 6

- [ ] T107 [P] [US6] Create `platform-api/src/test/java/com/einvoice/api/zatca/audit/ZatcaAuditEmissionIT.java` — exercises every state-changing endpoint (CREATE, UPDATE, DELETE, SUBMIT, CANCEL, RETRY, CHECK_STATUS, CLONE_AS_DRAFT) for both Standard and Simplified; asserts each emits exactly one `audit_logs` row carrying `entity_type ∈ {ZATCA_STANDARD, ZATCA_SIMPLIFIED}`, correct `action`, correct `company_id` + `authority_environment_id`, and well-formed `payload_before` / `payload_after`
- [ ] T108 [P] [US6] Create `platform-core/src/test/java/com/einvoice/core/repository/ImmutabilityTriggerZatcaIT.java` — attempt direct UPDATE and DELETE against `invoice_artifacts` and `audit_logs` rows whose `transaction_type ∈ {STANDARD, SIMPLIFIED}`; assert the Wave-7 `enforce_append_only` trigger fires and the operation is rejected at the DB layer

### Implementation for User Story 6

Implementation was completed in Phase 2 (T052 `AuditService` extension for `ZATCA_STANDARD` / `ZATCA_SIMPLIFIED` entity types) and is reused unchanged from Wave 7 (`enforce_append_only` trigger, append-only repository discipline). Every Wave-8 state-changing operation in the orchestrator and services already calls `AuditService.recordAction(...)`. The tests above are the deliverable.

**Checkpoint**: Auditability and append-only guarantees verified.

---

## Phase 9: Polish & Cross-Cutting Concerns

**Purpose**: Wave-8-specific cross-cutting features (bulk Check Status with streaming) and final integration validation.

### Bulk Check Status (FR-018a, Q5 — uncapped + NDJSON + cancel)

- [ ] T109 Create `platform-api/src/main/java/com/einvoice/api/zatca/submission/service/BulkCheckStatusRunRegistry.java` — in-memory registry of run-ids and their cancel flags; gc'd after completion
- [ ] T110 Create `platform-api/src/main/java/com/einvoice/api/zatca/submission/service/BulkCheckStatusService.java` — accepts `documentIds[]` and `transactionType`; iterates with server-side rate pacing (default 200 calls/min); polls the run registry between calls; streams NDJSON outcomes to the response body via `StreamingResponseBody`; emits `CANCELLED_NO_OP` for not-yet-processed documents on cancel
- [ ] T111 Add bulk endpoints `POST /zatca/standard/check-status` and `POST /zatca/simplified/check-status` to the existing controllers; both delegate to `BulkCheckStatusService`; response is `application/x-ndjson` with `Run-Id` header
- [ ] T112 Add `DELETE /api/runs/{runId}` endpoint in a new `platform-api/src/main/java/com/einvoice/api/runs/RunController.java`; sets the cancel flag on the registry entry
- [ ] T113 Update `frontend/src/app/documents/shared/bulk-status-check.dialog.ts` (T011) to consume the NDJSON stream via `fetch` + ReadableStream; render per-document outcomes as they arrive; Cancel button calls the `DELETE /api/runs/{runId}` endpoint
- [ ] T114 [P] Create `platform-api/src/test/java/com/einvoice/api/zatca/submission/BulkCheckStatusIT.java` — submit 250 docs in `IN_REVIEW`; bulk check; assert NDJSON stream returns 250 outcomes; cancel mid-run; assert subsequent outcomes are `CANCELLED_NO_OP` (FR-018a, Q5)

### Performance verification (quickstart Phase L)

- [ ] T115 [P] Run quickstart Phase L on a seeded DB (100k Standard documents across 10 companies); record observed p95 for list and filter-narrow; commit results under `specs/009-zatca-docs-submission/perf-results.md`
- [ ] T116 [P] EXPLAIN ANALYSE on the four list-query patterns from quickstart Phase L; verify index scans on the V56 compound indexes; if seq scans appear, file as bugs and fix index definitions in V56 (or add V56a)

### Documentation

- [ ] T117 [P] Append a Wave-8 schema-change note to `Docs/deployment-guide.md`; update `upgrade.sh` reference to mention V54–V56
- [ ] T118 [P] Append a Wave-8 entry to `Docs/configuration-reference.md` covering `zatca_configs.base_url` consumption and the chain-busy timeout (`lock_timeout = 30s`) tunable
- [ ] T119 [P] Update `CLAUDE.md` recent-changes block: "009-zatca-docs-submission: Wave 8 adds ZATCA Standard/Simplified document tables (V54–V56), ZATCA chain integrity with pessimistic acquisition (~30 s bounded wait), XAdES-BES signing, SHA-256 hash chain, QR TLV Phase-2 9 tags, 7-state lifecycle reused from Wave 7, uncapped bulk Check Status with NDJSON streaming + cancellation, optimistic-concurrency drafts, per-class authority status (clearance for Standard, reporting for Simplified)."

### Final integration

- [ ] T120 Run every quickstart.md phase A–L end-to-end against a fresh DB; record outcomes; any failure blocks wave acceptance
- [ ] T121 Run `mvn clean verify` from repo root; confirm all unit + integration tests pass (Constitution XXIV)
- [ ] T122 Run `ng test` from `frontend/`; confirm all Karma + Jasmine specs pass
- [ ] T123 Run `docker compose up` from repo root; smoke-test the full stack manually against Phase D + Phase E happy paths

---

## Dependencies & Execution Order

### Phase dependencies

- **Setup (Phase 1)** — no dependencies; can start immediately
- **Foundational (Phase 2)** — depends on Phase 1; blocks every user story phase. Within Phase 2 the dependency edges are:
  - T013–T015 (Flyway) before T016–T026 (entities depending on tables)
  - T004–T006 (enum rename) before T016–T019, T037–T039
  - T040 (AuthorityEngine SPI) before T050 (`ZatcaAuthorityEngine`), T051 (`ZatcaSubmissionOrchestrator`)
  - T043 (`ZatcaChainService`) before T051
  - T044–T049 (engine internals) before T050, T051
  - T036 (GlobalExceptionHandler) after T030–T035
  - T037 (LifecycleTransitions full matrix) after T005 + T016–T019 (the typed transitions reference the new entities)
- **User stories (Phases 3–8)** — all depend on Phase 2 completion; can run in parallel by feature team if staffed:
  - US1 (Standard) and US2 (Simplified) are symmetric P1 work; can run in parallel since they touch different files past Phase 2
  - US3 (history/retry/cancel/download) depends on at least one of US1 or US2 having shipped (the controllers it extends are defined there)
  - US4 (env isolation) depends on US1 or US2 (the tests target endpoints from those phases)
  - US5 (chain integrity) depends on US1 (the `ZatcaSubmissionController.submitStandard` endpoint is the test target)
  - US6 (audit) depends on at least one of US1 or US2 (entity types must exist)
- **Polish (Phase 9)** — depends on all desired user stories being complete

### Within each user story

- Tests (golden-file, contract, integration) are written first and asserted to fail before implementation lands (Constitution XXIV; spec FR-024 acceptance criterion).
- Models / repositories live in Phase 2 — within US1/US2 the work is service + controller + frontend.
- Backend before frontend per task ID order; in practice both can proceed in parallel once the contract YAML is locked.
- Story complete = all listed tests pass + quickstart phase for that story passes.

### Parallel opportunities

- **Phase 1**: T002 and T003 in parallel.
- **Phase 2**:
  - T007–T011 (frontend file moves) in parallel
  - T013–T015 (Flyway migrations) in parallel
  - T016–T028 (entities, repositories, specifications) largely in parallel after T013–T015
  - T029–T035 (utility + exception classes) in parallel
  - T044–T049 (engine internals) in parallel after T043
- **Phase 3 (US1)**: T057–T061 (tests) in parallel; T062, T069–T073, T075 in parallel; T063 → T064 → T068 (sequential within service+controller+orchestrator wiring)
- **Phase 4 (US2)**: T076–T079 in parallel; T080, T086–T090 in parallel; T081 → T082 → T084
- **Phases 3 and 4 in parallel by different developers** once Phase 2 is done — this is the recommended staffing
- **Phases 6–8 tests** can run in parallel once their target endpoints exist
- **Polish (Phase 9)**: T115–T119 in parallel

---

## Parallel Example: User Story 1 (Standard)

```bash
# After Phase 2 checkpoint, launch in parallel:
Task: "T057 [P] [US1] Golden-file UBL test for Standard variants in platform-zatca/src/test/java/.../ZatcaUblBuilderStandardGoldenFileTest.java"
Task: "T058 [P] [US1] Golden-file TLV test for QR 9-tag in platform-zatca/src/test/java/.../ZatcaQrTlvGoldenFileTest.java"
Task: "T059 [P] [US1] Contract test for ZatcaStandardController in platform-api/src/test/java/.../ZatcaStandardControllerContractTest.java"
Task: "T060 [P] [US1] Happy-path IT in platform-api/src/test/java/.../ZatcaStandardHappyPathIT.java"
Task: "T061 [P] [US1] Specifications test in platform-core/src/test/java/.../ZatcaStandardSpecificationsTest.java"

# Then in parallel:
Task: "T062 [P] [US1] ZatcaStandardFormMapper in platform-api/src/main/java/.../ZatcaStandardFormMapper.java"
Task: "T069 [P] [US1] Angular service in frontend/src/app/standard/services/zatca-standard.service.ts"
Task: "T070 [P] [US1] List component in frontend/src/app/standard/zatca-standard-list.component.{ts,html,scss}"
Task: "T071 [P] [US1] Form component in frontend/src/app/standard/zatca-standard-form.component.{ts,html,scss}"
Task: "T072 [P] [US1] Detail component in frontend/src/app/standard/zatca-standard-detail.component.{ts,html,scss}"
Task: "T073 [P] [US1] Routing module in frontend/src/app/standard/standard-routing.module.ts"
Task: "T075 [P] [US1] Karma specs in frontend/src/app/standard/*.spec.ts"

# Then sequentially:
Task: "T063 [US1] ZatcaStandardService"
Task: "T064 [US1] ZatcaStandardController"
Task: "T065 [US1] ZatcaClearanceClient"
Task: "T066 [US1] Wire engine.submit for Standard"
Task: "T067 [US1] Standard branch of ZatcaUblBuilder"
Task: "T068 [US1] Wire SubmissionController.submitStandard"
Task: "T074 [US1] Sidebar update"
```

---

## Implementation Strategy

### MVP First (User Story 1 only)

1. Complete Phase 1 (Setup) — confirm dependencies and module wiring.
2. Complete Phase 2 (Foundational) — schema, shared domain, engine internals, orchestrator skeleton. This is the heaviest phase by tasks but is shared by every story.
3. Complete Phase 3 (US1 — Standard B2B).
4. **STOP and VALIDATE** — run quickstart Phase D end-to-end. MVP demonstrable: a Saudi B2B taxpayer can clear an invoice through ZATCA Sandbox.
5. Deploy/demo if ready.

### Incremental delivery

1. Foundation (Phases 1 + 2) → ready for any story.
2. + US1 (Standard) → MVP → demo.
3. + US2 (Simplified) → second authority class → demo.
4. + US3 (history/retry/cancel/download) → operational completeness → demo.
5. + US4, US5, US6 (env isolation, chain integrity, audit) — these are mostly tests; ship together as the compliance/regression cohort.
6. + Polish (Phase 9: bulk Check Status, perf, docs) → wave-ready.

### Parallel team strategy

Once Phase 2 is done:

- **Developer A** — US1 (Standard) Phases 3 + later wires US3 cancel/retry for Standard
- **Developer B** — US2 (Simplified) Phase 4 + later wires US3 cancel/retry for Simplified
- **Developer C** — US4 + US5 + US6 tests (mostly integration test authoring; sits in `platform-api` and `platform-zatca` test trees)
- **Developer D** — Phase 9 (bulk Check Status, NDJSON streaming, frontend dialog rework, docs)

US1 and US2 are designed to be independently shippable: each can demo with the other absent. US3 lights up the same controllers for both classes.

---

## Notes

- **Tests are mandatory**, not optional, per Constitution XXIV. Golden-file, contract, integration, and concurrency tests are listed inline within each story.
- `[P]` tasks operate on different files and have no incomplete prerequisite — they can safely run in parallel by separate agents or developers.
- File paths in this document are exact; the executing agent should create the listed files at the listed paths.
- Every state-changing task in US1 + US2 + US3 must call `AuditService.recordAction(...)` exactly once per state change; T107 verifies this end-to-end.
- The chain-integrity concurrency test (T104) is the load-bearing regression for this wave. Treat any flake as a Sev-1 chain defect and fix it at the root.
- Commit after each task or logical group; preserve the wave-by-wave history per Constitution XXIII.2.
