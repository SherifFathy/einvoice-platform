# Implementation Plan: Authority Engines, Submission & Invoice Smart Form

**Branch**: `003-authority-engines-submission` | **Date**: 2026-04-16 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `/specs/003-authority-engines-submission/spec.md`

## Summary

This wave implements the full ZATCA and ETA compliance engines behind a common AuthorityEngine abstraction, an invoice lifecycle state machine, a three-layer validation engine, a submission orchestrator with persist-before-submit guarantees, ZATCA onboarding with resumable steps, ETA status polling with admin-controlled background processing, and a multi-step Angular smart invoice form with real-time calculations. All authority-specific logic is isolated in `platform-zatca` and `platform-eta` modules, while shared orchestration lives in `platform-core`.

## Technical Context

**Language/Version**: Java 17 (backend), TypeScript 5.x (frontend)
**Primary Dependencies**: Spring Boot 3.4.4, Spring Security, Spring Data JPA, Angular 19, Angular Material, xades4j 2.4.0 (ZATCA XAdES signing), BouncyCastle 1.80 (ETA CAdES signing), ZXing (QR generation), Jackson, Lombok
**Storage**: PostgreSQL 16 (via Docker Compose, Flyway migrations V12+)
**Testing**: JUnit 5 + Spring Boot Test (backend), Jasmine + Karma (frontend), golden-file tests for XML/JSON payloads
**Target Platform**: On-premises Linux/Windows server (Docker Compose deployment)
**Project Type**: Web application (multi-module Maven backend + Angular frontend)
**Performance Goals**: Single invoice submission under 5 seconds in nominal conditions (Constitution XVII.1)
**Constraints**: Stateless services (Constitution IV), all cryptography server-side (Constitution V), tenant isolation on every query (Constitution II)
**Scale/Scope**: Multi-tenant, multi-authority. Per-branch hash chain serialization for ZATCA. ETA batch capacity up to 100 docs per API call (single submission only in this wave).

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Notes |
|-----------|--------|-------|
| I. Compliance-First | PASS | ZATCA UBL 2.1 XML + XAdES signing, ETA JSON + CAdES signing, authority-specific validation rules as executable classes |
| II. Multi-Tenant Isolation | PASS | All new entities (submission_attempts, invoice_artifacts, eta_item_codes) carry tenant ownership; queries scoped via TenantContext |
| III. Branch + Environment Segregation | PASS | Submission orchestrator validates (tenant, branch, authority, environment) tuple; hash chain per (branch, environment) |
| IV. Stateless Service | PASS | Authority engines are stateless; ETA token cache is bounded and scoped |
| V. Server-Side Cryptography | PASS | All signing, hashing, QR generation server-side; no crypto material to Angular; credentials encrypted at rest via AES-256-GCM |
| VI. Immutable Audit | PASS | Every state transition creates audit record; submission attempts are append-only; invoice artifacts are immutable |
| VII. Deterministic Invoice Lifecycle | PASS | Explicit state machine with allowed transitions map; every attempt recorded independently |
| VIII. Validation Layering | PASS | Three-layer validation: structural, arithmetic, authority-compliance; backend is source of truth; smart form matches backend calc |
| IX. Angular Engineering | PASS | Reactive Forms for smart invoice form; schema-driven field visibility; no authority logic in frontend |
| X. Spring Boot Engineering | PASS | Layered architecture; AuthorityEngine interface; Flyway migrations for all schema changes |
| XI. Authority Adapter | PASS | Common AuthorityEngine interface; ZatcaAuthorityEngine and EtaAuthorityEngine; adding authority requires no core rewrite |
| XII. Secure On-Prem Deployment | N/A | Deployment is Wave 3; this wave adds no deployment changes |
| XIII. Reliability and Recovery | PASS | Persist-before-submit; bounded retries with exponential backoff; SUBMISSION_AMBIGUOUS state for timeouts |
| XIV. Document Preservation | PASS | Invoice artifacts immutable (no UPDATE); content hash for integrity; source data vs generated payload distinguished |
| XV. Change Control | PASS | All schema changes via Flyway; validation rules traceable to BR-KSA/ETA specs |
| XVI. Testing | PASS | Golden-file tests for ZATCA XML, QR TLV, ETA JSON; lifecycle transition tests; calculation parity tests; tenant isolation tests |
| XVII. Performance | PASS | Single submission under 5s target; ETA token pre-refresh avoids latency; pessimistic lock scoped to hash chain update only |
| XVIII. Source of Truth | PASS | spec.md defines functional truth; this plan defines sequencing |

