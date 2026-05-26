c# Tasks: Authority Engines, Submission & Invoice Smart Form

**Input**: Design documents from `/specs/003-authority-engines-submission/`
**Prerequisites**: plan.md (required), spec.md (required), research.md, data-model.md, contracts/

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

---

## Phase 1: Setup (Schema & Domain Entities)

**Purpose**: Flyway migrations for new tables and extended enums, plus new JPA entities

- [ ] T001 Create Flyway migration V12__extend_invoice_statuses.sql to add new InvoiceStatus enum values (VALIDATED, READY_FOR_SUBMISSION, SUBMISSION_IN_PROGRESS, CLEARED, REPORTED, ACCEPTED, IN_REVIEW, REJECTED, FAILED_RETRYABLE, FAILED_NON_RETRYABLE, SUBMISSION_AMBIGUOUS) in platform-core/src/main/resources/db/migration/V12__extend_invoice_statuses.sql
- [ ] T002 [P] Create Flyway migration V13__create_submission_attempts.sql with submission_attempts table per data-model.md in platform-core/src/main/resources/db/migration/V13__create_submission_attempts.sql
- [ ] T003 [P] Create Flyway migration V14__create_invoice_artifacts.sql with invoice_artifacts table per data-model.md in platform-core/src/main/resources/db/migration/V14__create_invoice_artifacts.sql
- [ ] T004 [P] Create Flyway migration V15__create_eta_item_codes.sql with eta_item_codes table per data-model.md in platform-core/src/main/resources/db/migration/V15__create_eta_item_codes.sql
- [ ] T005 [P] Create Flyway migration V16__create_onboarding_progress.sql with onboarding_progress table and add onboarding_status and polling_enabled columns to authority_configs in platform-core/src/main/resources/db/migration/V16__create_onboarding_progress.sql
- [ ] T006 [P] Create Flyway migration V17__add_external_invoice_ref.sql adding external_invoice_reference column to invoices table in platform-core/src/main/resources/db/migration/V17__add_external_invoice_ref.sql
- [ ] T006a [P] Add ZXing (com.google.zxing:core + com.google.zxing:javase) dependency to platform-zatca/pom.xml
- [ ] T007 Extend InvoiceStatus enum with all new status values in platform-core/src/main/java/com/einvoice/core/domain/enums/InvoiceStatus.java
- [ ] T008 [P] Create SubmissionResult enum (SUCCESS, REJECTED, ERROR, TIMEOUT, AMBIGUOUS) in platform-core/src/main/java/com/einvoice/core/domain/enums/SubmissionResult.java
- [ ] T009 [P] Create ArtifactType enum (SIGNED_XML, SIGNED_JSON, QR_CODE, CLEARED_XML, ETA_RESPONSE, ZATCA_RESPONSE, ETA_PDF) in platform-core/src/main/java/com/einvoice/core/domain/enums/ArtifactType.java
- [ ] T010 [P] Create OnboardingStep enum (NOT_STARTED, CSR_GENERATED, COMPLIANCE_CSID_OBTAINED, TEST_INVOICES_SUBMITTED, PRODUCTION_CSID_OBTAINED) in platform-core/src/main/java/com/einvoice/core/domain/enums/OnboardingStep.java
- [ ] T011 [P] Create SubmissionAttempt JPA entity with all fields per data-model.md in platform-core/src/main/java/com/einvoice/core/domain/SubmissionAttempt.java
- [ ] T012 [P] Create InvoiceArtifact JPA entity (immutable, no update methods) with content_hash field in platform-core/src/main/java/com/einvoice/core/domain/InvoiceArtifact.java
- [ ] T013 [P] Create EtaItemCode JPA entity with company_id tenant ownership in platform-core/src/main/java/com/einvoice/core/domain/EtaItemCode.java
- [ ] T014 [P] Create OnboardingProgress JPA entity with step_data JSONB and resume support in platform-core/src/main/java/com/einvoice/core/domain/OnboardingProgress.java
- [ ] T015 Add external_invoice_reference field to Invoice entity in platform-core/src/main/java/com/einvoice/core/domain/Invoice.java
- [ ] T016 [P] Create SubmissionAttemptRepository (append-only, no delete/update methods) in platform-core/src/main/java/com/einvoice/core/repository/SubmissionAttemptRepository.java
- [ ] T017 [P] Create InvoiceArtifactRepository (insert-only, find by invoice+type) in platform-core/src/main/java/com/einvoice/core/repository/InvoiceArtifactRepository.java
- [ ] T018 [P] Create EtaItemCodeRepository with tenant-scoped queries in platform-core/src/main/java/com/einvoice/core/repository/EtaItemCodeRepository.java
- [ ] T019 [P] Create OnboardingProgressRepository with findByBranchAndAuthorityAndEnvironment in platform-core/src/main/java/com/einvoice/core/repository/OnboardingProgressRepository.java
- [ ] T020 Add onboardingStatus and pollingEnabled fields to AuthorityConfig entity in platform-core/src/main/java/com/einvoice/core/domain/AuthorityConfig.java

---

## Phase 2: Foundational (Core Infrastructure)

**Purpose**: State machine, validation framework, authority engine abstraction, and submission orchestrator skeleton that all user stories depend on

**CRITICAL**: No user story work can begin until this phase is complete

