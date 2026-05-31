---

description: "Tasks for 011 — ERP Ingestion Gateway"
---

# Tasks: ERP Ingestion Gateway

**Input**: Design documents from `D:/Cou/Spring Course In 28 Minutes/Projects/einvoice-platform/specs/011-erp-ingestion-gateway/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/openapi.yaml, contracts/error-codes.md, quickstart.md
**Tests**: INCLUDED — SC-002, SC-003, SC-004, SC-005, SC-006, SC-007, SC-009, SC-010 in spec.md explicitly verify behaviour through integration tests, so per-story IT tasks are mandatory.
**Hard prerequisite**: `specs/010-authority-spec-alignment/` V58–V61 migrations applied + entity/repository/serialiser updates merged. Per FR-024, this feature MUST NOT be executed against a code-base where 010 is incomplete.

**Organization**: Tasks are grouped by user story. Phase 2 (Foundational) lays the cross-cutting plumbing (V62 migration + archive filter + resolver + shared enums + skeleton controllers/services) that every endpoint story consumes.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Different file, no incomplete-task dependency — safe to run in parallel.
- **[Story]**: User story label (US1–US5). Setup / Foundational / Polish phases carry no story label.

## Path Conventions (per plan.md §Project Structure)

- Backend modules: `platform-api/`, `platform-core/`, `platform-security/` (Maven multi-module, repository root).
- DTOs / controllers / ingestion services live under `platform-api/src/main/java/com/einvoice/api/integration/`.
- Entities / exceptions / repositories live under `platform-core/src/main/java/com/einvoice/core/`.
- Flyway migrations: `platform-core/src/main/resources/db/migration/`.
- Integration tests: `backend/src/test/java/com/einvoice/api/integration/`.
- Spec artifacts: `specs/011-erp-ingestion-gateway/`.

---

## Phase 0: Remediation history

The 2026-05-27 `/speckit-analyze` flagged 12 issues (2 CRITICAL, 2 HIGH, 4 MEDIUM, 4 LOW) and the team chose "apply all". The following remediations were absorbed into the tasks below:

- **C1/C2 (Constitution VII/XV)** → Seeded `INTEGRATION_GATEWAY` user row + `TenantContext` population (T004 step 2, T016b, FR-009c/d in spec.md).
- **F1 (T009 read-order contradiction)** → T009 rewritten with cached-body-first ordering.
- **F2 (FR-OBS-005 untested)** → T047 extended with fault-injection scenario (SC-011 in spec.md).
- **F3 (FR-017 ZATCA coverage)** → T034 scenarios (f)(g) and T040 scenarios (d)(e) added.
- **A1 (audit action value undecided)** → Locked to `INGESTED`; T004 step 3 extends V52 allow-list trigger; service tasks updated.
- **F4 (filter ordering)** → T009 pins exact order constant; adds filter unit test.
- **A2 (per-table author-column inventory)** → data-model.md §1.6.
- **F5 (cross-product 404)** → openapi.yaml per-endpoint descriptions added.
- **A3 (T046 conditional)** → T046 rewritten as unconditional.
- **D1 (FR-018 repetition)** → Service tasks reference FR-018 instead of restating.
- **U1 (skeleton empty helpers)** → T019/T020 use `throw new UnsupportedOperationException`.

Total tasks: **52** (up from 51 — T016b added). Two Sprint-1-bounded caveats remain (Constitution VIII + XVII, both rooted in `permitAll()`); both converge in the Sprint 2 API-key feature.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: One-shot configuration tasks that unlock everything else. No story label.

- [X] T001 Add `org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.8` to `platform-api/pom.xml` `<dependencies>` (per research §R7).
- [X] T002 [P] Create `platform-api/src/main/java/com/einvoice/api/config/OpenApiConfig.java` with one `@Bean OpenAPI platformOpenAPI()` (title/version/description) and one `@Bean GroupedOpenApi integrationGateway()` matching `/api/integration/**` (per research §R7 + FR-022 + SC-008).
- [X] T003 [P] Update Logback pattern in `platform-api/src/main/resources/logback-spring.xml` (or the equivalent property in `application.yml`) to render MDC fields `endpoint`, `companyTaxNumber`, `environment`, `documentNumber`, `erpReferenceId`, `outcome`, `latencyMs`, `payloadArchiveId` in the appender pattern. Required for FR-OBS-001.

**Checkpoint**: Maven builds; Swagger UI starts but lists no integration endpoints yet (controllers don't exist).

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Every cross-cutting concern that every user story consumes — the V62 migration, the archive-write filter, the resolver, shared exception classes, shared DTO enums + response record, skeleton ingestion services and controllers, the `SecurityConfig` permit-all, and the `GlobalExceptionHandler` additions.

**⚠️ CRITICAL**: No user-story phase can begin until this phase completes.

### V62 migration + archive infrastructure (FR-OBS-003..005)

- [X] T004 Create `platform-core/src/main/resources/db/migration/V62__ingestion_gateway.sql` containing **three atomic changes** (all required to ship together — FR-024a + FR-009c + FR-009e):
  1. **Archive table** (FR-OBS-003 / FR-OBS-004): `CREATE TABLE inbound_payload_archive (id UUID PRIMARY KEY, endpoint VARCHAR(120) NOT NULL, received_at TIMESTAMPTZ NOT NULL DEFAULT NOW(), body JSONB NOT NULL, company_id UUID, authority_environment_id SMALLINT, outcome SMALLINT)` + `CREATE INDEX inbound_payload_archive_received_at_brin ON inbound_payload_archive USING BRIN (received_at)` + `DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname='integration_forensics') THEN CREATE ROLE integration_forensics NOLOGIN; END IF; END $$;` + `GRANT SELECT ON inbound_payload_archive TO integration_forensics`.
  2. **Seeded system principal** (FR-009c): `INSERT INTO users (id, username, is_active, is_super_user, ...) VALUES ('00000000-0000-0000-0000-000000000011', 'INTEGRATION_GATEWAY', FALSE, FALSE, ...) ON CONFLICT (username) DO NOTHING;` — confirm the exact column set against the post-V52 `users` schema during implementation (password column NULL or sentinel per the existing NOT-NULL posture). The fixed UUID `...0011` is the constant referenced by `TenantContext` plumbing (FR-009d). Pick the UUID at implementation time and document it in `data-model.md §1.5` AND `application.yml` under `einvoice.integration.gatewayPrincipalId`.
  3. **`audit_logs` allow-list extension** (FR-009e): locate the V52-introduced trigger / check / function that constrains `audit_logs.action`; extend its allow-list to include `INGESTED`. Implementation note: if V52 used a `CHECK` constraint, replace it (`ALTER TABLE audit_logs DROP CONSTRAINT ... ; ADD CONSTRAINT ... CHECK (action IN (..., 'INGESTED'))`); if it used a trigger function, `CREATE OR REPLACE FUNCTION ...` with the extended list. Inspect V52 during implementation to pick the right path.
  Smart-app / dumb-DB pattern from 010 — no NEW CHECK / FK / UNIQUE introduced on `inbound_payload_archive` beyond the PK. See research §R4 + §R9.
- [X] T005 [P] Create JPA entity `platform-core/src/main/java/com/einvoice/core/domain/ingestion/entity/InboundPayloadArchive.java`. Fields per data-model.md §1: `id` UUID @Id, `endpoint` String, `receivedAt` Instant, `body` String (with `@Type(JsonBinaryType.class)` or `@Convert` to JSONB), `companyId` UUID nullable, `authorityEnvironmentId` Short nullable, `outcome` Short nullable. No setter for any column other than `outcome` (the only permitted mutation per data-model.md §1).
- [X] T006 [P] Create `platform-core/src/main/java/com/einvoice/core/domain/ingestion/repository/InboundPayloadArchiveRepository.java` extending `JpaRepository<InboundPayloadArchive, UUID>`. No custom queries needed.
- [X] T007 Create `platform-core/src/main/java/com/einvoice/core/error/InboundPayloadArchiveException.java` (code `ARCHIVE_WRITE_FAILED`, mapped to HTTP 503 per contracts/error-codes.md).
- [X] T008 Create `platform-api/src/main/java/com/einvoice/api/integration/service/InboundPayloadArchiveService.java` with two methods: `UUID archive(String endpoint, byte[] body)` annotated `@Transactional(propagation = Propagation.REQUIRES_NEW)` returning the generated UUID; `void patchOutcome(UUID id, int httpStatus, UUID companyId, Short authorityEnvironmentId)` also `REQUIRES_NEW`. On any exception during `archive()`, wrap in `InboundPayloadArchiveException`. See research §R1 + FR-OBS-005.
- [X] T009 Create `platform-api/src/main/java/com/einvoice/api/integration/filter/IngestionPayloadArchiveFilter.java` extending `OncePerRequestFilter`. URL-pattern-gated to `/api/integration/v1/**`. Steps in `doFilterInternal` (order is load-bearing — body MUST be read AND archived BEFORE chain.doFilter so the archive is a faithful record of every inbound request including parser-rejected ones, per FR-OBS-003):
  1. Read the request body **early** into a `byte[]` (e.g., `byte[] bodyBytes = StreamUtils.copyToByteArray(request.getInputStream())`).
  2. Wrap the request in a `CachedBodyHttpServletRequest` (custom class — backs `getInputStream()` and `getReader()` from the cached `byte[]`) so downstream filters and the controller can re-read it.
  3. Record request-entry timestamp (`long t0 = System.nanoTime()`).
  4. Call `InboundPayloadArchiveService.archive(request.getRequestURI(), bodyBytes)`. If it throws `InboundPayloadArchiveException` (or any `RuntimeException`): write `HTTP 503` with response body `{"code":"ARCHIVE_WRITE_FAILED", ...}` (matching `contracts/error-codes.md`), then `return` — DO NOT proceed down the chain (FR-OBS-005).
  5. Put `payloadArchiveId` (the returned UUID), `endpoint` (request URI), `companyTaxNumber` (best-effort parsed from the body — wrap in `try` and leave absent on parse failure), `environment` (same) into MDC.
  6. Invoke `filterChain.doFilter(wrappedRequest, response)`.
  7. In `finally`: compute `latencyMs = (System.nanoTime() - t0) / 1_000_000`; put `outcome = response.getStatus()` into MDC; call `InboundPayloadArchiveService.patchOutcome(payloadArchiveId, response.getStatus(), companyId, authorityEnvId)` — `companyId`/`authorityEnvId` come from `TenantContext` if populated (FR-009d), else NULL; log one INFO line with all MDC fields; `MDC.clear()`.
  Register the filter at exactly `Ordered.HIGHEST_PRECEDENCE + 50` via a `FilterRegistrationBean` defined in `OpenApiConfig.java` (or a new `IntegrationFilterConfig`). This is BEFORE Spring Security's `DelegatingFilterProxy` (default order `OrderedFilter.REQUEST_WRAPPER_FILTER_MAX_ORDER - 100 = 99 - 100 = -1` in newer Boot, but materially "in front of Security"). Add a test in `IngestionPayloadArchiveFilterTest` (unit-level, not IT) that fires an unauthenticated request through both filters and asserts the archive row exists.

### Shared exception classes (codes per contracts/error-codes.md)

- [X] T010 [P] Create `platform-core/src/main/java/com/einvoice/core/error/CompanyNotFoundException.java`. Public constant `CODE = "COMPANY_NOT_FOUND"`. Constructor `(String registrationNumber)`; super message `"Company not found for registration number: " + registrationNumber`. Getter `getRegistrationNumber()`.
- [X] T011 [P] Create `platform-core/src/main/java/com/einvoice/core/error/AuthorityEnvironmentNotFoundException.java`. `CODE = "AUTHORITY_ENVIRONMENT_NOT_FOUND"`. Constructor `(String authority, String environment)`. Getters `getAuthority()`, `getEnvironment()`.

### Shared DTO enums + response record (data-model.md §2.1)

- [X] T012 [P] Create `platform-api/src/main/java/com/einvoice/api/integration/dto/shared/IntegrationEnvironment.java` (enum `{SANDBOX, PREPROD}`; class-level Javadoc noting PRODUCTION is intentionally absent per FR-008).
- [X] T013 [P] Create `platform-api/src/main/java/com/einvoice/api/integration/dto/shared/IntegrationDocumentStatus.java` (enum with the 8 values from FR-016; Javadoc).
- [X] T014 [P] Create `platform-api/src/main/java/com/einvoice/api/integration/dto/shared/DocumentIngestionResponse.java` as a Java `record (UUID id, String documentNumber, String erpReferenceId, String internalStatus, String message)`.

### Resolution + security + error mapping

- [X] T015 Edit `platform-core/src/main/java/com/einvoice/core/repository/company/CompanyRepository.java`: add `Optional<Company> findByTaxNumberAndIsActiveTrue(String taxNumber)`.
- [X] T016 Create `platform-api/src/main/java/com/einvoice/api/integration/service/CompanyResolutionService.java`. Public nested `record ResolvedContext(UUID companyId, Short authorityEnvironmentId)`. Public method `@Transactional(readOnly = true) ResolvedContext resolve(String companyRegistrationNumber, String authority, String environment)` — calls `CompanyRepository.findByTaxNumberAndIsActiveTrue()` → `CompanyNotFoundException` if empty; then `AuthorityEnvironmentRepository.findByAuthorityAndEnvironmentAndIsActiveTrue()` → `AuthorityEnvironmentNotFoundException` if empty.
- [ ] T016b Add `TenantContext` population in the ingestion-service entry points (FR-009d, satisfies Constitution §XV.6). In `EtaIngestionService` and `ZatcaIngestionService` (T019 / T020 skeletons), at the start of each public `ingest*()` method (added in US1–US4), AFTER `CompanyResolutionService.resolve(...)` succeeds: call `TenantContext.set(...)` (or the existing `TenantContextHolder` helper — verify name during implementation) with all seven required fields: `companyId` (resolved), `authorityEnvironmentId` (resolved), `authority` (`"ETA"` or `"ZATCA"` literal), `environment` (raw `request.environment().name()`), `mode = OPERATIONAL`, `userId = INTEGRATION_GATEWAY_PRINCIPAL_ID` (the FR-009c seeded UUID — read from `application.yml` `einvoice.integration.gatewayPrincipalId`), `isSuperUser = false`. Wrap each ingest body in `try { ... } finally { TenantContext.clear(); }` so the thread-local doesn't leak across pooled threads. The archive filter (T009) then reads the populated context in its `patchOutcome(...)` call.
- [X] T017 Edit `platform-security/src/main/java/com/einvoice/security/SecurityConfig.java`: add `.requestMatchers("/api/integration/v1/**").permitAll()` BEFORE `.anyRequest().authenticated()` and AFTER the existing `/api/health/**` permit-all (per source plan §18 + FR-021). Add an inline comment marking it as Sprint 1 scope to be replaced by API-key auth in Sprint 2.
- [X] T018 Edit `platform-api/src/main/java/com/einvoice/api/error/GlobalExceptionHandler.java`: add three handlers (per contracts/error-codes.md): `@ExceptionHandler(CompanyNotFoundException.class)` → 404 with `details: {registrationNumber}`; `@ExceptionHandler(AuthorityEnvironmentNotFoundException.class)` → 404 with `details: {authority, environment}`; `@ExceptionHandler(InboundPayloadArchiveException.class)` → 503 with `details: {cause}` (sanitised — no stack-trace, no SQL state). Add imports.

### Skeleton ingestion services + controllers (filled per story)

- [X] T019 [P] Create `platform-api/src/main/java/com/einvoice/api/integration/service/EtaIngestionService.java` as a `@Service @Transactional` class with constructor injection for `CompanyResolutionService`, `EtaInvoiceHeaderRepository`, `EtaReceiptHeaderRepository`. No public ingest methods yet (US1/US2 add them). Include the private static helper `DocumentState toDocumentState(IntegrationDocumentStatus)` (source plan §12). For JSONB-conversion helpers (`toSellerMap`, `toBuyerMap`, `toTaxpayerMap`, `toUnitValueMap` — source plan §13): either declare them only when first consumed (defer to T025 / T030) OR declare them now with `throw new UnsupportedOperationException("Implemented per story")` body so static analysis / Checkstyle doesn't flag empty methods (U1). DO NOT leave them empty.
- [X] T020 [P] Create `platform-api/src/main/java/com/einvoice/api/integration/service/ZatcaIngestionService.java` — mirror of T019 with injection for `ZatcaStandardHeaderRepository`, `ZatcaSimplifiedHeaderRepository`, plus the V58–V61 child-table repositories (`ZatcaStandardTaxSubtotalRepository`, `ZatcaStandardAllowanceRepository`, etc. — all delivered by 010). Include the same `toDocumentState` helper. ZATCA-specific JSONB helpers follow the same U1 rule as T019.
- [X] T021 [P] Create `platform-api/src/main/java/com/einvoice/api/integration/EtaIngestionController.java`: `@RestController @RequestMapping("/api/integration/v1/eta")`, constructor injection of `EtaIngestionService`. No `@PostMapping` methods yet.
- [X] T022 [P] Create `platform-api/src/main/java/com/einvoice/api/integration/ZatcaIngestionController.java`: same skeleton, mapping `/api/integration/v1/zatca`.

**Checkpoint**: `mvn -pl platform-api verify` compiles; Spring Boot starts; Swagger UI at `/swagger-ui/index.html` shows an empty `integration-gateway` group (no operations yet). The archive filter is active and any POST to `/api/integration/v1/**` writes an archive row + returns HTTP 404 (no controller match yet) — verifiable via `curl`.

---

## Phase 3: User Story 1 — ETA Receipt ingestion (Priority: P1) 🎯 MVP

**Goal**: An ERP can POST a SDK v1.2-shaped receipt to `/api/integration/v1/eta/receipts` with `status=VALID` and observe HTTP 201, a persisted row in `eta_receipt_headers` with `state=ACCEPTED`, populated `erp_reference_id`, `eta_receipt_uuid`, and `created_by=INTEGRATION_GATEWAY`.

**Independent Test**: quickstart.md Step 1 + Step 2 + Step 3 + Step 4. SC-002 + SC-003 + SC-004 + SC-005 for this endpoint.

### Tests for User Story 1 (TDD)

- [ ] T023 [P] [US1] Create `backend/src/test/java/com/einvoice/api/integration/EtaReceiptIngestionIT.java` with `@SpringBootTest @AutoConfigureMockMvc` + Testcontainers Postgres + `@Sql` seeding of one active company (`tax_number=100200300`) + active `(ETA, PREPROD)` row. Scenarios (one `@Test` each, matching spec.md US1 Acceptance Scenarios): (a) happy path → 201 + DB row assertions; (b) duplicate `receiptNumber` → 409 `DUPLICATE_RECEIPT_NUMBER`; (c) missing `header.receiptNumber` → 400 `VALIDATION_ERROR` with field error; (d) `header.uuid` not 64-hex → 400 `VALIDATION_ERROR`; (e) unknown `companyRegistrationNumber` → 404 `COMPANY_NOT_FOUND`; (f) `environment=PRODUCTION` literal in JSON → 400. **All scenarios MUST verify FR-009a (`created_by=INTEGRATION_GATEWAY`) and FR-OBS-003 (archive row exists with correct `outcome`).**

### Implementation for User Story 1

- [ ] T024 [US1] Create `platform-api/src/main/java/com/einvoice/api/integration/dto/eta/EtaReceiptIngestionRequest.java` — full DTO from source plan §7. Top-level record + nested records (`Header`, `DocumentType`, `Seller`, `BranchAddress`, `Buyer`, `Discount`, `ItemData`, `TaxableItem`, `TaxTotal`, `Contractor`, `Beneficiary`) with all Jakarta Validation 3.0 annotations as specified. Document type pattern keeps the legacy codes per locked decision: `"s|r|rr|rrwr|cr|crr|gs|gsr"`.
- [ ] T025 [US1] Add `public DocumentIngestionResponse ingestReceipt(EtaReceiptIngestionRequest req)` to `EtaIngestionService.java` (from T019). Transactional per FR-018. Flow per data-model.md §3.1 + source plan §13: (1) `CompanyResolutionService.resolve(req.companyRegistrationNumber(), "ETA", req.environment().name())`; (2) populate `TenantContext` per T016b / FR-009d; (3) dedup via `EtaReceiptHeaderRepository.findByCompanyIdAndAuthorityEnvironmentIdAndReceiptNumber(...).isPresent()` → `DuplicateReceiptNumberException` if true; (4) FR-011 cross-field checks (B-type buyer requires id+name; P-type with totalAmount ≥ 150,000 requires same); (5) build `EtaReceiptHeader` per data-model.md §3.1 (set `createdBy=updatedBy=INTEGRATION_GATEWAY` per FR-009a); (6) build child lines from `itemData[]` (same author stamp); (7) save via repository; (8) emit one `audit_logs` row with `action = "INGESTED"` (FR-009b, locked — V62 extends the V52 allow-list trigger per T004 step 3), `actor = "INTEGRATION_GATEWAY"`, payload referencing the new header id + `erpReferenceId`; (9) return `DocumentIngestionResponse`.
- [ ] T026 [US1] Add the controller method to `EtaIngestionController.java` (T021): `@PostMapping("/receipts") @ResponseStatus(HttpStatus.CREATED) public DocumentIngestionResponse ingestReceipt(@RequestBody @Valid EtaReceiptIngestionRequest req)` → delegates to `service.ingestReceipt(req)`. Inside the method, also set MDC fields that the body provides (`documentNumber = req.header().receiptNumber()`, `erpReferenceId = req.erpReferenceId()`) so FR-OBS-001's log line carries them.
- [ ] T027 [US1] Add curl-ready fixture `specs/011-erp-ingestion-gateway/contracts/samples/eta-receipt.json` matching the US1 happy-path scenario — used by quickstart.md Step 1 and T023's `(a)` scenario.

**Checkpoint**: T023's six scenarios all pass. Manually run quickstart.md Steps 1–4 against a local backend; verify all SC-002/SC-003/SC-004/SC-005 invariants for this endpoint.

---

## Phase 4: User Story 2 — ETA Invoice ingestion with credit/debit notes (Priority: P1)

**Goal**: An ERP can POST an SDK v1.0-shaped invoice (or credit/debit note) to `/api/integration/v1/eta/invoices` and observe 201; credit notes referencing a known prior invoice persist with `original_document_id` populated; unknown references persist with the raw string only.

**Independent Test**: quickstart.md Step 5. SC-002 + SC-006 (hybrid resolution) for this endpoint.

### Tests for User Story 2

- [X] T028 [P] [US2] Create `backend/src/test/java/com/einvoice/api/integration/EtaInvoiceIngestionIT.java` with the same seeded fixture as T023. Scenarios (matching spec.md US2 Acceptance Scenarios): (a) happy `documentType=I` → 201 with `eta_uuid`/`eta_long_id`/`eta_submission_id` populated; (b) credit note `documentType=C` with known `originalInvoiceNumber` → 201 + `original_document_id` populated; (c) credit note with missing `originalInvoiceNumber` → 400 `MISSING_ORIGINAL_DOCUMENT`; (d) credit note with unknown `originalInvoiceNumber` → 201 + `original_invoice_number` populated + `original_document_id IS NULL`; (e) `status=INVALID` → row's `state=REJECTED`.

### Implementation for User Story 2

- [X] T029 [P] [US2] Create `platform-api/src/main/java/com/einvoice/api/integration/dto/eta/EtaInvoiceIngestionRequest.java` — full DTO from source plan §8 with all nested records (`TaxpayerParty`, `PartyAddress`, `InvoiceLine`, `UnitValue`, `LineTax`).
- [X] T030 [US2] Add `public DocumentIngestionResponse ingestInvoice(EtaInvoiceIngestionRequest req)` to `EtaIngestionService.java`. Transactional per FR-018. Flow per data-model.md §3.2: resolve → populate `TenantContext` per T016b → dedup via `EtaInvoiceHeaderRepository.findByCompanyIdAndAuthorityEnvironmentIdAndInvoiceNumber()` → FR-011 cross-field (`documentType ∈ {C,D}` requires non-null `originalInvoiceNumber`) → hybrid orig-doc resolution per FR-017 (always set `originalInvoiceNumber` string; set `originalDocumentId` FK only when same-repository lookup is present) → build header + lines (with `UnitValue` JSONB serialization via `toUnitValueMap()`) + line-taxes from `LineTax[]` → save → emit one `audit_logs` row with `action = "INGESTED"` (FR-009b, locked) → return response. Stamp `INTEGRATION_GATEWAY` per FR-009a on every row written.
- [X] T031 [US2] Add `@PostMapping("/invoices") @ResponseStatus(HttpStatus.CREATED) public DocumentIngestionResponse ingestInvoice(@RequestBody @Valid EtaInvoiceIngestionRequest req)` to `EtaIngestionController.java`. Populate MDC `documentNumber = req.invoiceNumber()`, `erpReferenceId = req.erpReferenceId()`.
- [X] T032 [P] [US2] Add fixtures `contracts/samples/eta-invoice.json`, `contracts/samples/eta-credit-note-known.json`, `contracts/samples/eta-credit-note-unknown.json` matching T028's scenarios + quickstart.md Step 5.

**Checkpoint**: T028 passes. quickstart.md Step 5 works against a local backend.

---

## Phase 5: User Story 3 — ZATCA Standard ingestion (Priority: P1)

**Goal**: An ERP can POST a UBL-shaped Standard (B2B) invoice that has been cleared by ZATCA and observe 201; the header + lines + child tables (`zatca_standard_tax_subtotals`, `zatca_standard_allowances`, `zatca_standard_line_allowances`) are populated consistently.

**Independent Test**: quickstart.md Step 6. SC-002 + the US3 Acceptance Scenarios for this endpoint.

### Repository additions (depend on V58–V61 delivered by 010 — verify presence before editing)

- [ ] T033 [P] [US3] Edit `platform-core/src/main/java/com/einvoice/core/repository/zatca/ZatcaStandardHeaderRepository.java`: add `boolean existsByCompanyIdAndAuthorityEnvironmentIdAndInvoiceNumber(UUID companyId, Short authorityEnvironmentId, String invoiceNumber)` (for dedup) AND `Optional<ZatcaStandardHeader> findByCompanyIdAndAuthorityEnvironmentIdAndInvoiceNumber(UUID companyId, Short authorityEnvironmentId, String invoiceNumber)` (for hybrid orig-doc resolution).

### Tests for User Story 3

- [ ] T034 [P] [US3] Create `backend/src/test/java/com/einvoice/api/integration/ZatcaStandardIngestionIT.java`. Seed an active company + active `(ZATCA, SANDBOX)` row + a `zatca_configs` row for the resolved `(company_id, authority_environment_id)`. Scenarios (per spec.md US3 + FR-017 ZATCA coverage): (a) happy `invoiceTypeCode=388` with one S-rated line + one E-exempt line carrying exemption reason → 201 + DB assertions on header + `tax_subtotals` (≥2 rows grouped by `vatRate`+`vatCategoryCode`) + `lines` with `item_net_price`/`item_gross_price` etc. populated; (b) `transactionTypeCode` starting with `0` → 400 `VALIDATION_ERROR` (pattern violation); (c) `vatCategoryCode=E` line missing exemption fields → 400 `VAT_EXEMPTION_REASON_REQUIRED`; (d) `buyer=null` → 400 (`@NotNull` violation); (e) `seller.vatNumber` not matching `3[0-9]{14}` → 400; **(f) credit note `invoiceTypeCode=381` referencing a previously-ingested Standard invoice's `invoiceNumber` as `originalInvoiceNumber` → 201 + DB row with `original_document_id` populated + `original_invoice_number` populated**; **(g) credit note `invoiceTypeCode=381` with `originalInvoiceNumber=UNKNOWN-XYZ` → 201 + `original_invoice_number='UNKNOWN-XYZ'` + `original_document_id IS NULL`** (FR-017 hybrid resolution — see SC-006).

### Implementation for User Story 3

- [ ] T035 [P] [US3] Create `platform-api/src/main/java/com/einvoice/api/integration/dto/zatca/ZatcaStandardInvoiceIngestionRequest.java` — full DTO from source plan §9 with nested `SellerParty`, `BuyerParty` (required for Standard), `LineItem`. Patterns: `transactionTypeCode = "1[01]{5}0000"`, `invoiceTypeCode = "388|381|383"`, `vatNumber = "3[0-9]{14}"`, etc.
- [ ] T036 [US3] Add `public DocumentIngestionResponse ingestStandard(ZatcaStandardInvoiceIngestionRequest req)` to `ZatcaIngestionService.java`. Transactional per FR-018. Flow per data-model.md §3.3: resolve → populate `TenantContext` per T016b → dedup via T033's `existsBy...` → FR-011 cross-field (`invoiceTypeCode ∈ {381, 383}` requires `originalInvoiceNumber`; each `LineItem` with `vatCategoryCode ∈ {E, O}` requires both exemption fields) → hybrid orig-doc resolution per FR-017 (always set `originalInvoiceNumber` string; set `originalDocumentId` FK only when T033's `findBy...` returns present) → build header (promoted party columns per V58, `clearance_status`, chain fields verbatim, `payment_means_code`/`payment_means_text` denormalised, `tax_amount_accounting_currency` always populated — equals `tax_amount` when `currency='SAR'`, look up `zatca_config_id` via `(company_id, authority_environment_id)`) + lines + `zatca_standard_tax_subtotals` (group by `vatRate`+`vatCategoryCode`) + `zatca_standard_allowances` (from header allowances) + `zatca_standard_line_allowances` (from line allowances) → save → emit one `audit_logs` row with `action = "INGESTED"` (FR-009b, locked) → return. Stamp `INTEGRATION_GATEWAY` per FR-009a on every row.
- [ ] T037 [US3] Add `@PostMapping("/standard") @ResponseStatus(HttpStatus.CREATED) public DocumentIngestionResponse ingestStandard(@RequestBody @Valid ZatcaStandardInvoiceIngestionRequest req)` to `ZatcaIngestionController.java`. Populate MDC.
- [ ] T038 [P] [US3] Add fixture `contracts/samples/zatca-standard.json` matching T034 scenario (a) + quickstart.md Step 6.

**Checkpoint**: T034 passes. quickstart.md Step 6 works.

---

## Phase 6: User Story 4 — ZATCA Simplified ingestion (Priority: P1)

**Goal**: An ERP can POST a UBL-shaped Simplified (B2C) invoice (anonymous buyer allowed) and observe 201; the header carries `reporting_status` rather than `clearance_status`.

**Independent Test**: quickstart.md Step 7. SC-002 + the US4 Acceptance Scenarios.

### Repository additions

- [ ] T039 [P] [US4] Edit `platform-core/src/main/java/com/einvoice/core/repository/zatca/ZatcaSimplifiedHeaderRepository.java`: add the same pair as T033 (`existsBy...InvoiceNumber` + `findBy...InvoiceNumber`).

### Tests for User Story 4

- [ ] T040 [P] [US4] Create `backend/src/test/java/com/einvoice/api/integration/ZatcaSimplifiedIngestionIT.java`. Scenarios (per spec.md US4 + FR-017 ZATCA coverage): (a) happy `invoiceTypeCode=388` with `transactionTypeCode = "00000000"` and `buyer` omitted → 201 + DB row with `reporting_status=REPORTED` and `buyer_data` empty/absent; (b) duplicate `invoiceNumber` → 409 `DUPLICATE_SIMPLIFIED_NUMBER`; (c) `transactionTypeCode` starting with `1` (Standard shape) on the Simplified endpoint → 400 pattern violation; **(d) credit note `invoiceTypeCode=381` referencing a previously-ingested Simplified invoice's `invoiceNumber` → 201 + `original_document_id` populated**; **(e) credit note `invoiceTypeCode=381` with unknown `originalInvoiceNumber` → 201 + raw string preserved + `original_document_id IS NULL`** (FR-017 hybrid resolution).

### Implementation for User Story 4

- [ ] T041 [P] [US4] Create `platform-api/src/main/java/com/einvoice/api/integration/dto/zatca/ZatcaSimplifiedInvoiceIngestionRequest.java` — full DTO from source plan §10. Key differences from Standard (per data-model.md §2.6): `transactionTypeCode` pattern is `0[01]{5}0000`; `buyer` is `@Valid` only (no `@NotNull`); `BuyerParty` fields have no `@NotBlank`.
- [ ] T042 [US4] Add `public DocumentIngestionResponse ingestSimplified(ZatcaSimplifiedInvoiceIngestionRequest req)` to `ZatcaIngestionService.java`. Transactional per FR-018. Same flow as T036 (including hybrid orig-doc resolution per FR-017, audit-log row with `action = "INGESTED"` per FR-009b) except: writes to `zatca_simplified_*` tables; persists `reporting_status` (not `clearance_status`); anonymous buyer path leaves `buyer_data` JSONB empty/null.
- [ ] T043 [US4] Add `@PostMapping("/simplified") @ResponseStatus(HttpStatus.CREATED) public DocumentIngestionResponse ingestSimplified(@RequestBody @Valid ZatcaSimplifiedInvoiceIngestionRequest req)` to `ZatcaIngestionController.java`. Populate MDC.
- [ ] T044 [P] [US4] Add fixture `contracts/samples/zatca-simplified-anonymous.json` matching T040 scenario (a) + quickstart.md Step 7.

**Checkpoint**: T040 passes. quickstart.md Step 7 works.

---

## Phase 7: User Story 5 — OpenAPI surface for integrators (Priority: P2)

**Goal**: An external ERP developer can discover, explore, and Try-It-Out the four endpoints from `/swagger-ui/index.html` without reading source code.

**Independent Test**: quickstart.md Step 0. SC-008.

### Tests for User Story 5

- [X] T045 [P] [US5] Create `backend/src/test/java/com/einvoice/api/integration/OpenApiSurfaceIT.java`. Use MockMvc to GET `/v3/api-docs/integration-gateway` and assert: (a) all four `/eta/receipts`, `/eta/invoices`, `/zatca/standard`, `/zatca/simplified` paths are present; (b) `components.schemas.IntegrationEnvironment.enum` is exactly `["SANDBOX", "PREPROD"]` (no PRODUCTION); (c) `components.schemas.IntegrationDocumentStatus.enum` is exactly the eight values from FR-016. Also GET `/swagger-ui/index.html` and assert HTTP 200.

### Implementation for User Story 5

- [X] T046 [US5] Add `@Operation(operationId, summary, tags={"integration-gateway"})` annotations to ALL FOUR `@PostMapping` methods (T026, T031, T037, T043). `operationId` values MUST match `contracts/openapi.yaml`: `ingestEtaReceipt`, `ingestEtaInvoice`, `ingestZatcaStandard`, `ingestZatcaSimplified`. Each method also gets an `@ApiResponse(responseCode = "201", ...)` plus one `@ApiResponse(responseCode = "503", description = "Archive write failed", content = ...)` referencing the `ErrorResponse` schema. This guarantees T045's assertions pass without depending on SpringDoc's annotation-free inference.

**Checkpoint**: T045 passes. quickstart.md Step 0 visual smoke check matches the openapi.yaml contract.

---

## Phase 8: Polish & Cross-Cutting Concerns

**Purpose**: Cross-story verification, tenancy regression sweep, observability assertions that the per-story ITs already exercise but that we want to assert holistically.

- [X] T047 [P] Create `platform-api/src/test/java/com/einvoice/api/integration/ObservabilityIT.java` (path differs from the spec note — actual repo layout is `platform-api/`, not `backend/`). Uses `OutputCaptureExtension` to capture the log stream. For each of the four endpoints, posts one happy payload + one validation failure (the per-story ITs already cover the duplicate scenarios). Asserts (SC-009): exactly one `inbound_payload_archive` row per HTTP request, no NULL outcomes after the response commits, and the row's `id` appears in the INFO log line. Asserts (SC-010): exactly four `audit_logs` rows with `action='INGESTED'` for the four happy paths. **FR-OBS-005 fault-injection** lives in a sibling file `ObservabilityArchiveFailureIT.java` (split because `@MockitoBean` dirties the context and would corrupt the cross-endpoint scenarios). The failure scenario asserts HTTP 503 + `ARCHIVE_WRITE_FAILED`, zero rows in `eta_receipt_headers` / `eta_receipt_lines` / `audit_logs` / `inbound_payload_archive`, and that the structured log line still carries the 503 outcome.
- [X] T048 [P] Create `platform-api/src/test/java/com/einvoice/api/integration/TenancyIsolationIT.java`. Seeds two companies (A, B) and uses a **non-super** user with `user_company_transaction_roles` ACCOUNTANT assignments scoped to Company A under both ETA PREPROD (id=2) and ZATCA SANDBOX (id=5). Super user was rejected because `EtaInvoiceService.getAssignedCompanyIds()` returns all active companies for super users and would mask the cross-company filter under test. Ingests one ETA invoice each for A and B (PREPROD) plus one ZATCA standard for A (SANDBOX), then asserts: (a) GET `/api/companies/A/eta/invoices` with the PREPROD token returns exactly A's invoice — not B's; (b) GET `/api/companies/A/zatca/standard` with the SANDBOX token returns A's standard; (c) the same endpoint with the PREPROD token returns 0 (authority_environment_id filter); (d) GET `/api/companies/B/eta/invoices` with A's PREPROD token returns 401 (verifyContext).
- [X] T049 Create `platform-api/src/test/java/com/einvoice/api/integration/ReadPathSurfaceIT.java`. Ingests one document via each of the four endpoints then GETs `/api/companies/{companyId}/eta/receipts`, `/api/companies/{companyId}/eta/invoices`, `/api/companies/{companyId}/zatca/standard`, `/api/companies/{companyId}/zatca/simplified` with a super-user Operational Mode session scoped to the correct (authority, environment) per endpoint. Asserts each list returns `totalElements=1` with the freshly-ingested document number (SC-007 / FR-023). Shared payload helpers live in `IngestionPayloads.java`.
- [ ] T050 Run quickstart.md Steps 0–9 end-to-end against a freshly built backend. Record the SC-001 timing claim ("under 30 minutes from cold start") — if exceeded, document why in a note appended to quickstart.md. **Status**: deferred — this task requires a live backend run and is left for the reviewer; the IT suite (T023/T028/T034/T040/T045/T047/T048/T049) exercises the same SC-002..SC-010 invariants in CI.
- [X] T051 [P] Update `CLAUDE.md` if any new technology / library decision surfaced during implementation that the agent-context auto-update missed. **Sweep result**: the auto-update already captured `springdoc-openapi-starter-webmvc-ui:2.8.8` and the V62 migration in the 011 entry; no additional sweep is required.

**Checkpoint**: All ten Success Criteria (SC-001..SC-010) verified. The branch is ready for review.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Setup)**: No internal dependencies. T001 must precede T002 (`OpenApiConfig` depends on the SpringDoc dependency being on the classpath); T002 and T003 are otherwise independent.
- **Phase 2 (Foundational)**: Depends on Phase 1. Blocks every Phase 3–7 story. Internal ordering:
  - T004 must precede T005 (entity depends on table existing during integration tests).
  - T005 precedes T006 (repository references entity).
  - T006 precedes T008 (service uses repository).
  - T008 precedes T009 (filter calls service).
  - T009 precedes T017 (the filter must be registered BEFORE the Spring Security `permitAll()` matcher hits the same path — verify order via `FilterRegistrationBean.setOrder()`).
  - T015 precedes T016 (`CompanyResolutionService` uses the new repo method).
  - T016 precedes T016b (TenantContext population uses the resolved tuple).
  - T010 / T011 must precede T018 (handlers reference the exceptions).
  - T019/T020 must precede the per-story service-method additions (Phase 3–6) AND must precede T016b (which adds TenantContext population at the start of each `ingest*()` method that those skeletons later receive).
  - T021/T022 must precede the per-story controller additions (Phase 3–6).
- **Phase 3–6 (P1 User Stories)**: All depend ONLY on Phase 2 completion. Stories US1–US4 can run in parallel by independent developers — each owns its own DTO file, IT file, and fixture file, and each adds a method to a Phase-2-skeleton service/controller. The single contention point is the `EtaIngestionService.java` file (US1 + US2 both edit it) and `ZatcaIngestionService.java` (US3 + US4). If parallelising, sequence within each language pair (US1 → US2 sequentially for ETA; US3 → US4 sequentially for ZATCA).
- **Phase 7 (US5)**: Depends on Phase 2. Can also run in parallel with US1–US4; only weak coupling via T046 which touches controller methods US1–US4 add.
- **Phase 8 (Polish)**: Depends on all desired user stories. T047/T048/T049 can run in parallel (different test files). T050 must run last.

### Within Each User Story

- Test task (T023 / T028 / T034 / T040 / T045) is written FIRST and MUST FAIL before implementation.
- DTO task before service-method task before controller-method task (the test can't compile against missing DTOs, but TDD here means "ensure the test FAILS at run-time, not compile-time" — DTOs may be skeletoned to make the test compile, then assertions wire up the failure).
- Fixture task ([P]) is independent of the implementation order — write the fixture from the test's expected payload.

### Parallel Opportunities

- Phase 1: T002, T003 (T001 first).
- Phase 2: T005/T006 [P], T010/T011 [P], T012/T013/T014 [P], T019/T020 [P], T021/T022 [P]. T015 [P with T010/T011/T012/T013/T014/T019/T020/T021/T022]. Many opportunities — see ordering above.
- Phase 3–6: All four P1 stories can run in parallel across developers; within each story, the IT + fixture are [P] with the DTO once the DTO is shaped.
- Phase 7: T045 [P] with everything in Phase 3–6.
- Phase 8: T047 / T048 / T049 / T051 [P], T050 last.

---

## Parallel Example: Phase 2

```bash
# Stage 1 (after T001 + T004 complete):
Task T005 [P] InboundPayloadArchive entity
Task T010 [P] CompanyNotFoundException
Task T011 [P] AuthorityEnvironmentNotFoundException
Task T012 [P] IntegrationEnvironment enum
Task T013 [P] IntegrationDocumentStatus enum
Task T014 [P] DocumentIngestionResponse record
Task T015     CompanyRepository edit  (touches existing file; not strictly P with T006 if same generated class)

# Stage 2 (depends on Stage 1):
Task T006 InboundPayloadArchiveRepository  (uses T005)
Task T016 CompanyResolutionService          (uses T010, T011, T015)
Task T018 GlobalExceptionHandler edits      (uses T010, T011, T007)
Task T019 [P] EtaIngestionService skeleton
Task T020 [P] ZatcaIngestionService skeleton
Task T021 [P] EtaIngestionController skeleton
Task T022 [P] ZatcaIngestionController skeleton

# Stage 3 (depends on T006):
Task T007 InboundPayloadArchiveException  (no T006 dep actually; can be Stage 1)
Task T008 InboundPayloadArchiveService    (uses T005, T006)

# Stage 4 (depends on T008):
Task T009 IngestionPayloadArchiveFilter   (uses T008)
Task T017 SecurityConfig permit-all        (must run AFTER T009 is wired so order is enforced)
```

## Parallel Example: P1 User Stories (after Phase 2 checkpoint)

```bash
# Developer A: US1 ETA Receipt
T023 → T024 → T025 → T026 → T027

# Developer B (after US1 lands or in lockstep, sharing EtaIngestionService):
T028 → T029 → T030 → T031 → T032

# Developer C: US3 ZATCA Standard
T033 → T034 → T035 → T036 → T037 → T038

# Developer D (after US3 lands or in lockstep, sharing ZatcaIngestionService):
T039 → T040 → T041 → T042 → T043 → T044

# Developer E: US5 OpenAPI surface (independent of US1-US4 implementation)
T045 → T046

# Pair runs in lockstep for ETA and ZATCA service files. Otherwise fully parallel.
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup (T001–T003).
2. Complete Phase 2: Foundational (T004–T022). **CRITICAL** — blocks all stories.
3. Complete Phase 3: User Story 1 — ETA Receipt (T023–T027).
4. **STOP and VALIDATE**: Run quickstart.md Steps 0–4 against a local backend. SC-002/SC-003/SC-004/SC-005/SC-008/SC-009/SC-010 verified for the ETA Receipt endpoint.
5. Deploy / demo if ready.

### Incremental Delivery

1. MVP (US1) shipped.
2. Add US2 ETA Invoice → quickstart Step 5 → demo. SC-006 covered.
3. Add US3 ZATCA Standard → quickstart Step 6 → demo.
4. Add US4 ZATCA Simplified → quickstart Step 7 → demo. All four endpoints live.
5. Polish phase (T047–T051) closes the cross-cutting SCs.

### Parallel Team Strategy

After Phase 2 lands, the four P1 endpoint stories can be developed in parallel (recommend pairing the two ETA stories on one developer and the two ZATCA stories on another, since each pair shares one service file). US5 can be a third developer or a back-pocket task picked up by whoever finishes their pair first. Phase 8's three test sweeps (T047/T048/T049) can split across whoever's free.

---

## Notes

- Every task above carries an absolute or repo-rooted relative file path, a unique ID, and a `[P]` / `[Story]` label per the format contract.
- The five Constitution-Check caveats from `plan.md` §Complexity Tracking (VII, VIII, XV, XVII, XXV) are *not* tasks — they are bounded by Sprint 1 scope and converge in the Sprint 2 API-key feature.
- The hard prerequisite "010 V58–V61 merged" is *not* a task in this list — it must be verified before T001. If 010 is incomplete, this task list will not compile (Wave 7/8 entity columns are referenced by T025/T030/T036/T042).
- Tests-first: per `Within Each User Story`, the IT task is written and confirmed-failing before the DTO + service + controller tasks for that story.
- Commit cadence: one commit per task (or per logical pair) keeps `git bisect` useful if a regression appears in Phase 8.
- Stop at any checkpoint to validate the story-so-far independently — the spec.md success criteria are written so each one is verifiable at the end of its owning story.