**Gate result: PASS** — No violations. Proceed to Phase 0.

## Project Structure

### Documentation (this feature)

```text
specs/003-authority-engines-submission/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/           # Phase 1 output
│   ├── submission-api.md
│   ├── zatca-onboarding-api.md
│   ├── eta-management-api.md
│   └── artifact-api.md
└── tasks.md             # Phase 2 output (via /speckit.tasks)
```

### Source Code (repository root)

```text
platform-core/
├── src/main/java/com/einvoice/core/
│   ├── domain/
│   │   ├── Invoice.java                    # (existing — extend with new status values)
│   │   ├── SubmissionAttempt.java           # NEW
│   │   ├── InvoiceArtifact.java            # NEW
│   │   ├── OnboardingProgress.java         # NEW
│   │   └── enums/
│   │       ├── InvoiceStatus.java          # (existing — extend)
│   │       ├── SubmissionResult.java       # NEW
│   │       └── ArtifactType.java           # NEW
│   ├── repository/
│   │   ├── SubmissionAttemptRepository.java # NEW
│   │   ├── InvoiceArtifactRepository.java  # NEW
│   │   └── OnboardingProgressRepository.java # NEW
│   ├── service/
│   │   ├── InvoiceStateMachine.java        # NEW
│   │   ├── ValidationService.java          # NEW (3-layer orchestrator)
│   │   ├── ValidationRule.java             # NEW (interface)
│   │   ├── SubmissionOrchestrator.java     # NEW
│   │   ├── AuthorityEngine.java            # NEW (interface)
│   │   ├── AuthorityEngineFactory.java     # NEW
│   │   └── validation/
│   │       ├── StructuralValidationRule.java     # NEW (multiple rules)
│   │       ├── ArithmeticValidationRule.java     # NEW
│   │       └── SubmissionReadinessRule.java      # NEW
│   └── resources/db/migration/
│       ├── V12__add_invoice_statuses.sql         # NEW
│       ├── V13__create_submission_attempts.sql    # NEW
│       ├── V14__create_invoice_artifacts.sql      # NEW
│       ├── V15__create_eta_item_codes.sql         # NEW
│       ├── V16__create_onboarding_progress.sql    # NEW
│       └── V17__add_external_invoice_ref.sql      # NEW
└── src/test/java/com/einvoice/core/
    ├── service/InvoiceStateMachineTest.java
    ├── service/ValidationServiceTest.java
    └── service/SubmissionOrchestratorTest.java

platform-zatca/
├── src/main/java/com/einvoice/zatca/
│   ├── ZatcaAuthorityEngine.java           # NEW (implements AuthorityEngine)
│   ├── xml/
│   │   └── ZatcaUblBuilder.java            # NEW
│   ├── qr/
│   │   └── ZatcaQrService.java             # NEW
│   ├── hash/
│   │   └── ZatcaHashService.java           # NEW
│   ├── signing/
│   │   └── ZatcaSigningService.java        # NEW
│   ├── onboarding/
│   │   └── ZatcaOnboardingService.java     # NEW
│   ├── renewal/
│   │   └── ZatcaCertRenewalService.java    # NEW
│   ├── client/
│   │   ├── ZatcaClearanceClient.java       # NEW
│   │   └── ZatcaReportingClient.java       # NEW
│   └── validation/
│       └── ZatcaComplianceRules.java       # NEW (BR-KSA rules)
└── src/test/java/com/einvoice/zatca/
    ├── xml/ZatcaUblBuilderTest.java        # golden-file tests
    ├── qr/ZatcaQrServiceTest.java          # golden-file tests
    ├── hash/ZatcaHashServiceTest.java
    └── signing/ZatcaSigningServiceTest.java

platform-eta/
├── src/main/java/com/einvoice/eta/
│   ├── EtaAuthorityEngine.java             # NEW (implements AuthorityEngine)
│   ├── auth/
│   │   └── EtaTokenManager.java            # NEW
│   ├── serializer/
│   │   └── EtaInvoiceSerializer.java       # NEW
│   ├── signing/
│   │   └── EtaSigningService.java          # NEW
│   ├── client/
│   │   ├── EtaSubmissionClient.java        # NEW
│   │   ├── EtaStatusClient.java            # NEW
│   │   └── EtaDocumentClient.java          # NEW
│   ├── codes/
│   │   └── EtaCodeService.java             # NEW
│   ├── polling/
│   │   └── EtaStatusPollingService.java    # NEW
│   └── validation/
│       └── EtaComplianceRules.java         # NEW
└── src/test/java/com/einvoice/eta/
    ├── serializer/EtaInvoiceSerializerTest.java  # golden-file tests
    └── auth/EtaTokenManagerTest.java

platform-api/
├── src/main/java/com/einvoice/api/
│   ├── invoice/
│   │   ├── InvoiceController.java          # (existing — extend with submit/retry/status)
│   │   ├── InvoiceSubmissionController.java # NEW
│   │   └── dto/
│   │       ├── SubmitResponse.java         # NEW
│   │       ├── SubmissionAttemptResponse.java # NEW
│   │       └── ArtifactResponse.java       # NEW
│   ├── zatca/
│   │   ├── ZatcaOnboardingController.java  # NEW
│   │   └── dto/
│   │       ├── OnboardingStatusResponse.java # NEW
│   │       └── ImportCsidRequest.java      # NEW
│   ├── eta/
│   │   ├── EtaCodeController.java          # NEW
│   │   ├── EtaPollingController.java       # NEW
│   │   └── dto/
│   │       ├── CodeRequest.java            # NEW
│   │       └── CodeResponse.java           # NEW
│   └── config/
│       └── GlobalExceptionHandler.java     # (existing — extend)

frontend/src/app/
├── invoices/
│   ├── invoice-form/                       # (existing — major rewrite to smart form)
│   │   ├── invoice-form.component.ts
│   │   ├── steps/
│   │   │   ├── header-step.component.ts    # NEW
│   │   │   ├── buyer-step.component.ts     # NEW
│   │   │   ├── lines-step.component.ts     # NEW
│   │   │   └── review-step.component.ts    # NEW
│   │   └── services/
│   │       └── calculation.service.ts      # NEW
│   ├── invoice-detail/                     # (existing — extend with submission timeline, artifacts)
│   └── invoice-list/                       # (existing — extend with status filters, actions)
├── config/
│   ├── zatca-onboarding/                   # NEW
│   │   └── zatca-onboarding.component.ts
│   ├── zatca-certificate/                  # NEW
│   │   └── zatca-certificate.component.ts
│   └── eta-codes/                          # NEW
│       └── eta-codes.component.ts
└── shared/
    ├── services/
    │   ├── invoice.service.ts              # (existing — extend with submit/retry/artifacts)
    │   ├── zatca.service.ts                # NEW
    │   ├── eta-code.service.ts             # NEW
    │   └── eta-polling.service.ts          # NEW
    └── components/
        └── submission-timeline/            # NEW
            └── submission-timeline.component.ts
```

**Structure Decision**: Follows the existing 7-module Maven structure established in Wave 0. New code is added to `platform-zatca`, `platform-eta`, and `platform-core` per the Authority Adapter pattern (Constitution XI). All authority-specific logic is in its respective module; shared orchestration and state machine in `platform-core`. Frontend follows existing Angular workspace structure with feature-scoped components.

## Complexity Tracking

> No Constitution violations — table not required.