- [x] T021 Create InvoiceStateMachine with allowed transitions map and transition() method that validates and creates audit log entry in platform-core/src/main/java/com/einvoice/core/service/InvoiceStateMachine.java
- [x] T022 Create InvalidTransitionException in platform-core/src/main/java/com/einvoice/core/exception/InvalidTransitionException.java
- [x] T023 Create ValidationRule interface with validate(Invoice, Authority) returning List<ValidationError> in platform-core/src/main/java/com/einvoice/core/service/validation/ValidationRule.java
- [x] T024 Create ValidationError record with layer, authority, ruleId, field, message, severity fields in platform-core/src/main/java/com/einvoice/core/service/validation/ValidationError.java
- [x] T025 Create ValidationLayer enum (STRUCTURAL, ARITHMETIC, COMPLIANCE, READINESS) in platform-core/src/main/java/com/einvoice/core/service/validation/ValidationLayer.java
- [x] T026 Create ValidationSeverity enum (ERROR, WARNING) in platform-core/src/main/java/com/einvoice/core/service/validation/ValidationSeverity.java
- [x] T027 Create ValidationService orchestrator that runs all rules grouped by layer and returns errors+warnings in platform-core/src/main/java/com/einvoice/core/service/ValidationService.java
- [x] T028 [P] Create StructuralValidationRules (STRUCT-001 through STRUCT-004): required fields, at least one line, buyer B2B VAT, credit/debit note reference (internal or external) in platform-core/src/main/java/com/einvoice/core/service/validation/StructuralValidationRules.java
- [x] T029 [P] Create ArithmeticValidationRules (ARITH-001 through ARITH-005): line net, line VAT, document totals, VAT breakdown match, rounding in platform-core/src/main/java/com/einvoice/core/service/validation/ArithmeticValidationRules.java
- [x] T030 [P] Create SubmissionReadinessRules (READY-001 through READY-003): authority config active, credentials not expired, sequence valid in platform-core/src/main/java/com/einvoice/core/service/validation/SubmissionReadinessRules.java
- [x] T031 Create AuthorityEngine interface with generatePayload(), submit(), normalizeResponse() methods in platform-core/src/main/java/com/einvoice/core/service/AuthorityEngine.java
- [x] T032 Create SubmissionResultDto record with status, warnings, errors fields for normalized authority responses in platform-core/src/main/java/com/einvoice/core/service/SubmissionResultDto.java
- [x] T033 Create AuthorityEngineFactory that resolves engine by Authority enum in platform-core/src/main/java/com/einvoice/core/service/AuthorityEngineFactory.java
- [x] T034 Create SubmissionOrchestrator implementing the 14-step submit flow per plan.md (load, validate state, transition, create attempt, resolve engine, generate payload, persist artifact, submit, persist response, normalize, update state, update attempt, update chain if ZATCA, audit log) in platform-core/src/main/java/com/einvoice/core/service/SubmissionOrchestrator.java
- [x] T035 Create InvoiceArtifactService for storing artifacts with SHA-256 content hash computation in platform-core/src/main/java/com/einvoice/core/service/InvoiceArtifactService.java
- [x] T036 Create SubmissionAttemptService for creating and updating submission attempt records in platform-core/src/main/java/com/einvoice/core/service/SubmissionAttemptService.java
- [x] T037 Update GlobalExceptionHandler to handle InvalidTransitionException (409), OptimisticLockException (409), and authority-specific errors in platform-api/src/main/java/com/einvoice/api/config/GlobalExceptionHandler.java
- [x] T038 Create InvoiceStateMachineTest covering every valid transition succeeds and every invalid transition throws in platform-core/src/test/java/com/einvoice/core/service/InvoiceStateMachineTest.java

**Checkpoint**: Foundation ready — state machine, validation pipeline, authority engine interface, and submission orchestrator are operational. User story implementation can begin.

---

## Phase 3: User Story 1 — Submit a ZATCA Tax Invoice End-to-End (Priority: P1) MVP

**Goal**: An accountant can create a ZATCA standard tax invoice, submit it for clearance, and receive CLEARED status with downloadable signed XML artifacts.

**Independent Test**: Create a draft invoice for a ZATCA-configured branch, submit to ZATCA sandbox, verify CLEARED status and stored SIGNED_XML + CLEARED_XML artifacts.

### Implementation for User Story 1

