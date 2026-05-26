---
description: "Task list for Wave 7 — ETA Document Tables and Submission Engine"
---

# Tasks: Wave 7 — ETA Document Tables and Submission Engine

**Input**: Design documents from `/specs/008-eta-docs-submission/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/, quickstart.md

**Tests**: REQUIRED. Constitution XXIV mandates **golden-file tests** for every authority engine payload generator (XXIV.1), unit coverage for every lifecycle transition (XXIV.3), contract tests for the new REST surface (XXIV.4 spirit), integration tests for tenant + authority-environment isolation (XXIV.5–6), and security tests for the append-only operational tables (Constitution IX.3, XXI.4). Test tasks are interleaved per story.

**Organization**: Tasks are grouped by user story to enable independent implementation and testing. All file paths are repo-relative from `D:\Cou\Spring Course In 28 Minutes\Projects\einvoice-platform\`.

## Path Conventions (Wave 7 actual layout — multi-module Maven + Angular)

- Backend modules at repo root: `platform-core/`, `platform-eta/` (newly populated), `platform-api/`. Other modules untouched.
- Frontend at `frontend/src/app/`.
- Flyway migrations at `platform-core/src/main/resources/db/migration/`.
- Test sources at `<module>/src/test/java/...` (backend), `frontend/src/app/.../*.spec.ts` (frontend).
- Contracts at `specs/008-eta-docs-submission/contracts/`.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Confirm baseline state and pre-stage documentation before Wave 7 implementation begins. Branch `008-eta-docs-submission` already exists with the Wave 6 commit as its parent.

- [X] T001 Confirm working tree clean and on branch `008-eta-docs-submission`; verify `mvn -q -DskipTests verify` and `cd frontend && npm ci && npm run build` succeed against the current Wave-6 baseline (captures pre-Wave-7 green state for rollback comparison).
- [X] T002 [P] Update `Docs/configuration-reference.md` with a Wave-7 section listing the new tables (`eta_invoice_headers`, `eta_invoice_lines`, `eta_invoice_line_taxes`, `eta_receipt_headers`, `eta_receipt_lines`, `eta_receipt_line_taxes`, `submission_attempts`, `invoice_artifacts`, `audit_logs`), the new permission scopes (`INVOICE`, `RECEIPT`) with all 8 actions per Constitution XVII.6, and the new ETA HTTP base URLs per `authority_environment_id`.
- [X] T003 [P] Update `Docs/deployment-guide.md` with Wave-7 notes: V48–V53 migrations apply on top of V47; verify the V52 trigger `append_only_guard` is created on `invoice_artifacts` and `audit_logs`; outbound HTTPS access from the app container to ETA Pre-Production and Production endpoints must be reachable.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Schema, base entities, lifecycle/state-machine support, shared operational tables, permission seeds, and the append-only enforcement layer that every user story depends on. **No user story work may begin until this phase is complete.**

### 2A — Flyway migrations (V48–V53, sequential by version)

- [ ] T004 Create `platform-core/src/main/resources/db/migration/V48__eta_invoice_headers.sql` per data-model.md §1: table `eta_invoice_headers` with `version INTEGER NOT NULL DEFAULT 0` (FR-025), CHECK constraints on `documentType`, `currency`, and original-document-presence-for-credit/debit notes (FR-023), unique constraint `uq_eta_invoice_number (company_id, authority_environment_id, invoice_number)` (FR-006).
- [ ] T005 Create `platform-core/src/main/resources/db/migration/V49__eta_invoice_lines_and_taxes.sql` per data-model.md §2 and §3: `eta_invoice_lines` with `uq_eta_invoice_line (header_id, line_number)`, `CHECK (item_type IN ('GS1','EGS'))`, `CHECK (quantity > 0)`, and `eta_invoice_line_taxes`. Both with `ON DELETE CASCADE` from the header.
- [ ] T006 Create `platform-core/src/main/resources/db/migration/V50__eta_receipt_headers.sql` per data-model.md §4: same shape as `eta_invoice_headers` with `receipt_number`, nullable `buyer_data`, `pos_serial`, `payment_method`, `original_receipt_id`, `version` column, `uq_eta_receipt_number`.
- [ ] T007 Create `platform-core/src/main/resources/db/migration/V51__eta_receipt_lines_and_taxes.sql` per data-model.md §5–6: same shape as V49 referencing `eta_receipt_headers.id`.
- [ ] T008 Create `platform-core/src/main/resources/db/migration/V52__shared_operational_tables.sql` per data-model.md §7–9: tables `submission_attempts`, `invoice_artifacts`, `audit_logs` with their constraints and the `BEFORE UPDATE OR DELETE` trigger `append_only_guard` on `invoice_artifacts` and `audit_logs` that raises `EXCEPTION 'append_only_table'` (research Decision 3). The trigger on `submission_attempts` allows UPDATE only on the columns `result, status_code, error_summary, response_payload_ref, completed_at` (the result-finalisation pathway from research Decision 4); any UPDATE that touches other columns or any DELETE is rejected.
- [ ] T009 Create `platform-core/src/main/resources/db/migration/V53__wave7_compound_indexes.sql` per data-model.md §1, §4, §7–9: `idx_eta_inv_ctx_status`, `idx_eta_inv_ctx_date`, `idx_eta_inv_number`, `idx_eta_inv_lines_header`, `idx_eta_inv_taxes_line`, `idx_eta_rec_ctx_status`, `idx_eta_rec_ctx_date`, `idx_eta_rec_number`, `idx_eta_rec_lines_header`, `idx_eta_rec_taxes_line`, `idx_submission_doc`, `idx_submission_ctx_completed`, `idx_artifacts_doc`, `idx_artifacts_ctx_type`, `idx_audit_company_env`, `idx_audit_entity`. Constitution XXV.2 compound-index requirement.
- [ ] T010 Apply V48–V53 against a clean local DB. Confirm via `psql`: all 8 tables exist, 16 indexes are present, the append-only trigger fires on a manual `UPDATE invoice_artifacts SET content='x' WHERE id IN (...)` and `DELETE FROM audit_logs WHERE id IN (...)` (quickstart Phase J). Also confirm that an `UPDATE submission_attempts SET result='SUCCESS' WHERE result IS NULL` succeeds while `UPDATE submission_attempts SET document_id=gen_random_uuid() WHERE id IN (...)` fails.

### 2B — Domain enums + lifecycle state machine

- [ ] T011 [P] Create `platform-core/src/main/java/com/einvoice/core/domain/shared/TransactionType.java` (enum `INVOICE, RECEIPT, STANDARD, SIMPLIFIED` per research Decision 11).
- [ ] T012 [P] Create `platform-core/src/main/java/com/einvoice/core/domain/shared/SubmissionResult.java` (enum `SUCCESS, REJECTED, ERROR, TIMEOUT, AMBIGUOUS`).
- [ ] T013 [P] Create `platform-core/src/main/java/com/einvoice/core/domain/shared/ArtifactType.java` (enum `SIGNED_JSON, SIGNED_XML, QR_CODE, CLEARED_XML, ETA_RESPONSE, ZATCA_RESPONSE`).
- [ ] T014 [P] Create `platform-core/src/main/java/com/einvoice/core/domain/eta/lifecycle/EtaInvoiceState.java` (enum DRAFT, SUBMITTING, IN_REVIEW, VALID, REJECTED, SUBMISSION_AMBIGUOUS, CANCELLED per research Decision 1).
- [ ] T015 [P] Create `platform-core/src/main/java/com/einvoice/core/domain/eta/lifecycle/EtaReceiptState.java` (same shape).
- [ ] T016 [P] Create `platform-core/src/main/java/com/einvoice/core/domain/eta/document/EtaInvoiceDocumentType.java` (enum `i, c, d, ei, ec, ed`; methods `requiresOriginalDocument()` returning `true` for `c, d, ec, ed`).
- [ ] T017 [P] Create `platform-core/src/main/java/com/einvoice/core/domain/eta/document/EtaReceiptDocumentType.java` (enum of the 23 v1.2 receipt subtype codes; `requiresOriginalDocument()` for return/cancellation subtypes per data-model.md §4).
- [ ] T018 Create `platform-core/src/main/java/com/einvoice/core/domain/eta/lifecycle/LifecycleAction.java` (enum `EDIT, DELETE, SUBMIT, CANCEL, RETRY, CHECK_STATUS, CLONE_TO_NEW_DRAFT, MARK_VALID, MARK_REJECTED, MARK_IN_REVIEW, MARK_AMBIGUOUS`) and `platform-core/src/main/java/com/einvoice/core/lifecycle/EtaInvoiceLifecycle.java` exposing `boolean allowed(EtaInvoiceState from, LifecycleAction action)` and `EtaInvoiceState next(EtaInvoiceState from, LifecycleAction action)` encoding the matrix in research Decision 1. Throws `InvalidLifecycleTransitionException` on disallowed combinations.
- [ ] T019 Create `platform-core/src/main/java/com/einvoice/core/lifecycle/EtaReceiptLifecycle.java` (same matrix shape against `EtaReceiptState`).
- [ ] T020 [P] Create `platform-core/src/test/java/com/einvoice/core/lifecycle/EtaInvoiceLifecycleTest.java` — parameterised unit test asserting **every (state, action) cell** in the matrix matches research Decision 1. Explicitly assert `REJECTED → ∅` (no `allowed(REJECTED, *)` returns true except `CLONE_TO_NEW_DRAFT` which is a creation action — covered in the orchestrator, not the matrix), and `CANCELLED → ∅`.
- [ ] T021 [P] Create `platform-core/src/test/java/com/einvoice/core/lifecycle/EtaReceiptLifecycleTest.java` (same).

### 2C — Money math (5-decimal precision per Constitution XIII.5)

- [ ] T022 [P] Create `platform-core/src/main/java/com/einvoice/core/money/EtaMoneyMath.java` with `round5(BigDecimal)`, `sum5(BigDecimal...)`, `reconcileLineTotals(List<EtaInvoiceLine>)`, `reconcileHeaderTotals(EtaInvoiceHeader)`. All use `RoundingMode.HALF_UP` and `MathContext` with 5 fractional digits. Throws `TotalsInconsistentException` from the reconciliation helpers.
- [ ] T023 [P] Create `platform-core/src/test/java/com/einvoice/core/money/EtaMoneyMathTest.java` — covers (a) ETA-spec rounding examples (5-decimal half-up), (b) line-total reconciliation across a 5-line invoice with mixed VAT and WHT taxes, (c) header-total reconciliation including extra-discount handling.

### 2D — Domain entities for shared operational tables

- [ ] T024 [P] Create `platform-core/src/main/java/com/einvoice/core/domain/shared/SubmissionAttempt.java` JPA entity per data-model.md §7. Mapped read-only except for the finalisation pathway (no JPA setters on `companyId`, `authorityEnvironmentId`, `transactionType`, `documentId`, `attemptNumber`, `submittedAt`).
- [ ] T025 [P] Create `platform-core/src/main/java/com/einvoice/core/domain/shared/InvoiceArtifact.java` JPA entity per data-model.md §8. All fields immutable after construction (no setters at all; constructor-only population).
- [ ] T026 [P] Create `platform-core/src/main/java/com/einvoice/core/domain/shared/AuditLog.java` JPA entity per data-model.md §9. Same immutability shape as `InvoiceArtifact`.

### 2E — Repositories for shared operational tables (append-only marker interface)

- [ ] T027 Create `platform-core/src/main/java/com/einvoice/core/repository/shared/WriteOnlyRepository.java` — marker interface declaring `save(T)`, `findById(UUID)`, `findAll(Specification<T>, Pageable)`; explicitly **does not extend** `JpaRepository` (which would expose `delete*` and `*update*` methods). Generic over entity type and ID type.
- [ ] T028 [P] Create `platform-core/src/main/java/com/einvoice/core/repository/shared/SubmissionAttemptRepository.java` extending `WriteOnlyRepository<SubmissionAttempt, UUID>` plus a method `Optional<Integer> findMaxAttemptNumber(UUID documentId)` and a single `@Modifying @Query` method `finalizeAttempt(UUID id, SubmissionResult result, Integer statusCode, String errorSummary, String responsePayloadRef, OffsetDateTime completedAt)` — the **only** UPDATE permitted on this table, matching the V52 trigger's allowlist (research Decision 4).
- [ ] T029 [P] Create `platform-core/src/main/java/com/einvoice/core/repository/shared/InvoiceArtifactRepository.java` extending `WriteOnlyRepository<InvoiceArtifact, UUID>` — no `@Modifying` methods at all.
- [ ] T030 [P] Create `platform-core/src/main/java/com/einvoice/core/repository/shared/AuditLogRepository.java` extending `WriteOnlyRepository<AuditLog, Long>` — no `@Modifying` methods at all.
- [ ] T031 Create `platform-core/src/test/java/com/einvoice/core/repository/shared/AppendOnlyRepositoryEnforcementTest.java` — reflective test that **fails the build** if any class in `com.einvoice.core.repository.shared` declares a method whose name matches `delete*` or `update*` (other than `SubmissionAttemptRepository.finalizeAttempt`, explicitly allowlisted by name), or any `@Modifying @Query` annotation present on those repositories (other than the allowlisted one). Closes the application-layer defence-in-depth for research Decision 3.

### 2F — Common error types + exception handler updates

- [ ] T032 [P] Create exception classes under `platform-core/src/main/java/com/einvoice/core/error/`: `DuplicateInvoiceNumberException`, `DuplicateReceiptNumberException`, `MissingOriginalDocumentException`, `IncompatibleOriginalDocumentException`, `TotalsInconsistentException`, `NoCertificateConfiguredException`, `DocumentNotDraftException`, `InvalidLifecycleTransitionException`, `OptimisticLockConflictException`, `AppendOnlyViolationException`, `BulkBatchLimitExceededException` — each carrying the relevant context data (document id, field details, etc.) as final fields.
- [ ] T033 Update `platform-api/src/main/java/com/einvoice/api/error/GlobalExceptionHandler.java` to map the 11 new exceptions to the error codes in `contracts/error-codes.md`. `OptimisticLockConflictException` returns a structured `ConflictBody` per the OpenAPI contract (with `expectedVersion`, `actualVersion`, `current` populated). Other exceptions follow the standard `{ code, message, details? }` shape. Map Hibernate's `ObjectOptimisticLockingFailureException` (and `OptimisticLockException`) to `OptimisticLockConflictException` so JPA `@Version` failures are converted at the controller advice layer.
- [ ] T034 [P] Create `platform-api/src/test/java/com/einvoice/api/error/GlobalExceptionHandlerWave7Test.java` — assertions that each new exception is mapped to the expected `code`, HTTP status, and body shape from `contracts/error-codes.md`.

### 2G — Permission seed (INVOICE + RECEIPT modules)

- [ ] T035 Create `platform-core/src/main/resources/db/migration/V53a__wave7_invoice_receipt_permissions_seed.sql` that inserts rows into `transaction_role_permissions` to bind: **COMPANY_ADMIN** → all 8 actions (VIEW, CREATE, EDIT, DELETE, CANCEL, TRANSFER, REFRESH, SUBMIT) on transaction types `INVOICE` and `RECEIPT`; **ACCOUNTANT** → VIEW, CREATE, SUBMIT, REFRESH on `INVOICE` and `RECEIPT`; **VIEWER** → VIEW on `INVOICE` and `RECEIPT`. Per Constitution XVII.6–8 and data-model.md "Lifecycle-action ↔ permission mapping". Idempotent (`INSERT … ON CONFLICT DO NOTHING`). **Note**: `TRANSFER` is seeded for forward-compat with Wave 9 (inter-branch transfers); no Wave-7 endpoint consumes it. This is consistent with Constitution XVII.6's "8 defined action permissions" and is documented here so a future audit does not flag it as dead config.
- [ ] T036 Update `platform-security` Wave-5 `SessionContextAssembler` (no code change unless required) to confirm the assembled `permissions` object for an `ACCOUNTANT` under ETA Pre-Production now includes both `INVOICE.{VIEW,CREATE,SUBMIT,REFRESH}` and `RECEIPT.{VIEW,CREATE,SUBMIT,REFRESH}`. Add `platform-security/src/test/java/com/einvoice/security/session/Wave7PermissionsAssemblyTest.java` to assert this.

### 2H — AuthorityEngine SPI (refactor scaffold per Constitution XVI)

- [ ] T037 Create `platform-core/src/main/java/com/einvoice/core/authority/AuthorityEngine.java` — interface declaring `serialize(DocumentInput input) → SerializedPayload`, `sign(SerializedPayload payload, CertificateMaterial cert) → SignedPayload`, `submit(SignedPayload signed, AuthorityCredentials creds) → AuthorityResponse`, `cancel(CancelInput input) → AuthorityResponse`, `checkStatus(StatusInput input) → AuthorityResponse`. Wave 8 will add a second implementation (`ZatcaAuthorityEngine`).
- [ ] T038 [P] Create the supporting value records in the same package: `DocumentInput`, `SerializedPayload`, `CertificateMaterial`, `SignedPayload`, `AuthorityCredentials`, `AuthorityResponse`, `CancelInput`, `StatusInput`. Records, immutable, no business logic.

### 2I — Frontend state-machine codegen (must precede US1/US2 frontend forms)

- [ ] T038a [P] Wire `platform-core/pom.xml` to run a small `exec-maven-plugin` invocation that exports `EtaInvoiceState`, `EtaReceiptState`, and the `LifecycleTransitions` matrix to TypeScript constants at `frontend/src/app/invoices/shared/generated/eta-states.ts`. The generation runs in the `process-classes` phase of `platform-core` (after T014/T015/T018/T019 produce the Java sources). Output file has a `// THIS FILE IS GENERATED — DO NOT EDIT` header. Add `frontend/src/app/invoices/shared/generated/eta-states.spec.ts` asserting round-trip values match expected enum members. **Ordering rationale (analysis remediation U1)**: this task originally sat in Polish (T116) but the US1/US2 forms (T068, T090) need the generated matrix from day one to gate Submit/Edit/Delete/Cancel/Retry buttons by state without hand-mirroring the Java enum in TypeScript.

**Checkpoint**: Foundation ready — user story implementation can now begin in parallel.

---

## Phase 3: User Story 1 — Create, sign, and submit an ETA tax invoice (Priority: P1) 🎯 MVP

**Goal**: A `COMPANY_ADMIN` user on a company under ETA Pre-Production can create a new standard invoice end-to-end, submit it, and see a non-draft state with `etaUuid` populated on the document.

**Independent Test**: Quickstart phases C and D pass without any receipts code, without any retry/cancel logic, without bulk check-status, and without the conflict-resolution dialog.

### 3A — Backend entities + repositories

- [ ] T039 [P] [US1] Create `platform-core/src/main/java/com/einvoice/core/domain/eta/EtaInvoiceHeader.java` per data-model.md §1: JPA `@Entity`, `@Version` column for FR-025 (research Decision 2), all fields per the schema. `state` mapped as `@Enumerated(EnumType.STRING)` to `EtaInvoiceState`. JSONB columns (`sellerData`, `buyerData`, `paymentData`, `deliveryData`) via `@JdbcTypeCode(SqlTypes.JSON)` deserialising to `Map<String,Object>`. `lines` mapped `@OneToMany(cascade=ALL, orphanRemoval=true)`.
- [ ] T040 [P] [US1] Create `platform-core/src/main/java/com/einvoice/core/domain/eta/EtaInvoiceLine.java` per data-model.md §2 with `taxes` mapped `@OneToMany(cascade=ALL, orphanRemoval=true)`.
- [ ] T041 [P] [US1] Create `platform-core/src/main/java/com/einvoice/core/domain/eta/EtaInvoiceLineTax.java` per data-model.md §3.
- [ ] T042 [US1] Create `platform-core/src/main/java/com/einvoice/core/repository/eta/EtaInvoiceHeaderRepository.java` extending `JpaRepository<EtaInvoiceHeader, UUID>` and `JpaSpecificationExecutor<EtaInvoiceHeader>`; adds `Optional<EtaInvoiceHeader> findByCompanyIdAndAuthorityEnvironmentIdAndInvoiceNumber(UUID, Short, String)` for duplicate-detection lookup.
- [ ] T043 [P] [US1] Create `platform-core/src/main/java/com/einvoice/core/repository/support/EtaInvoiceSpecifications.java` extending `OperationalRepositorySupport`: factories `forCompany(UUID)`, `inState(EtaInvoiceState)`, `withDocumentType(EtaInvoiceDocumentType)`, `issuedBetween(LocalDate, LocalDate)`, plus `inActiveTenantAndAssignedCompany(Set<UUID> assigned)` for the multi-company list per FR-022.

### 3B — ETA authority engine (platform-eta module)

- [ ] T044 [P] [US1] Create `platform-eta/src/main/java/com/einvoice/eta/client/EtaHttpClient.java` — Spring `RestClient` wrapper. Base URLs from a constant map keyed by `authority_environments.id` (PRODUCTION → ETA Production base URL, PREPROD → ETA Pre-Production base URL). Exposes `submitInvoice(...)`, `submitReceipt(...)`, `cancelDocument(...)`, `getDocumentStatus(...)`, `requestToken(...)`. Per research Decision 6. All methods return a typed `EtaHttpResponse` value that distinguishes success / business-rejection / transport-error / timeout.
- [ ] T045 [P] [US1] Create `platform-eta/src/main/java/com/einvoice/eta/token/EtaTokenManager.java` — Caffeine-backed cache keyed by `(companyId, authorityEnvironmentId)` with TTL = (token expiry − 60s) per research Decision 6. On 401, invalidates the entry and performs one refresh attempt before propagating.
- [ ] T046 [P] [US1] Create `platform-eta/src/main/java/com/einvoice/eta/sign/EtaSigningService.java` — CAdES-BES detached signature over canonical JSON bytes using BouncyCastle 1.80; reads cert + private key fresh on every call from `EtaConfigRepository` (Wave 6) per research Decision 8. Throws `NoCertificateConfiguredException` when the config is missing or empty.
- [ ] T047 [P] [US1] Create `platform-eta/src/main/java/com/einvoice/eta/serialize/EtaInvoiceSerializer.java` — Jackson-based, custom `ObjectMapper` with deterministic alphabetical key ordering, ISO-8601 timestamps with offset, BigDecimal as plain string at 5 decimals, no-null suppression, per research Decision 7. Maps `EtaInvoiceHeader` → ETA SDK JSON payload covering all 6 document types (`i`, `c`, `d`, `ei`, `ec`, `ed`). Throws on missing required fields per document type (e.g. `deliveryData` required for `ei`/`ec`/`ed`).
- [ ] T048 [P] [US1] Create `platform-eta/src/main/java/com/einvoice/eta/engine/EtaAuthorityEngine.java` — implements the Phase-2H `AuthorityEngine` SPI. Composes the serializer + signer + HTTP client. Translates `EtaHttpResponse` outcomes to `AuthorityResponse` (success/rejected/error/timeout/ambiguous).
- [ ] T049 [P] [US1] Create golden-file fixtures under `platform-eta/src/test/resources/golden/invoices/`: one canonical input + expected serialized payload per document type (`i.json`, `c.json`, `d.json`, `ei.json`, `ec.json`, `ed.json`). Inputs are constructed from a deterministic test builder; expected outputs are the byte-exact ETA JSON the serializer should produce.
- [ ] T050 [US1] Create `platform-eta/src/test/java/com/einvoice/eta/serialize/EtaInvoiceSerializerGoldenTest.java` — parameterised over the 6 fixtures; asserts byte-for-byte equality between serializer output and the golden file. Constitution XXIV.1 enforcement. Run via `mvn -pl platform-eta test`.
- [ ] T051 [US1] Create `platform-eta/src/test/java/com/einvoice/eta/sign/EtaSigningServiceTest.java` — uses a Wave-6 test-fixture certificate (already present under `platform-eta/src/test/resources/certs/`, or add one if absent) to assert (a) signing returns a non-empty CAdES envelope, (b) re-signing the same input yields the same signature (deterministic), (c) `NoCertificateConfiguredException` is thrown when the test config is empty.
- [ ] T052 [US1] Create `platform-eta/src/test/java/com/einvoice/eta/token/EtaTokenManagerTest.java` — asserts (a) cache hit returns stored token, (b) 401 from a downstream call invalidates the entry and triggers one refresh, (c) cross-environment cache isolation (`(companyA, PREPROD)` and `(companyA, PRODUCTION)` are independent entries; Constitution IV.3).

### 3C — Audit service + submission orchestrator (platform-api)

- [ ] T053 [P] [US1] Create `platform-api/src/main/java/com/einvoice/api/audit/service/AuditService.java` — single emitter exposing `record(action, entityType, entityId, payloadBefore, payloadAfter)`. Reads `companyId`, `authorityEnvironmentId`, `userId` from `TenantContext`. Writes via `AuditLogRepository`. **No update / delete methods.** Per Constitution IX and FR-016/017.
- [ ] T054 [P] [US1] Create `platform-api/src/main/java/com/einvoice/api/audit/service/AuditServiceTest.java` — asserts (a) writes one row with the correct context fields, (b) the rendered payload-after captures key fields without secrets, (c) no public method named `update*` or `delete*` exists on the class (reflective assertion).
- [ ] T055 [US1] Create `platform-api/src/main/java/com/einvoice/api/eta/submission/service/EtaSubmissionOrchestrator.java` — composes the engine + `SubmissionAttemptRepository` + `InvoiceArtifactRepository` + `AuditService`. Implements `submit(EtaInvoiceHeader)`, `cancel(EtaInvoiceHeader, reason)`, `retry(EtaInvoiceHeader)`, `checkStatus(EtaInvoiceHeader)` for the invoice transaction type. The submission flow strictly follows research Decision 4 (split-transaction; in-flight `SubmissionAttempt` row inserted with `result=NULL` before the outbound HTTP call; finalised via `SubmissionAttemptRepository.finalizeAttempt(...)` after the response). Writes one `INVOICE_ARTIFACT` row per attempt for `SIGNED_JSON` and one for `ETA_RESPONSE`. Updates `EtaInvoiceHeader.state` per the `EtaInvoiceLifecycle` matrix only — never mutates state directly. Emits an `AuditLog` row per state transition.

### 3D — Application service + REST controller

- [ ] T056 [US1] Create `platform-api/src/main/java/com/einvoice/api/eta/invoice/service/EtaInvoiceService.java` — orchestrates DTO ↔ entity mapping, calls `EtaMoneyMath.reconcileHeaderTotals` and `reconcileLineTotals` on every save, performs duplicate-number detection, validates `originalDocumentId` existence + type compatibility for credit/debit notes (FR-023), validates that **every line's `unitValue` JSONB carries the four required keys** (`currencySold`, `amountEGP`, `amountSold`, `currencyExchangeRate`) at create/edit time so malformed payloads are rejected at the API boundary rather than at serialise-time, and enforces draft-only edit/delete (FR-007). Exposes `create`, `update` (taking the client's `If-Match` `version`; throws `OptimisticLockConflictException` on mismatch), `delete` (DRAFT only), `findById`, `list(filter)`, and `cloneAsDraft(rejectedId, newInvoiceNumber)` per Q1 / FR-007a (creates a new DRAFT row pre-populated from the rejected source; rejected source unchanged).
- [ ] T057 [US1] Create `platform-api/src/main/java/com/einvoice/api/eta/invoice/service/EtaInvoiceFormMapper.java` — pure DTO ↔ entity mapping. No JPA in the mapper; no business logic.
- [ ] T058 [US1] Create `platform-api/src/main/java/com/einvoice/api/eta/invoice/EtaInvoiceController.java` per `contracts/eta-invoices-api.openapi.yaml` for CRUD endpoints (list, create, get-by-id, update, delete, clone-as-draft). All actions gated by `@RequiresPermission(transactionType=INVOICE, action=…)` from Wave 5. GET responses set `ETag: "<version>"`; PUT requires `If-Match` and returns the structured `ConflictBody` on optimistic-lock conflict per OpenAPI.
- [ ] T059 [US1] Create `platform-api/src/main/java/com/einvoice/api/eta/submission/EtaSubmissionController.java` for `POST /eta/invoices/{docId}/submit`. Delegates to `EtaSubmissionOrchestrator.submit(...)`. Gated by `@RequiresPermission(INVOICE, SUBMIT)`.

### 3E — Contract + integration + lifecycle tests

- [ ] T060 [P] [US1] Create `platform-api/src/test/java/com/einvoice/api/eta/invoice/EtaInvoiceControllerContractTest.java` — Spring Boot Test using MockMvc. Exercises every endpoint in `contracts/eta-invoices-api.openapi.yaml` for invoices: list (with company / status / date filters), POST 201 + ETag, GET 200 + ETag, PUT with valid If-Match → 200, PUT with stale If-Match → 409 ConflictBody, DELETE on draft → 204, DELETE on submitted → 409 DOCUMENT_NOT_DRAFT, **POST with a line whose `unitValue` is missing any of the four required keys (`currencySold`, `amountEGP`, `amountSold`, `currencyExchangeRate`) → 400 with a structured field-error response** (covers the API-boundary validation introduced in T056).
- [ ] T061 [P] [US1] Create `platform-api/src/test/java/com/einvoice/api/eta/submission/EtaSubmissionControllerContractTest.java` — exercises POST /submit / /cancel / /retry / /clone-as-draft for invoices; uses a `@MockBean EtaAuthorityEngine` that returns canned `AuthorityResponse` values for each branch.
- [ ] T062 [US1] Create `platform-api/src/test/java/com/einvoice/api/eta/invoice/EtaInvoiceLifecycleIntegrationTest.java` — Testcontainers-backed (`postgres:16-alpine`). **Scenario A (happy path)**: seed a company + ETA Pre-Production environment + a Wave-6 `eta_configs` row with a test certificate; (a) POST a draft, (b) PUT to mutate it, (c) POST /submit with engine mocked to return SUCCESS — assert state=VALID, etaUuid populated, two `invoice_artifacts` rows present, one `submission_attempts` row with `result=SUCCESS`, and `audit_logs` rows for create + edit + submit (3 actions). Maps to quickstart Phase C and D. **Scenario B (no certificate configured — FR-008 integration coverage)**: seed the same company but with an empty / missing `eta_configs` row; POST a draft, then POST /submit; assert response is 409 `NO_CERTIFICATE_CONFIGURED`, the document remains in `DRAFT`, no `submission_attempts` row is written, and no `invoice_artifacts` row is written. Closes the gap that T051 only covers at the signing-service unit level.
- [ ] T063 [US1] Create `platform-api/src/test/java/com/einvoice/api/eta/submission/EtaSubmissionOrchestratorAmbiguousTest.java` — engine mock throws a simulated transport timeout; assert state lands in `SUBMISSION_AMBIGUOUS`, the in-flight attempt row is finalised with `result=AMBIGUOUS`, the `SIGNED_JSON` artifact is still persisted (it was signed before the outbound call) but `ETA_RESPONSE` is absent. Maps to spec User Story 1 Scenario 4 and FR-011.
- [ ] T064 [US1] Create `platform-api/src/test/java/com/einvoice/api/eta/invoice/EtaInvoiceConflictTest.java` — open a draft with two `MockMvc` sessions, save from session A, attempt to save from session B with the stale If-Match; assert 409 with `code=OPTIMISTIC_LOCK_CONFLICT`, `expectedVersion=0`, `actualVersion=1`, and `current` populated. Maps to FR-025 / research Decision 2 / quickstart Phase F.
- [ ] T065 [US1] Create `platform-api/src/test/java/com/einvoice/api/eta/invoice/EtaInvoiceRejectionTerminalTest.java` — engine mock returns REJECTED; assert state=REJECTED, edit attempt → 409 DOCUMENT_NOT_DRAFT, delete attempt → 409 DOCUMENT_NOT_DRAFT. Then call `POST /clone-as-draft` with a new `invoiceNumber`: assert 201 with a new id and state=DRAFT; assert the rejected source row's `version` and `updatedAt` are unchanged. Maps to FR-007 / Q1 / quickstart Phase G.

### 3F — Angular: invoices feature folder

- [ ] T066 [P] [US1] Create `frontend/src/app/invoices/eta/services/eta-invoice.service.ts` — HttpClient wrapper for `/api/companies/{id}/eta/invoices/*`. Implements `list(filter)`, `getById(id)` (captures the response `ETag`), `create(body)`, `update(id, body, ifMatch)`, `delete(id)`, `cloneAsDraft(rejectedId, newInvoiceNumber)`, `submit(id)`. All methods type-safe against the OpenAPI schema. The `update` method catches HTTP 409 with `code=OPTIMISTIC_LOCK_CONFLICT` and surfaces the structured body to callers.
- [ ] T067 [P] [US1] Create `frontend/src/app/invoices/eta/eta-invoice-list.component.{ts,html,scss}` — Material table with columns Document Number, Company, Type, Issue Date, Total, Status, Actions. Company / Status / Date range filters bound to query parameters. Per-row action buttons gated by `appHasPermission`. Lists records across all assigned companies for the active environment (FR-022).
- [ ] T068 [P] [US1] Create `frontend/src/app/invoices/eta/eta-invoice-form.component.{ts,html,scss}` — Reactive Form (Constitution XIV.1). Captures all header fields and a child `line-items-editor.component` for the lines (Phase 3G). Submit button gated by `INVOICE.CREATE` / `INVOICE.EDIT` per state. On 409 OPTIMISTIC_LOCK_CONFLICT, opens the conflict-resolution dialog (Phase 3G T071).
- [ ] T069 [P] [US1] Create `frontend/src/app/invoices/eta/eta-invoice-detail.component.{ts,html,scss}` — read-only view of header + line items + tax breakdown. Hosts the **Submit** button (when DRAFT) and the **Create new draft from this document** button (when REJECTED — Q1).
- [ ] T070 [US1] Update `frontend/src/app/app.routes.ts` to register `/invoices/eta` (list), `/invoices/eta/new` (form), `/invoices/eta/:id` (detail), `/invoices/eta/:id/edit` (form). Update `frontend/src/app/layout/sidebar/sidebar.component.ts` to render the **Invoices** menu entry gated by `INVOICE.VIEW` on any assigned company in the active environment.

### 3G — Angular: shared editing components (used by both invoices and receipts)

- [ ] T071 [P] [US1] Create `frontend/src/app/invoices/shared/line-items-editor.component.{ts,html,scss}` — array of line rows with item code, description, quantity, unit value, discount, taxes-array sub-editor. Reactive Forms `FormArray`. Emits `lines` valid/invalid status to the parent form.
- [ ] T072 [P] [US1] Create `frontend/src/app/invoices/shared/conflict-resolution.dialog.{ts,html,scss}` — Material Dialog showing the user's pending changes vs the server's current document side-by-side. Two buttons: **Discard mine** (close dialog, reload form with `current`) and **Overwrite with mine** (re-PUT with the new `If-Match` from the conflict body's `actualVersion`). Per FR-025 / research Decision 2.
- [ ] T073 [US1] Create `frontend/src/app/invoices/eta/eta-invoice-form.component.spec.ts` — component tests covering (a) form validity on a happy 1-line input, (b) totals reconciliation feedback to the user, (c) submit-disabled-until-valid, (d) 409 → opens conflict dialog with both versions; tests use HttpClient testing module to inject the canned 409 response, (e) **on form init in "new invoice" mode, `sellerData` is auto-populated from the active company's master record** (covers User Story 1 Scenario 1 — the form is "pre-scoped to their active company and ETA environment with the company's data pre-filled as seller").

**Checkpoint**: After this phase, User Story 1 is fully functional end-to-end. Quickstart phases A–D + F + G pass. This is the MVP.

---

## Phase 4: User Story 2 — Create, sign, and submit an ETA receipt (Priority: P1)

**Goal**: A `COMPANY_ADMIN` user can create and submit any v1.2 receipt subtype end-to-end, with B2C (buyer-optional) and return/cancellation (original-receipt-required) variants supported.

**Independent Test**: Quickstart Phase K (receipts smoke test) passes without User Story 3, 4, or 5 implemented.

### 4A — Backend entities + repositories

- [X] T074 [P] [US2] Create `platform-core/src/main/java/com/einvoice/core/domain/eta/EtaReceiptHeader.java` per data-model.md §4. Same JPA shape as `EtaInvoiceHeader` with the differences from the spec (nullable `buyerData`, `posSerial`, `paymentMethod`, `originalReceiptId`, `documentTypeVersion` default `1.2`, `receiptNumber`).
- [X] T075 [P] [US2] Create `platform-core/src/main/java/com/einvoice/core/domain/eta/EtaReceiptLine.java` and `EtaReceiptLineTax.java` per data-model.md §5–6.
- [X] T076 [US2] Create `platform-core/src/main/java/com/einvoice/core/repository/eta/EtaReceiptHeaderRepository.java` with the receipts equivalent of T042's helper method.
- [X] T077 [P] [US2] Create `platform-core/src/main/java/com/einvoice/core/repository/support/EtaReceiptSpecifications.java` mirroring `EtaInvoiceSpecifications`.

### 4B — ETA serializer extension for receipts

- [X] T078 [P] [US2] Create `platform-eta/src/main/java/com/einvoice/eta/serialize/EtaReceiptSerializer.java` covering all 23 v1.2 subtypes per research Decision 7. Uses the same configured `ObjectMapper` as the invoice serializer (deterministic ordering, 5-decimal money, no nulls).
- [X] T079 [P] [US2] Create golden-file fixtures under `platform-eta/src/test/resources/golden/receipts/`: at least 3 representative subtypes — `r.json` (standard sale), `cr.json` (cancellation), `rr.json` (return).
- [X] T080 [US2] Create `platform-eta/src/test/java/com/einvoice/eta/serialize/EtaReceiptSerializerGoldenTest.java` — parameterised over the receipt fixtures, byte-for-byte equality assertions.

### 4C — AuthorityEngine extension + orchestrator overload

- [X] T081 [US2] Extend `platform-eta/src/main/java/com/einvoice/eta/engine/EtaAuthorityEngine.java` to handle the `RECEIPT` transaction type — same submission shape as invoices, with the receipt serializer/HTTP-client paths.
- [X] T082 [US2] Extend `platform-api/src/main/java/com/einvoice/api/eta/submission/service/EtaSubmissionOrchestrator.java` to also support receipts: same flow per research Decision 4, parameterised by `transactionType=RECEIPT` and using `EtaReceiptLifecycle` for state transitions.

### 4D — Application service + REST controller

- [X] T083 [US2] Create `platform-api/src/main/java/com/einvoice/api/eta/receipt/service/EtaReceiptService.java` mirroring `EtaInvoiceService` with these differences: `buyerData` optional in validation; `originalReceiptId` required for return/cancellation subtypes per `EtaReceiptDocumentType.requiresOriginalDocument()`; receipt-number uniqueness keyed by `(companyId, authorityEnvironmentId, receiptNumber)`.
- [X] T084 [P] [US2] Create `platform-api/src/main/java/com/einvoice/api/eta/receipt/service/EtaReceiptFormMapper.java`.
- [X] T085 [US2] Create `platform-api/src/main/java/com/einvoice/api/eta/receipt/EtaReceiptController.java` per `contracts/eta-receipts-api.openapi.yaml` for CRUD endpoints. Extends `EtaSubmissionController` paths to receipts (`POST /eta/receipts/{docId}/submit`, etc.) by wiring the controller to the same orchestrator with `transactionType=RECEIPT`.

### 4E — Contract + integration tests

- [X] T086 [P] [US2] Create `platform-api/src/test/java/com/einvoice/api/eta/receipt/EtaReceiptControllerContractTest.java` mirroring T060, covering b2c (buyer-absent) and return-subtype (original-required) flows. Asserts 400 `MISSING_ORIGINAL_DOCUMENT` when a `cr` receipt is created without `originalReceiptId`.
- [X] T087 [US2] Create `platform-api/src/test/java/com/einvoice/api/eta/receipt/EtaReceiptLifecycleIntegrationTest.java` mirroring T062 for the receipt happy path.

### 4F — Angular: receipts feature folder

- [X] T088 [P] [US2] Create `frontend/src/app/receipts/eta/services/eta-receipt.service.ts` mirroring T066.
- [X] T089 [P] [US2] Create `frontend/src/app/receipts/eta/eta-receipt-list.component.{ts,html,scss}` mirroring T067 (with the additional `receiptType` filter from the OpenAPI contract).
- [X] T090 [P] [US2] Create `frontend/src/app/receipts/eta/eta-receipt-form.component.{ts,html,scss}` mirroring T068. The form adapts to the selected `documentType`: B2C subtypes hide the buyer card; return/cancellation subtypes show a required "Original receipt" picker. Reuses `line-items-editor.component` and `conflict-resolution.dialog` from Phase 3G.
- [X] T091 [P] [US2] Create `frontend/src/app/receipts/eta/eta-receipt-detail.component.{ts,html,scss}` mirroring T069.
- [X] T092 [US2] Update `frontend/src/app/app.routes.ts` to register `/receipts/eta/*` routes; update `sidebar.component.ts` to render the **Receipts** entry gated by `RECEIPT.VIEW`.

**Checkpoint**: After this phase, US1 and US2 are both independently testable. Quickstart phases C, D, K pass.

---

## Phase 5: User Story 3 — Track submission history and download authority artifacts (Priority: P2)

**Goal**: Users can view the full submission timeline of any submitted document, download any stored artifact byte-for-byte, retry an ambiguous submission, and forward a cancellation to ETA.

**Independent Test**: Quickstart phases H and I pass — bulk Check status fans out concurrently and updates documents per ETA outcomes; cancellation forwards to ETA without platform-side window check.

### 5A — Status service + bulk fan-out infrastructure

- [X] T093 [P] [US3] Create `platform-eta/src/main/java/com/einvoice/eta/status/EtaStatusService.java` exposing `checkStatus(EtaInvoiceHeader)` and `checkStatus(EtaReceiptHeader)`. Calls `EtaHttpClient.getDocumentStatus` with the stored `etaSubmissionId`; returns an `AuthorityResponse` to the orchestrator.
- [X] T094 [P] [US3] Create `platform-api/src/main/java/com/einvoice/api/eta/submission/service/BulkStatusCheckExecutor.java` — bounded thread pool (8 concurrent calls, named `eta-bulk-status-pool`) per research Decision 5. Exposes `runBulk(transactionType, List<UUID> documentIds) → List<BulkStatusOutcome>`. For each id: permission check (`REFRESH` on the document's company+env), then `EtaSubmissionOrchestrator.checkStatus(...)`. Denied ids return `FORBIDDEN`; unknown ids return `NOT_FOUND`. Validates batch size ≤ 200 and throws `BulkBatchLimitExceededException` otherwise.
- [X] T095 [P] [US3] Create `platform-api/src/test/java/com/einvoice/api/eta/submission/BulkStatusCheckExecutorTest.java` — covers (a) parallel execution of 50 ids returns per-id outcomes within budget, (b) batch size 201 → 400 `BULK_BATCH_LIMIT_EXCEEDED`, (c) mixed permission outcomes per id (FORBIDDEN coexists with UPDATED).

### 5B — Submission history + artifact download endpoints

- [X] T096 [P] [US3] Extend `EtaSubmissionController` with: `POST /eta/invoices/check-status` and `POST /eta/receipts/check-status` (single + bulk per research Decision 5; single-id is a length-1 array); `POST /eta/invoices/{id}/cancel`, `POST /eta/invoices/{id}/retry`, and the receipts counterparts; `GET /eta/invoices/{id}/submissions` and the receipts counterpart.
- [X] T097 [P] [US3] Create `platform-api/src/main/java/com/einvoice/api/eta/artifact/EtaArtifactController.java` — implements `GET /eta/invoices/{docId}/artifacts/{type}?attemptNumber=` and receipts counterpart. Returns the artifact bytes with `Content-Disposition: attachment` and `X-Artifact-Hash` per the OpenAPI contract. When `attemptNumber` is absent, returns the most-recent artifact of the requested type.
- [X] T098 [P] [US3] Create `platform-api/src/test/java/com/einvoice/api/eta/artifact/EtaArtifactControllerContractTest.java` — asserts (a) 200 with correct MIME, (b) `X-Artifact-Hash` equals SHA-256 of the body, (c) `attemptNumber=N` selects the correct attempt's artifact, (d) 404 when no artifact of the requested type exists.

### 5C — Cancel + retry flow

- [X] T099 [US3] Extend `EtaSubmissionOrchestrator.cancel(...)` and `.retry(...)` paths to match research Decision 4's split-transaction model. Cancel forwards every request to ETA with no time-window short-circuit (FR-013 / Q3); the outcome row is a new `submission_attempts` entry; the state transition is driven by `EtaInvoiceLifecycle.next(...)` based on ETA's response.
- [X] T100 [US3] Create `platform-api/src/test/java/com/einvoice/api/eta/submission/EtaSubmissionOrchestratorCancelTest.java` — engine mock returns SUCCESS → state=CANCELLED; engine mock returns REJECTED (out-of-window) → state stays VALID, rejection reason appears in submission history. **Verify a single outbound call is issued regardless of elapsed time** (Q3 enforcement via test on the mock invocation count).
- [X] T101 [US3] Create `platform-api/src/test/java/com/einvoice/api/eta/submission/EtaSubmissionOrchestratorRetryTest.java` — from `SUBMISSION_AMBIGUOUS`, retry with engine returning SUCCESS → state=VALID, attempt 2 row finalised, artifacts deduplicated by attempt number.

### 5D — Angular: shared submission UI

- [X] T102 [P] [US3] Create `frontend/src/app/invoices/shared/submission-history.component.{ts,html,scss}` — vertical timeline of attempts (number, timestamp, user, result, error summary). Rendered inside both invoice and receipt detail screens.
- [X] T103 [P] [US3] Create `frontend/src/app/invoices/shared/artifact-download.component.{ts,html,scss}` — buttons per artifact type. Each button triggers a download via the service-layer call to `GET /artifacts/{type}`.
- [X] T104 [P] [US3] Create `frontend/src/app/invoices/shared/bulk-status-check.dialog.{ts,html,scss}` — Material Dialog that shows a per-document progress list with success/failed/forbidden indicators. Driven by the bulk endpoint response. Opened from the list-screen's bulk action toolbar.
- [X] T105 [US3] Update `eta-invoice-list.component.ts` and `eta-receipt-list.component.ts` to add (a) the per-row checkbox column, (b) the "Select all matching the current search" toggle, (c) the toolbar bulk-action **Check status** button gated by `REFRESH`, (d) the dialog dispatch via T104.
- [X] T106 [P] [US3] Update `eta-invoice-detail.component.ts` and `eta-receipt-detail.component.ts` to host `submission-history.component`, `artifact-download.component`, the per-document **Check status** button (gated by `REFRESH`), the **Cancel** action (gated by `CANCEL`, visible when state=`VALID`), and the **Retry** action (gated by `SUBMIT`, visible when state=`SUBMISSION_AMBIGUOUS`).

**Checkpoint**: After this phase, US1 + US2 + US3 are independently complete. Quickstart phases C, D, H, I, K pass.

---

## Phase 6: User Story 4 — Authority environment isolation (Priority: P2)

**Goal**: Confirm by integration test that records created under one authority environment are 100% invisible from another for the same company.

**Independent Test**: Quickstart Phase E passes.

> All isolation enforcement is already in place via Wave 5's `TenantContext` + `TenantFilter` and the new specifications in Phases 3 and 4. This phase **validates** that enforcement rather than adding new behaviour.

### 6A — Cross-environment integration tests (Constitution XXIV.6)

- [X] T107 [US4] Create `platform-api/src/test/java/com/einvoice/api/eta/isolation/EtaInvoiceCrossEnvIsolationTest.java` — Testcontainers-backed. Seed one company assigned to both `authority_environment_id = 1` (PRODUCTION) and `2` (PREPROD). Create an invoice under env 2 (PREPROD session); switch session to env 1 (PRODUCTION). Assert (a) `GET /eta/invoices` does not return the PREPROD invoice, (b) `GET /eta/invoices/{prepropId}` returns 404 (not 403; ID enumeration must not leak existence — per quickstart Phase E), (c) `POST /eta/invoices/check-status` with the PREPROD id returns `outcome=NOT_FOUND`. Repeat the test for receipts.
- [X] T108 [P] [US4] Create `platform-api/src/test/java/com/einvoice/api/eta/isolation/EtaReceiptCrossEnvIsolationTest.java` — receipts equivalent.
- [X] T109 [US4] Create `platform-api/src/test/java/com/einvoice/api/eta/isolation/AdminModeRejectsOperationalEndpointsTest.java` — a Super User logs in **without** selecting a company (Admin Mode). Every Wave-7 endpoint is exercised; all must return 403 `COMPANY_CONTEXT_REQUIRED`. Constitution VII.4 enforcement.

**Checkpoint**: After this phase, the isolation property holds across both transaction types and across operational vs admin mode. Quickstart phases C–E + K pass.

---

## Phase 7: User Story 5 — Immutable artifacts and audit trail (Priority: P3)

**Goal**: Verify that artifacts and audit logs cannot be modified or deleted through any platform code path, and that every state-changing action emits an audit log.

**Independent Test**: Quickstart Phase J passes — direct SQL `UPDATE` / `DELETE` on `invoice_artifacts` and `audit_logs` is rejected by the database trigger.

> Same as Phase 6: append-only enforcement is already in place (V52 trigger, `WriteOnlyRepository` marker, append-only enforcement reflective test from Phase 2E). This phase adds the **verification suite**.

### 7A — Immutability and audit-emission tests

- [X] T110 [P] [US5] Create `platform-core/src/test/java/com/einvoice/core/repository/shared/AppendOnlyDbTriggerTest.java` — Testcontainers-backed. Inserts one row into each of `invoice_artifacts` and `audit_logs` via the repository, then issues raw JDBC `UPDATE` and `DELETE` against each row; asserts both statements fail with `ERROR: append_only_table` (V52 trigger). For `submission_attempts`, asserts UPDATE on the allowlisted columns succeeds and UPDATE on a non-allowlisted column (e.g. `attempt_number`) fails.
- [X] T111 [P] [US5] Create `platform-api/src/test/java/com/einvoice/api/audit/AuditEmissionCoverageTest.java` — Spring Boot Test. For each user-facing action across the invoice and receipt controllers (create, edit, delete-draft, submit, cancel, retry, check-status, clone-as-draft, overwrite-on-conflict), assert exactly one `audit_logs` row is written with the expected `action` string and the actor's `userId`. Asserts FR-016.
- [X] T112 [US5] Create `platform-api/src/test/java/com/einvoice/api/audit/AuditLogReadOnlyApiTest.java` — asserts that no REST endpoint exposes UPDATE or DELETE on `audit_logs` or `invoice_artifacts` (the controllers don't even have a method for it; the test scans the application's `RequestMappingHandlerMapping` for any handler whose URL contains `audit_logs` or `invoice_artifacts` with method `PUT`/`PATCH`/`DELETE` — should be empty).

**Checkpoint**: After this phase, all 5 user stories are independently functional. Quickstart phases A–L all pass.

---

## Phase 8: Polish & Cross-Cutting Concerns

**Purpose**: Final-mile validation, performance budgets, documentation, and quickstart sign-off.

- [X] T113 [P] Run `mvn -pl platform-core,platform-security,platform-eta,platform-api clean verify` from repo root; resolve any test or build failures. No `-DskipTests`.
- [X] T114 [P] Run `cd frontend && npm ci && npm run lint && npm run test -- --watch=false && npm run build`; resolve any failures.
- [ ] T115 Verify performance budgets per research Decision 10 via lightweight JMeter or `k6` script under `tests/perf/wave7/`: list 1000 rows < 2s p95, single-document submit < 5s p95 (with engine mocked, isolating platform-side cost), bulk check-status (200 docs) < 30s p95. Capture results in `specs/008-eta-docs-submission/perf-results.md` (created in this task; not previously listed in plan deliverables — append to the spec dir).
- [X] T116 [P] Final-mile verification of the state-machine codegen: confirm the generated `frontend/src/app/invoices/shared/generated/eta-states.ts` (produced by T038a) is in sync with the latest Java enums after all of Phases 3–7 land — re-run `mvn -pl platform-core process-classes` and `git diff --exit-code frontend/src/app/invoices/shared/generated/` should be clean. Any drift here is an immediate red flag that an enum was edited without re-running the codegen.
- [ ] T117 [P] Manually run quickstart phases A–L end-to-end against a freshly-migrated database with ETA Pre-Production sandbox credentials; document any deviation from expected behaviour as a follow-up issue.
- [X] T118 [P] Update `CLAUDE.md` (already touched by `update-agent-context.ps1`) and `Docs/configuration-reference.md` with any post-implementation notes (e.g. confirmed ETA endpoint URLs, observed token TTLs, any quirks discovered during T117).
- [ ] T119 Tag the merge commit `wave7-eta-docs-submission-complete` on the integration branch once `mvn clean verify` and `ng test` are green and T117 passes.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: T001–T003 — no dependencies; can start immediately. T002 and T003 are parallel docs edits.
- **Foundational (Phase 2)**: depends on Phase 1. T004–T010 (2A migrations) are strictly sequential by version. T011–T021 (2B enums + lifecycle) parallelize across distinct files. T022–T023 (2C money math) parallelize. T024–T026 (2D shared entities) parallelize. T027–T031 (2E shared repos + enforcement test) — T027 first, then T028–T030 in parallel, then T031. T032–T034 (2F exceptions + handler) — T032 first, then T033 and T034 in parallel. T035–T036 (2G permission seed) sequential. T037–T038 (2H AuthorityEngine SPI) — T037 first, then T038 in parallel. **All of Phase 2 must complete before any user-story phase begins.**
- **User Stories (Phases 3–7)**: each depends only on Phase 2. They can be executed sequentially in priority order (recommended for solo dev) or in parallel by separate developers (per the Parallel Team Strategy).
- **Polish (Phase 8)**: depends on whichever user stories are in scope; if shipping MVP-only, T113–T117 still apply minus the receipt-specific verifications.

### User Story Dependencies

- **US1 (P1)**: depends only on Phase 2. The MVP slice.
- **US2 (P1)**: depends on Phase 2 + reuses `line-items-editor.component` and `conflict-resolution.dialog` from Phase 3G (frontend-only dependency). Backend has zero cross-story dependency.
- **US3 (P2)**: depends on Phase 2 + the orchestrator/engine/controllers introduced in US1 and US2 (it extends them with cancel/retry/check-status). Frontend extends list and detail components from US1 + US2.
- **US4 (P2)**: depends on Phase 2 + at least US1 (so there's something to test isolation against). Adds tests only; no production code.
- **US5 (P3)**: depends on Phase 2 + at least US1 (so there are documents producing artifacts and audit rows). Adds tests only; no production code.

### Within Each User Story

- Backend entities → repositories → services → controllers → contract tests → integration tests.
- Frontend services → list/form/detail components → routing → component tests.
- The `[P]`-marked tasks within a phase are independent of one another and can be run by separate developers.

### Parallel Opportunities

- All `[P]`-marked Phase 1 docs tasks run together.
- All Phase 2 enum tasks (T011–T017) run in parallel.
- All Phase 2 shared-entity tasks (T024–T026) run in parallel.
- All Phase 3A entity tasks (T039–T041) run in parallel.
- All Phase 3B engine-side tasks (T044–T047) run in parallel.
- All Phase 3F frontend service + component shells (T066–T069) run in parallel.
- All Phase 4 (receipts) and Phase 3 (invoices) backend layers run in parallel by separate developers once Phase 2 is done.
- All Phase 6 + Phase 7 verification suites run in parallel once their dependent user stories are merged.

---

## Parallel Example: User Story 1 backend

```bash
# Launch all Phase 3A entities together (different files, no dependencies):
Task: "Create EtaInvoiceHeader entity at platform-core/src/main/java/com/einvoice/core/domain/eta/EtaInvoiceHeader.java"
Task: "Create EtaInvoiceLine entity at platform-core/src/main/java/com/einvoice/core/domain/eta/EtaInvoiceLine.java"
Task: "Create EtaInvoiceLineTax entity at platform-core/src/main/java/com/einvoice/core/domain/eta/EtaInvoiceLineTax.java"

# Then launch Phase 3B engine-side tasks together:
Task: "Create EtaHttpClient at platform-eta/src/main/java/com/einvoice/eta/client/EtaHttpClient.java"
Task: "Create EtaTokenManager at platform-eta/src/main/java/com/einvoice/eta/token/EtaTokenManager.java"
Task: "Create EtaSigningService at platform-eta/src/main/java/com/einvoice/eta/sign/EtaSigningService.java"
Task: "Create EtaInvoiceSerializer at platform-eta/src/main/java/com/einvoice/eta/serialize/EtaInvoiceSerializer.java"
```

---

## Implementation Strategy

### MVP First (User Story 1 only)

1. Complete Phase 1: Setup (T001–T003).
2. Complete Phase 2: Foundational in full (T004–T038) — schema, lifecycle matrix, shared operational tables, permission seeds, AuthorityEngine SPI.
3. Complete Phase 3: User Story 1 (T039–T073) — invoice create / submit end-to-end.
4. **STOP and VALIDATE**: Run quickstart phases A–G against ETA Pre-Production. If all pass, this is the MVP.
5. Ship internal demo; gather feedback before US2.

### Incremental Delivery (recommended)

1. MVP (US1) → demo → ship.
2. Add US2 (receipts) — shares Phase 2 infrastructure, reuses frontend shared components, no breaking changes to US1. → demo → ship.
3. Add US3 (submission history + retry/cancel + bulk check-status) — extends orchestrator + UI. → demo → ship.
4. Add US4 (isolation tests) and US5 (immutability tests) — test-only phases; ship in CI gate.

### Parallel Team Strategy

With multiple developers:

1. Whole team: Phase 1 + Phase 2 together (one PR per Phase-2 sub-section).
2. Once Phase 2 is merged to integration branch:
   - Developer A: Phase 3 (Invoice US1)
   - Developer B: Phase 4 (Receipt US2)
   - Developer C: prepares Phase 5 scaffolding (status service, bulk executor)
3. After US1 + US2 merge: Developer C completes Phase 5 (US3).
4. After US3 merge: Developers A and B run Phase 6 + Phase 7 verification suites in parallel.
5. Phase 8 polish before tagging.

---

## Notes

- All tasks specify exact repo-relative file paths so an LLM can execute them without round-tripping context.
- `[P]` = different files, no dependencies on incomplete tasks in the same phase.
- The append-only enforcement is **three layers** — repository marker (T027–T031), service surface (no UPDATE/DELETE methods exposed, T053–T054, T112), and database trigger (T008, T010). Constitution IX.3 and XXI.4 require this defence-in-depth.
- The state machine has a **single source of truth** in `LifecycleTransitions` (T018, T019); the Angular side imports a generated TypeScript copy of the same matrix (T116) so UI button-gating and backend enforcement can never drift.
- The submission flow's split-transaction shape (research Decision 4) is the only correct way to satisfy Constitution XX.1 — every task that touches the orchestrator (T055, T081–T082, T099) must respect that ordering.
- Verify tests fail before implementation where TDD is sensible (e.g. T020/T021 before T018/T019; T031 before T027–T030 succeed).
- Commit after each task or logical group; tag MVP completion after T073.
- Stop at any checkpoint to validate independently — each user story is designed to be an end-to-end working slice.

## Task counts

- **Phase 1 (Setup)**: 3 tasks
- **Phase 2 (Foundational)**: 36 tasks (T004–T038, T038a)
- **Phase 3 (US1 — Invoice, P1, MVP)**: 35 tasks (T039–T073)
- **Phase 4 (US2 — Receipt, P1)**: 19 tasks (T074–T092)
- **Phase 5 (US3 — Submission history + retry/cancel + bulk, P2)**: 14 tasks (T093–T106)
- **Phase 6 (US4 — Isolation tests, P2)**: 3 tasks (T107–T109)
- **Phase 7 (US5 — Immutability + audit tests, P3)**: 3 tasks (T110–T112)
- **Phase 8 (Polish)**: 7 tasks (T113–T119)

**Total: 120 tasks** across 8 phases. Parallel opportunities: 76 tasks marked `[P]`. (Original 119; T038a added during analysis remediation U1; T116 retained with reduced scope as a final-mile verification of the codegen wired by T038a.)