- [x] T039 [P] [US1] Create ZatcaUblBuilder that maps Invoice domain entity to UBL 2.1 XML Document with all mandatory ZATCA elements (seller, buyer, delivery, payment means, tax total, monetary total, line items) in platform-zatca/src/main/java/com/einvoice/zatca/xml/ZatcaUblBuilder.java
- [x] T040 [P] [US1] Create ZatcaHashService implementing SHA-256 hash of canonicalized XML (Exclusive C14N), base64 encoding, and previous invoice hash chain management with seed hash constant in platform-zatca/src/main/java/com/einvoice/zatca/hash/ZatcaHashService.java
- [x] T041 [P] [US1] Create ZatcaSigningService using xades4j XadesBesSigningProfile for enveloped XAdES-BES signatures with certificate loading from EncryptionService in platform-zatca/src/main/java/com/einvoice/zatca/signing/ZatcaSigningService.java
- [x] T042 [P] [US1] Create ZatcaQrService with TLV encoding for Tags 1-8 (seller name, VAT, timestamp, total with VAT, VAT amount, hash, signature, public key) and base64 output in platform-zatca/src/main/java/com/einvoice/zatca/qr/ZatcaQrService.java
- [x] T043 [US1] Create ZatcaClearanceClient for POST /invoices/clearance/single with Basic auth (CSID:secret), Accept-Version V2, base64 XML body, and response parsing in platform-zatca/src/main/java/com/einvoice/zatca/client/ZatcaClearanceClient.java
- [x] T044 [P] [US1] Create ZatcaComplianceRules implementing ZATCA-specific BR-KSA validation rules (ZATCA-001 through ZATCA-006) as ValidationRule implementations in platform-zatca/src/main/java/com/einvoice/zatca/validation/ZatcaComplianceRules.java
- [x] T045 [US1] Create ZatcaAuthorityEngine implementing AuthorityEngine interface, orchestrating: UBL build -> hash chain -> QR -> sign -> submit via clearance client -> normalize response in platform-zatca/src/main/java/com/einvoice/zatca/ZatcaAuthorityEngine.java
- [x] T046 [US1] Register ZatcaAuthorityEngine and ZatcaComplianceRules in AuthorityEngineFactory and ValidationService via Spring configuration in platform-zatca/src/main/java/com/einvoice/zatca/ZatcaConfig.java
- [x] T047 [US1] Add pessimistic lock query on authority_configs row for hash chain update in AuthorityConfigRepository in platform-core/src/main/java/com/einvoice/core/repository/AuthorityConfigRepository.java
- [x] T048 [US1] Create InvoiceSubmissionController with POST /api/invoices/{id}/validate, POST /api/invoices/{id}/confirm-submission, POST /api/invoices/{id}/submit endpoints per contracts/submission-api.md in platform-api/src/main/java/com/einvoice/api/invoice/InvoiceSubmissionController.java
- [x] T049 [P] [US1] Create SubmitResponse, SubmissionAttemptResponse, and ValidationResultResponse DTOs in platform-api/src/main/java/com/einvoice/api/invoice/dto/
- [x] T050 [US1] Create GET /api/invoices/{id}/submissions endpoint returning submission timeline in InvoiceSubmissionController in platform-api/src/main/java/com/einvoice/api/invoice/InvoiceSubmissionController.java
- [x] T051 [US1] Create GET /api/invoices/{id}/artifacts and GET /api/invoices/{id}/artifacts/{type} endpoints for artifact listing and download per contracts/artifact-api.md in platform-api/src/main/java/com/einvoice/api/invoice/InvoiceArtifactController.java
- [x] T052 [P] [US1] Create ArtifactResponse and ArtifactListResponse DTOs in platform-api/src/main/java/com/einvoice/api/invoice/dto/ArtifactResponse.java
- [x] T053 [P] [US1] Create golden-file test for ZatcaUblBuilder: known invoice input -> expected XML output (verify structure, namespaces, element content) in platform-zatca/src/test/java/com/einvoice/zatca/xml/ZatcaUblBuilderGoldenFileTest.java
- [x] T054 [P] [US1] Create golden-file test for ZatcaQrService: known invoice data -> expected TLV bytes in platform-zatca/src/test/java/com/einvoice/zatca/qr/ZatcaQrServiceGoldenFileTest.java
- [x] T055 [P] [US1] Create ZatcaHashServiceTest verifying hash computation, chain linkage, and seed hash for first invoice in platform-zatca/src/test/java/com/einvoice/zatca/hash/ZatcaHashServiceTest.java
- [x] T056 [P] [US1] Create ZatcaSigningServiceTest verifying signature structure (enveloped, XAdES-BES, certificate embedded) in platform-zatca/src/test/java/com/einvoice/zatca/signing/ZatcaSigningServiceTest.java
- [x] T057 [US1] Create golden-file test resource files (expected XML, expected TLV bytes) in platform-zatca/src/test/resources/golden-files/

**Checkpoint**: ZATCA standard tax invoice can be submitted and cleared via sandbox. End-to-end flow DRAFT -> VALIDATED -> READY_FOR_SUBMISSION -> SUBMISSION_IN_PROGRESS -> CLEARED is operational.

---

## Phase 4: User Story 2 — Submit a ZATCA Simplified Invoice for Reporting (Priority: P1)

**Goal**: Accountant creates a simplified tax invoice and submits for ZATCA reporting. QR code with scannable data is generated and stored.

**Independent Test**: Create a simplified invoice, submit to ZATCA sandbox reporting endpoint, verify REPORTED status and QR_CODE artifact.

**Dependencies**: Shares ZATCA engine infrastructure from US1 (ZatcaUblBuilder, signing, hash). Only needs ZatcaReportingClient addition.

### Implementation for User Story 2

- [x] T058 [US2] Create ZatcaReportingClient for POST /invoices/reporting/single with same auth pattern as clearance client in platform-zatca/src/main/java/com/einvoice/zatca/client/ZatcaReportingClient.java
- [x] T059 [US2] Update ZatcaAuthorityEngine to route Tax Invoices to clearance and Simplified Invoices to reporting based on invoice type in platform-zatca/src/main/java/com/einvoice/zatca/ZatcaAuthorityEngine.java
- [x] T060 [US2] Add QR code image generation via ZXing QRCodeWriter in ZatcaQrService (PNG byte output for future PDF use) in platform-zatca/src/main/java/com/einvoice/zatca/qr/ZatcaQrService.java
- [x] T061 [US2] Create golden-file test for simplified invoice XML output (different type code 386, ProfileID subtype) in platform-zatca/src/test/java/com/einvoice/zatca/xml/ZatcaUblBuilderSimplifiedGoldenFileTest.java

**Checkpoint**: Both ZATCA clearance (US1) and reporting (US2) flows are operational. All ZATCA invoice types can be submitted.

---

## Phase 5: User Story 3 — Submit an ETA Invoice End-to-End (Priority: P1)

**Goal**: Accountant creates an invoice for an Egyptian company, submits to ETA, sees ACCEPTED status, and can download the ETA-generated PDF.

**Independent Test**: Create an invoice for an ETA-configured branch, submit to ETA pre-production, verify ACCEPTED status and SIGNED_JSON artifact.

### Implementation for User Story 3

- [x] T062 [P] [US3] Create EtaTokenManager with ConcurrentHashMap cache keyed by (branch_id, environment), auto-refresh 60s before expiry, thread-safe token acquisition in platform-eta/src/main/java/com/einvoice/eta/auth/EtaTokenManager.java
- [x] T063 [P] [US3] Create EtaInvoiceSerializer mapping Invoice entity to ETA JSON format per document type version schema with Jackson custom serialization in platform-eta/src/main/java/com/einvoice/eta/serializer/EtaInvoiceSerializer.java
- [x] T063a [P] [US3] Create EtaDocumentTypeSchemaCache fetching schemas via GET /documenttypes/{id}/versions/{ver} through EtaStatusClient, caching locally per (documentTypeId, version) with TTL, used by EtaInvoiceSerializer for field mapping in platform-eta/src/main/java/com/einvoice/eta/serializer/EtaDocumentTypeSchemaCache.java
- [x] T064 [P] [US3] Create EtaSigningService using BouncyCastle CMSSignedDataGenerator for CAdES-BES detached signatures over serialized JSON in platform-eta/src/main/java/com/einvoice/eta/signing/EtaSigningService.java
- [x] T065 [US3] Create EtaSubmissionClient for POST /documentsubmissions with OAuth bearer auth and batch response parsing in platform-eta/src/main/java/com/einvoice/eta/client/EtaSubmissionClient.java
- [x] T066 [P] [US3] Create EtaStatusClient for GET /documents/{documentId}/details and GET /documents/recent in platform-eta/src/main/java/com/einvoice/eta/client/EtaStatusClient.java
- [x] T067 [P] [US3] Create EtaDocumentClient for GET /documents/{documentId}/pdf and PUT /documents/{documentId}/state in platform-eta/src/main/java/com/einvoice/eta/client/EtaDocumentClient.java
- [x] T068 [P] [US3] Create EtaComplianceRules implementing ETA-specific validation (ETA-001 document type schema, ETA-002 enabled document type check) as ValidationRule implementations in platform-eta/src/main/java/com/einvoice/eta/validation/EtaComplianceRules.java
- [x] T069 [US3] Create EtaAuthorityEngine implementing AuthorityEngine interface, orchestrating: serialize -> sign -> submit -> normalize response in platform-eta/src/main/java/com/einvoice/eta/EtaAuthorityEngine.java
- [x] T070 [US3] Register EtaAuthorityEngine and EtaComplianceRules in AuthorityEngineFactory and ValidationService via Spring configuration in platform-eta/src/main/java/com/einvoice/eta/EtaConfig.java
- [x] T071 [US3] Create GET /api/invoices/{id}/eta-pdf endpoint proxying ETA PDF download through EtaDocumentClient in platform-api/src/main/java/com/einvoice/api/invoice/InvoiceArtifactController.java
- [x] T072 [US3] Create EtaStatusPollingService with @Scheduled task polling IN_REVIEW invoices, checking polling_enabled flag on authority_configs, updating invoice status on final decision in platform-eta/src/main/java/com/einvoice/eta/polling/EtaStatusPollingService.java
- [x] T073 [US3] Create EtaPollingController with POST /api/admin/eta-polling/stop, POST /api/admin/eta-polling/resume, GET /api/admin/eta-polling/status per contracts/eta-management-api.md in platform-api/src/main/java/com/einvoice/api/eta/EtaPollingController.java
- [x] T074 [US3] Create POST /api/invoices/{id}/check-status endpoint for manual single-invoice ETA status check in InvoiceSubmissionController in platform-api/src/main/java/com/einvoice/api/invoice/InvoiceSubmissionController.java
- [x] T074a [US3] Create POST /api/invoices/{id}/cancel-eta endpoint validating ACCEPTED status and invoking EtaDocumentClient.cancelDocument() in platform-api/src/main/java/com/einvoice/api/invoice/InvoiceSubmissionController.java
- [x] T074b [US3] Add "Cancel" action button (with confirmation dialog) for ETA invoices in ACCEPTED status on InvoiceDetailComponent in frontend/src/app/invoices/invoice-detail/invoice-detail.component.ts
- [x] T075 [P] [US3] Create golden-file test for EtaInvoiceSerializer: known invoice -> expected JSON output in platform-eta/src/test/java/com/einvoice/eta/serializer/EtaInvoiceSerializerGoldenFileTest.java
- [x] T076 [P] [US3] Create EtaTokenManagerTest verifying cache, auto-refresh, thread safety, and 401 retry in platform-eta/src/test/java/com/einvoice/eta/auth/EtaTokenManagerTest.java
- [x] T077 [P] [US3] Create EtaSigningServiceTest verifying CMS structure and signed attributes in platform-eta/src/test/java/com/einvoice/eta/signing/EtaSigningServiceTest.java
- [x] T078 [US3] Create golden-file test resource files (expected JSON) in platform-eta/src/test/resources/golden-files/

**Checkpoint**: All three P1 user stories complete. ZATCA clearance, ZATCA reporting, and ETA submission are all operational end-to-end.

---

## Phase 6: User Story 4 — Invoice Lifecycle Management and Retry (Priority: P2)

**Goal**: Accountant can retry failed-retryable submissions with exponential backoff and return rejected invoices to draft for correction. Ambiguous timeouts are clearly surfaced.

**Independent Test**: Simulate network timeout -> verify FAILED_RETRYABLE -> retry -> verify success. Simulate authority rejection -> verify REJECTED -> return to draft -> fix -> resubmit.

### Implementation for User Story 4

- [x] T079 [US4] Add retry logic to SubmissionOrchestrator: max 3 retries, exponential backoff (2s, 4s, 8s) with jitter for FAILED_RETRYABLE invoices in platform-core/src/main/java/com/einvoice/core/service/SubmissionOrchestrator.java
- [x] T080 [US4] Add timeout handling to SubmissionOrchestrator: 30s authority call timeout -> SUBMISSION_AMBIGUOUS state in platform-core/src/main/java/com/einvoice/core/service/SubmissionOrchestrator.java
- [x] T081 [US4] Create POST /api/invoices/{id}/retry endpoint validating FAILED_RETRYABLE status and triggering SubmissionOrchestrator in InvoiceSubmissionController in platform-api/src/main/java/com/einvoice/api/invoice/InvoiceSubmissionController.java
- [x] T082 [US4] Create POST /api/invoices/{id}/return-to-draft endpoint validating REJECTED status and transitioning to DRAFT via InvoiceStateMachine in InvoiceSubmissionController in platform-api/src/main/java/com/einvoice/api/invoice/InvoiceSubmissionController.java
- [x] T083 [US4] Add ETA HTTP 429 rate limit handling with exponential backoff to EtaSubmissionClient in platform-eta/src/main/java/com/einvoice/eta/client/EtaSubmissionClient.java
- [x] T084 [US4] Create SubmissionOrchestratorTest verifying: persist-before-submit, retry on FAILED_RETRYABLE, timeout -> SUBMISSION_AMBIGUOUS, REJECTED -> DRAFT transition in platform-core/src/test/java/com/einvoice/core/service/SubmissionOrchestratorTest.java

**Checkpoint**: Full lifecycle management with retry, timeout, and rejection recovery is operational.

---

## Phase 7: User Story 5 — Create Invoices with Smart Form (Priority: P2)

**Goal**: Multi-step Angular invoice form with dynamic field rendering based on authority/type/customer, real-time calculations, and review step with validation summary.

**Independent Test**: Walk through the form for ZATCA Tax Invoice, ZATCA Simplified, and ETA invoice types; verify field visibility changes, real-time totals, and validation messages.

### Implementation for User Story 5

- [x] T085 [P] [US5] Create CalculationService in Angular mirroring backend InvoiceCalculationService: line net, line VAT, line total, document totals, VAT breakdown, rounding (BigDecimal HALF_UP equivalent) in frontend/src/app/invoices/invoice-form/services/calculation.service.ts
- [x] T086 [P] [US5] Create HeaderStepComponent with authority selector, invoice type, subtype flags (ZATCA-specific), dates, currency (default SAR/EGP per authority with override) in frontend/src/app/invoices/invoice-form/steps/header-step.component.ts
- [x] T087 [P] [US5] Create BuyerStepComponent with customer search/select from catalogue, inline create option, dynamic B2B/B2C field requirements in frontend/src/app/invoices/invoice-form/steps/buyer-step.component.ts
- [x] T088 [P] [US5] Create LinesStepComponent with item catalogue selection, quantity/discount/VAT inputs, real-time per-line calculations, add/remove/reorder lines in frontend/src/app/invoices/invoice-form/steps/lines-step.component.ts
- [x] T089 [US5] Create ReviewStepComponent showing full invoice preview: header data, line items table, VAT breakdown by category, document totals, validation summary with warnings highlighted in frontend/src/app/invoices/invoice-form/steps/review-step.component.ts
- [x] T090 [US5] Rewrite InvoiceFormComponent as multi-step wizard using Angular Material Stepper, Reactive Forms, integrating all step components with shared form model in frontend/src/app/invoices/invoice-form/invoice-form.component.ts
- [x] T091 [US5] Add dynamic field visibility rules: authority drives subtype flags vs document types, customer type drives buyer VAT requirement, invoice type drives supply date and original invoice reference in frontend/src/app/invoices/invoice-form/invoice-form.component.ts
- [x] T092 [US5] Add client-side validation guidance: red borders on invalid fields, inline error messages, disable submit button until all errors resolved in frontend/src/app/invoices/invoice-form/steps/ (all step components)
- [x] T093 [US5] Add ETA document type filtering based on enabled_document_types from branch authority config in HeaderStepComponent in frontend/src/app/invoices/invoice-form/steps/header-step.component.ts
- [x] T094 [US5] Add support for external invoice reference (text input) alongside internal invoice lookup (dropdown) for credit/debit notes in BuyerStepComponent in frontend/src/app/invoices/invoice-form/steps/buyer-step.component.ts
- [x] T095 [US5] Create calculation.service.spec.ts with parity tests: same inputs as backend InvoiceCalculationServiceTest must produce identical outputs in frontend/src/app/invoices/invoice-form/services/calculation.service.spec.ts

**Checkpoint**: Smart invoice form is fully functional with dynamic rendering, real-time calculations, and validation guidance.

---

## Phase 8: User Story 8 — Three-Layer Invoice Validation (Priority: P2)

**Goal**: Accountant sees all validation errors grouped by layer (structural, arithmetic, compliance) with severity indicators before submission. All locally-detectable issues are caught.

**Independent Test**: Submit invoices with known errors at each layer; verify all errors caught with correct layer/rule attribution.

**Note**: Validation rules were created in foundational phase (T028-T030) and authority-specific phases (T044 for ZATCA, T068 for ETA). This phase focuses on the Angular validation display and integration testing.

### Implementation for User Story 8

- [x] T096 [US8] Update InvoiceService to call POST /api/invoices/{id}/validate and parse grouped validation response in frontend/src/app/shared/services/invoice.service.ts
- [x] T097 [US8] Create ValidationResultComponent displaying errors grouped by layer with severity badges (ERROR red, WARNING yellow) and field-level highlights in frontend/src/app/invoices/invoice-detail/validation-result.component.ts
- [x] T098 [US8] Add validate-before-submit flow to ReviewStepComponent: call validate endpoint, display results, block submission on ERRORs, allow proceed on WARNINGs only in frontend/src/app/invoices/invoice-form/steps/review-step.component.ts
- [x] T099 [US8] Create ValidationServiceTest covering all structural rules (STRUCT-001 to STRUCT-004), arithmetic rules (ARITH-001 to ARITH-005), and readiness rules (READY-001 to READY-003) in platform-core/src/test/java/com/einvoice/core/service/ValidationServiceTest.java
- [x] T100 [US8] Create ZatcaComplianceRulesTest covering BR-KSA-04 (date), BR-KSA-07 (subtype flags), BR-KSA-09 (seller address), BR-KSA-15 (supply dates), BR-KSA-46 (buyer VAT exports), BR-KSA-56 (credit note ref) in platform-zatca/src/test/java/com/einvoice/zatca/validation/ZatcaComplianceRulesTest.java
- [x] T100a [US8] Create EtaComplianceRulesTest covering ETA-001 (document type schema validation) and ETA-002 (enabled document type check) in platform-eta/src/test/java/com/einvoice/eta/validation/EtaComplianceRulesTest.java

**Checkpoint**: Validation pipeline fully tested and integrated with Angular. All three validation layers operational.

---

## Phase 9: User Story 9 — Invoice Hash Chain Integrity (Priority: P2)

**Goal**: ZATCA hash chain is maintained per (branch, environment) with seed hash for first invoice and pessimistic locking for concurrent submission serialization.

**Independent Test**: Submit 3 sequential invoices; verify each contains correct previous hash. Submit 2 concurrently; verify serialization.

**Note**: Hash chain infrastructure was built in US1 (T040, T047). This phase adds integration testing and concurrent submission serialization verification.

### Implementation for User Story 9

- [x] T101 [US9] Create ZatcaHashChainIntegrationTest verifying: first invoice uses seed hash, three sequential invoices chain correctly, counter increments on each success in platform-zatca/src/test/java/com/einvoice/zatca/hash/ZatcaHashChainIntegrationTest.java
- [x] T102 [US9] Create concurrent submission test: two invoices for same branch submitted simultaneously are serialized via pessimistic lock, second gets first's hash in platform-core/src/test/java/com/einvoice/core/service/ConcurrentSubmissionTest.java
- [x] T103 [US9] Verify hash chain does NOT advance on REJECTED or FAILED submissions (only on SUCCESS) in ZatcaHashChainIntegrationTest in platform-zatca/src/test/java/com/einvoice/zatca/hash/ZatcaHashChainIntegrationTest.java

**Checkpoint**: Hash chain integrity verified with sequential, concurrent, and failure scenarios.

---

## Phase 10: User Story 6 — ZATCA Onboarding and Certificate Management (Priority: P2)

**Goal**: Company admin can onboard a branch to ZATCA through a resumable wizard (CSR -> compliance CSID -> test invoices -> production CSID), import existing certificates, and renew expiring ones.

**Independent Test**: Walk through onboarding wizard with ZATCA sandbox; verify each step produces expected artifacts. Simulate mid-step failure and verify resume.

### Implementation for User Story 6

- [x] T104 [US6] Create ZatcaOnboardingService orchestrating 4-step onboarding: CSR generation -> POST /compliance for compliance CSID -> submit 6 test invoices -> POST /production/csids for production CSID, with per-step progress persistence in platform-zatca/src/main/java/com/einvoice/zatca/onboarding/ZatcaOnboardingService.java
- [x] T105 [US6] Create ZatcaCsrGenerator for CSR generation with required ZATCA fields (CN, OU, O, C, serialNumber) in platform-zatca/src/main/java/com/einvoice/zatca/onboarding/ZatcaCsrGenerator.java
- [x] T106 [US6] Create ZatcaComplianceClient for POST /compliance (CSR -> compliance CSID) and POST /compliance/invoices (test invoice submission) in platform-zatca/src/main/java/com/einvoice/zatca/client/ZatcaComplianceClient.java
- [x] T107 [US6] Create ZatcaProductionCsidClient for POST /production/csids (exchange compliance CSID for production CSID) and PATCH /production/csids (renewal) in platform-zatca/src/main/java/com/einvoice/zatca/client/ZatcaProductionCsidClient.java
- [x] T108 [US6] Create ZatcaCertRenewalService for PATCH /production/csids certificate renewal flow in platform-zatca/src/main/java/com/einvoice/zatca/renewal/ZatcaCertRenewalService.java
- [x] T109 [US6] Create ZatcaOnboardingController with POST /api/branches/{branchId}/zatca/onboard, GET .../onboard/status, POST .../import-csid, POST .../renew-certificate, GET .../certificate-status per contracts/zatca-onboarding-api.md in platform-api/src/main/java/com/einvoice/api/zatca/ZatcaOnboardingController.java
- [x] T110 [P] [US6] Create OnboardingStatusResponse, ImportCsidRequest, CertificateStatusResponse DTOs in platform-api/src/main/java/com/einvoice/api/zatca/dto/
- [x] T111 [US6] Create ZatcaOnboardingComponent with multi-step wizard showing current progress, resume capability, CSR data form, compliance progress indicators, and production CSID confirmation in frontend/src/app/config/zatca-onboarding/zatca-onboarding.component.ts
- [x] T112 [P] [US6] Create ZatcaCertificateComponent showing certificate status with expiry date, days until expiry warning (< 30 days), and renew button in frontend/src/app/config/zatca-certificate/zatca-certificate.component.ts
- [x] T113 [US6] Create ZatcaService in Angular with methods for onboard, getOnboardingStatus, importCsid, renewCertificate, getCertificateStatus in frontend/src/app/shared/services/zatca.service.ts
- [x] T114 [US6] Add CSID import form (file upload for certificate + private key + secret) to ZatcaOnboardingComponent as alternative to automated onboarding in frontend/src/app/config/zatca-onboarding/zatca-onboarding.component.ts
- [x] T115 [US6] Add routing for ZATCA onboarding and certificate screens under Config module in frontend/src/app/config/config.component.ts and frontend/src/app/app.routes.ts

**Checkpoint**: ZATCA onboarding with resume support, manual import, and certificate renewal are operational.

---

## Phase 11: User Story 7 — ETA Item Code Management (Priority: P3)

**Goal**: Accountant or admin can register new ETA item codes, search published codes, and track registration status.

**Independent Test**: Register a new item code via management screen; verify status updates from ETA pre-production.

### Implementation for User Story 7

- [x] T116 [US7] Create EtaCodeService for item code registration (POST /codesusage), listing (GET /codesusage), searching published codes (GET /codes), updating codes (PUT /codesusage/{id}) via ETA API in platform-eta/src/main/java/com/einvoice/eta/codes/EtaCodeService.java
- [x] T117 [US7] Create EtaCodeController with POST /api/eta/codes, GET /api/eta/codes, GET /api/eta/codes/search-published, PUT /api/eta/codes/{id} per contracts/eta-management-api.md in platform-api/src/main/java/com/einvoice/api/eta/EtaCodeController.java
- [x] T118 [P] [US7] Create CodeRequest, CodeResponse, PublishedCodeResponse DTOs in platform-api/src/main/java/com/einvoice/api/eta/dto/
- [x] T119 [US7] Create EtaCodesComponent with code list table, register new code form, search published codes tab, status badges in frontend/src/app/config/eta-codes/eta-codes.component.ts
- [x] T120 [US7] Create EtaCodeService in Angular with methods for register, list, searchPublished, update in frontend/src/app/shared/services/eta-code.service.ts
- [x] T121 [US7] Add routing for ETA codes screen under Config module in frontend/src/app/app.routes.ts

**Checkpoint**: ETA item code management is operational.

---

## Phase 12: Angular Invoice List & Detail Enhancements

**Purpose**: Update existing invoice list and detail views to support full lifecycle, submission timeline, artifact downloads, and status-aware actions.

### Invoice Detail Enhancements

- [x] T122 [P] Create SubmissionTimelineComponent showing all submission attempts with timestamps, status, error messages in frontend/src/app/shared/components/submission-timeline/submission-timeline.component.ts
- [x] T123 Update InvoiceDetailComponent to display: current lifecycle status badge, submission timeline, artifact download buttons (XML, JSON, PDF), ZATCA warnings, retry button for FAILED_RETRYABLE, return-to-draft button for REJECTED in frontend/src/app/invoices/invoice-detail/invoice-detail.component.ts
- [x] T124 Update InvoiceService with methods for submit, retry, validate, confirmSubmission, checkStatus, getSubmissions, getArtifact, returnToDraft, getEtaPdf in frontend/src/app/shared/services/invoice.service.ts

### Invoice List Enhancements

- [x] T125 Update InvoiceListComponent with status badges for all lifecycle states (color-coded), filters for date range/status/authority/type/customer, per-invoice action buttons (View, Edit for draft, Submit for ready, Retry for failed, Cancel for draft) in frontend/src/app/invoices/invoice-list/invoice-list.component.ts
- [x] T126 Update StatusBadgeComponent with new status values and color scheme (green: CLEARED/REPORTED/ACCEPTED, yellow: IN_REVIEW/VALIDATED, red: REJECTED/FAILED_*, gray: DRAFT/CANCELLED, orange: SUBMISSION_AMBIGUOUS) in frontend/src/app/shared/components/status-badge/status-badge.component.ts

### ETA Polling Controls

- [x] T127 Create EtaPollingService in Angular with methods for getStatus, stop, resume in frontend/src/app/shared/services/eta-polling.service.ts
- [x] T128 Add ETA polling control panel (status indicator, stop/resume buttons) to Config module in frontend/src/app/config/config.component.ts

**Checkpoint**: All Angular screens updated with full lifecycle support.

---

## Phase 13: Polish & Cross-Cutting Concerns

**Purpose**: Improvements that affect multiple user stories

- [x] T129 Refactor existing InvoiceValidationService to delegate to new ValidationService (remove duplicate validation logic, keep backward compatibility) in platform-core/src/main/java/com/einvoice/core/service/InvoiceValidationService.java
- [x] T130 [P] Add @Audited annotation to all submission, onboarding, and certificate management service methods for audit trail in platform-zatca and platform-eta service classes
- [x] T131 [P] Create TenantIsolationSubmissionTest: verify Company A cannot submit Company B's invoices, cannot see Company B's artifacts in platform-api/src/test/java/com/einvoice/api/TenantIsolationSubmissionTest.java
- [x] T102a [Polish] Add real-DB concurrent submission test using @SpringBootTest + Testcontainers PostgreSQL: two transactions on separate threads compete for the same authority_configs row, verify the second blocks until the first commits and observes the post-commit hash. Location: platform-core/src/test/java/com/einvoice/core/service/ConcurrentSubmissionDbTest.java
- [x] T132 [P] Create ZATCA PDF decision document per implementation plan in Docs/zatca-pdf-decision.md
- [x] T133 Add currency default logic: SAR for ZATCA, EGP for ETA (with override allowed) to InvoiceService create/update flow in platform-core/src/main/java/com/einvoice/core/service/InvoiceService.java
- [x] T134 Run quickstart.md smoke test checklist and verify all items pass
- [x] T135 Update InvoiceController to block modifications on non-DRAFT invoices, returning 409 with clear message in platform-api/src/main/java/com/einvoice/api/invoice/InvoiceController.java

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — can start immediately
- **Foundational (Phase 2)**: Depends on Setup completion — BLOCKS all user stories
- **US1 ZATCA Clearance (Phase 3)**: Depends on Foundational
- **US2 ZATCA Reporting (Phase 4)**: Depends on US1 (shares ZATCA engine infra)
- **US3 ETA Submission (Phase 5)**: Depends on Foundational only — can run in parallel with US1
- **US4 Retry/Lifecycle (Phase 6)**: Depends on US1 or US3 (needs working submission orchestrator)
- **US5 Smart Form (Phase 7)**: Depends on Foundational only — can start in parallel with US1/US3
- **US8 Validation UI (Phase 8)**: Depends on Foundational — can start after validation rules exist
- **US9 Hash Chain (Phase 9)**: Depends on US1 — needs working ZATCA submission
- **US6 ZATCA Onboarding (Phase 10)**: Depends on US1 (needs ZATCA engine working for test invoices)
- **US7 ETA Codes (Phase 11)**: Depends on US3 (needs ETA token manager)
- **Angular Enhancements (Phase 12)**: Depends on US1/US3 (needs submission APIs)
- **Polish (Phase 13)**: Depends on all desired user stories being complete

### User Story Dependencies

```
Foundational ──┬──> US1 (ZATCA Clearance) ──> US2 (ZATCA Reporting)
               │                            ├──> US9 (Hash Chain)
               │                            └──> US6 (ZATCA Onboarding)
               │
               ├──> US3 (ETA Submission) ───> US7 (ETA Codes)
               │
               ├──> US5 (Smart Form) [parallel with US1/US3]
               │
               └──> US8 (Validation UI) [parallel with US1/US3]

US4 (Retry) depends on US1 OR US3

Angular Enhancements depend on US1 + US3

Polish depends on all
```

### Parallel Opportunities

**After Foundational completes, these can run in parallel:**
- US1 (ZATCA Clearance) + US3 (ETA Submission) — independent authority modules
- US5 (Smart Form) — frontend work independent of backend engine building
- US8 (Validation UI) — frontend work with existing validation rules

**Within ZATCA engine (US1):**
- T039 (UBL builder) + T040 (hash) + T041 (signing) + T042 (QR) — all independent services
- T053 + T054 + T055 + T056 — all golden-file tests in parallel

**Within ETA engine (US3):**
- T062 (token) + T063 (serializer) + T064 (signing) — independent services
- T066 + T067 — independent clients

---

## Parallel Example: ZATCA Engine (US1)

```
# Launch all independent ZATCA services together:
Task T039: "Create ZatcaUblBuilder in platform-zatca/.../xml/ZatcaUblBuilder.java"
Task T040: "Create ZatcaHashService in platform-zatca/.../hash/ZatcaHashService.java"
Task T041: "Create ZatcaSigningService in platform-zatca/.../signing/ZatcaSigningService.java"
Task T042: "Create ZatcaQrService in platform-zatca/.../qr/ZatcaQrService.java"
Task T044: "Create ZatcaComplianceRules in platform-zatca/.../validation/ZatcaComplianceRules.java"

# Then sequential: ZatcaAuthorityEngine depends on all above
Task T045: "Create ZatcaAuthorityEngine in platform-zatca/.../ZatcaAuthorityEngine.java"
```

---

## Parallel Example: ETA Engine (US3)

```
# Launch all independent ETA services together:
Task T062: "Create EtaTokenManager in platform-eta/.../auth/EtaTokenManager.java"
Task T063: "Create EtaInvoiceSerializer in platform-eta/.../serializer/EtaInvoiceSerializer.java"
Task T064: "Create EtaSigningService in platform-eta/.../signing/EtaSigningService.java"
Task T066: "Create EtaStatusClient in platform-eta/.../client/EtaStatusClient.java"
Task T067: "Create EtaDocumentClient in platform-eta/.../client/EtaDocumentClient.java"

# Then sequential: EtaAuthorityEngine depends on above
Task T069: "Create EtaAuthorityEngine in platform-eta/.../EtaAuthorityEngine.java"
```

---

## Implementation Strategy

### MVP First (User Stories 1 + 2 + 3)

1. Complete Phase 1: Setup (migrations + entities)
2. Complete Phase 2: Foundational (state machine + validation + orchestrator)
3. Complete Phase 3: US1 ZATCA Clearance
4. Complete Phase 4: US2 ZATCA Reporting
5. Complete Phase 5: US3 ETA Submission
6. **STOP and VALIDATE**: All three authority submission flows working end-to-end
7. Deploy/demo if ready — core compliance capability is operational

### Incremental Delivery

1. Setup + Foundational -> Foundation ready
2. Add US1 + US2 -> ZATCA fully operational -> Demo
3. Add US3 -> ETA operational -> Demo (MVP complete!)
4. Add US4 -> Retry/lifecycle -> Robustness
5. Add US5 -> Smart form -> UX upgrade
6. Add US6 -> ZATCA onboarding -> Self-service setup
7. Add US7 + US8 + US9 -> Remaining features
8. Angular enhancements + Polish -> Production-ready

### Parallel Team Strategy

With multiple developers after Foundational:
- Developer A: US1 + US2 (ZATCA engine)
- Developer B: US3 (ETA engine)
- Developer C: US5 (Angular smart form)
- Join for US4, US6, US7, US8, US9, Angular enhancements, Polish

---

## Notes

- [P] tasks = different files, no dependencies
- [Story] label maps task to specific user story for traceability
- Each user story should be independently completable and testable
- Golden-file tests committed to repo as test resources
- Commit after each task or logical group
- Stop at any checkpoint to validate story independently
- All new entities use tenant-scoped queries (Constitution II)
- All artifacts are immutable (Constitution XIV)
- All state transitions create audit log entries (Constitution VI)
