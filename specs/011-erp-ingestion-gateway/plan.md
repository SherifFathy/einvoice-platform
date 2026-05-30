# Implementation Plan: ERP Ingestion Gateway

**Branch**: `011-erp-ingestion-gateway` | **Date**: 2026-05-27 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `specs/011-erp-ingestion-gateway/spec.md`

## Summary

Four `POST /api/integration/v1/**` endpoints let an ERP push exactly one externally-submitted document per call into the platform's existing operational tables (`eta_invoice_*`, `eta_receipt_*`, `zatca_standard_*`, `zatca_simplified_*`). Each call resolves `(companyRegistrationNumber, environment)` against `companies.tax_number` + `authority_environments`, dedupes against the document number, persists header + lines + (where applicable) line-taxes inside one transaction, writes one `audit_logs` row, and emits one structured INFO log line. Every inbound request (success or failure) is first persisted to a new `inbound_payload_archive` table outside the ingest transaction so payloads from rejected, rolled-back, or unauthorised calls remain available for forensics. No external authority API is called; the ERP owns the authority-side lifecycle. Sprint 1 is unauthenticated (`permitAll()`) — API-key / HMAC auth is a Sprint 2 concern.

Per the locked Clarifications session 2026-05-27:

- **Actor on ingested rows**: literal `INTEGRATION_GATEWAY` stamped into `created_by`/`updated_by`/`submitted_by`. No schema change.
- **Audit log**: exactly one row per *successful* ingest, action = `INGESTED` (or `CREATED` if the V52 allow-list trigger rejects the new value), inside the ingest transaction.
- **No request-body, line-count, or rate-limit caps in Sprint 1** — defer to Sprint 2 API-key feature.
- **Observability**: one structured INFO log per ingest with MDC fields; default Actuator metrics only; raw payload archive table is the forensics path.
- **No per-endpoint latency SLO**.

Builds on `specs/010-authority-spec-alignment/` (V58–V61): the `erp_reference_id` column, the SDK-shaped tables (ZATCA per-rate VAT subtotals, line-block, signature artifacts; ETA Receipt v1.2 line restructure), and the entity / serialiser updates that consume them. **Feature 011 MUST NOT begin until the V58–V61 sequence and its paired Wave 7/8 updates have merged** (FR-024).

## Technical Context

**Language/Version**: Java 17 (backend), TypeScript 5.x (frontend, Angular 19) — frontend is read-side only here, no new screens.
**Primary Dependencies**: Spring Boot 3.4.4 (web, data-jpa, validation, security), Jackson, Lombok, Spring Data JPA + Flyway 10.x, PostgreSQL JDBC driver. **New dependency**: `org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.8` (Swagger UI + OpenAPI 3 spec generation, FR-022).
**Storage**: PostgreSQL 16 (single instance via Docker Compose). **One new Flyway migration: V62** (FR-024a) carrying three atomic changes: (1) create `inbound_payload_archive` table + BRIN index + `integration_forensics` role grant (FR-OBS-003 / FR-OBS-004); (2) seed one `users` row representing the gateway system principal (FR-009c) — `username='INTEGRATION_GATEWAY'`, `is_active=false`, `is_super_user=false`; (3) extend the existing V52 `audit_logs` allow-list trigger to accept `INGESTED` as an action value (FR-009e). All existing operational tables (header / line / line-tax across ETA Invoice, ETA Receipt, ZATCA Standard, ZATCA Simplified) plus `audit_logs` are otherwise consumed unchanged, in the SDK-aligned shape delivered by V58–V61.
**Testing**: JUnit 5 + Spring Boot Test (`@SpringBootTest` + `@AutoConfigureMockMvc` for controller integration tests), Testcontainers PostgreSQL 16 for end-to-end DB assertions, Jackson `ObjectMapper` for DTO deserialisation tests, log-capture via `OutputCaptureExtension` for FR-OBS-001 verification. No frontend tests added (read paths are unchanged).
**Target Platform**: On-prem Linux server running the existing `platform-api` Spring Boot module behind the existing reverse proxy. Browser (Angular) — read-side only.
**Project Type**: Web application — backend + frontend monorepo (existing `platform-api`, `platform-core`, `platform-security`, `frontend/` layout). No new top-level modules.
**Performance Goals**: **No per-endpoint latency / throughput SLO declared** (clarified 2026-05-27 Q5). Default Spring Boot Actuator HTTP metrics observe regressions; no CI assertion on ingest latency. Constitution XXV.1's 5-second submission target applies to internal *submission* flows; the gateway does not submit to authorities and is not bound by that target. Compound indexes on `(company_id, authority_environment_id, <document_number>)` already exist on all four header tables (delivered by V53/V57); no new indexes required for the dedup path. The new `inbound_payload_archive` table gets a single-column UUID PK and one BRIN index on `received_at` (cheap, append-only).
**Constraints**: Sprint 1 is unauthenticated (`permitAll()` on `/api/integration/v1/**`). No declared payload-size, line-count, or rate-limit caps (clarified 2026-05-27 Q3). No external authority API is called. The archive write MUST succeed before the main ingest transaction begins (FR-OBS-003 / FR-OBS-005); the platform refuses to accept any payload it cannot first archive. RBAC on the archive store is mandatory (FR-OBS-004) — it may contain partner business data.
**Scale/Scope**: 1 new Flyway migration (V62), 1 new JPA entity (`InboundPayloadArchive`), 2 new exceptions (`CompanyNotFoundException`, `AuthorityEnvironmentNotFoundException`), 3 shared DTO types (2 enums + 1 response record), 4 request DTO records (with ~40 nested record types across the four authorities), 1 resolution service, 2 ingestion services (ETA, ZATCA), 1 archive service, 1 archive interceptor / filter, 2 controllers, 5 repository methods added on 3 existing repositories, 4 entity field touches (already delivered by 010 — re-verified here), 2 `GlobalExceptionHandler` additions, 1 `SecurityConfig` permit-all line, 1 `OpenApiConfig` bean class, 1 logback / MDC filter for FR-OBS-001. Total: **≈16 new files + ≈11 file edits** (per source plan §11).

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-evaluated after Phase 1 design — verdict unchanged.*

| Principle | Verdict | Notes |
|---|---|---|
| I. Compliance-First | PASS | DTOs are shaped to the published SDKs (ETA Receipt v1.2, ETA Invoice v1.0, ZATCA Fatoora v2.0.3). Authority-specific rules surface as DTO `@Pattern` / service-layer cross-field checks (FR-011), not controller branching. |
| II. Multi-Tenant Isolation | PASS | Every ingested row carries `(company_id, authority_environment_id)` resolved at FR-006 / FR-007. The audit-log row, the archive row (where resolution succeeded), and every header/line/line-tax row inherit the existing isolation key. |
| III. Authority Environment Architecture | PASS | Resolution targets the existing `authority_environments` registry. The DTO `IntegrationEnvironment` enum (`SANDBOX | PREPROD`) is a *subset* of the registry — `PRODUCTION` is intentionally absent (FR-008). Cross-product gaps (ETA+SANDBOX, ZATCA+PREPROD) resolve to HTTP 404 by the existing active-only filter; documented in research.md §R3. |
| IV. Branch + Environment Segregation | PASS | No branch-scoped state introduced. Header tables retain their existing branch / environment FKs. |
| V. Stateless Service | PASS | Controllers, ingestion services, and the resolution service are stateless. The archive service writes-and-returns; no mutable in-memory state survives the request. |
| VI. Server-Side Cryptography | N/A | No signing, hashing, or QR generation. ZATCA / ETA chain values arrive *as data* in the payload; the platform stores them verbatim. |
| VII. Admin / Operational Mode Separation | **PASS** | The gateway resolves `(company_id, authority_environment_id)` from the request body BEFORE any operational repository write. At the point of any persistence call, `TenantContext.company_id` is non-null (populated per FR-009d), satisfying VII.4's literal reading. Resolution failures return HTTP 404 (`COMPANY_NOT_FOUND` / `AUTHORITY_ENVIRONMENT_NOT_FOUND`), never a null-context write. |
| **VIII. Transaction Module Architecture** | **PASS — with caveat** | The Sprint 1 `permitAll()` (FR-021) means no `user_company_transaction_roles` check runs on the gateway path. The seeded `INTEGRATION_GATEWAY` user (FR-009c) exists to populate `TenantContext.user_id` but has no role rows — by design, since granting it CREATE on every (company × env × transaction-type) tuple is precisely the partner-identity work Sprint 2 will perform via the API-key → partner mapping. RBAC re-enters automatically through the existing read paths (FR-023) when users view the ingested documents. |
| IX. Immutable Audit | PASS | FR-009b emits exactly one `audit_logs` row per successful ingest, action = `INGESTED` (or `CREATED`), `actor = INTEGRATION_GATEWAY`, tenancy columns matching the header. Append-only semantics preserved; nothing in the gateway path performs UPDATE or DELETE on `audit_logs`. |
| X. Deterministic Document Lifecycle | PASS | The inbound `IntegrationDocumentStatus` is mapped to the existing `DocumentState` by FR-016 (8 → 4). Invalid mapping is structurally impossible because the enum is exhaustive. No new lifecycle states added. |
| XI. Physical Document Table Strategy | PASS | Each endpoint writes to exactly its module's physical table group. No cross-table writes; no shared tables introduced beyond the new `inbound_payload_archive` (an *infrastructure* table, not a document table). |
| XII. ZATCA Hash Chain Integrity | PASS | The gateway *does not submit* — it stores chain values that the ERP already obtained from ZATCA (`invoice_counter_value`, `previous_invoice_hash`, `invoice_hash`, `qr_code_base64`). No pessimistic lock on `zatca_chain_state` is taken; the chain row is not consulted at all. The chain remains owned by the internal submission engine. |
| XIII. Validation Layering | PASS | Declarative DTO validation (Bean Validation 3.0 via `@Valid`, `@NotBlank`, `@Pattern`, `@DecimalMin`, etc.) is the first layer; service-layer cross-field rules (FR-011) are the second; the existing domain services + authority engines are the third. Backend remains the source of truth. |
| XIV. Angular Engineering | PASS | No new Angular screens. Ingested documents surface through the existing list/detail/artifact-download paths (FR-023). |
| XV. Spring Boot Engineering | **PASS** | Layered architecture preserved (controller → ingestion service → resolution service + repository). XV.6's `TenantContext` is fully populated for every gateway request per FR-009d: `company_id` (resolved), `authority_environment_id` (resolved), `authority` (derived from URL), `environment` (raw enum), `mode = OPERATIONAL`, `user_id = <FR-009c seeded UUID>`, `is_super_user = false`. XV.7's filter invariant holds verbatim — the resolved tuple is what gets written and what existing operational reads filter by. The Q1-locked `INTEGRATION_GATEWAY` literal continues to populate the author audit columns (FR-009a); the seeded users row (FR-009c) supplies the in-memory `TenantContext.user_id` — the two policies coexist without conflict. |
| XVI. Authority Adapter | PASS | `AuthorityEngine` abstraction is *not* touched — the gateway does not submit. ETA / ZATCA engines are bypassed entirely. |
| **XVII. RBAC and Access Control** | **PASS — with caveat** | `permitAll()` on `/api/integration/v1/**` (FR-021) is the *only* explicit RBAC bypass introduced by this feature, and it is bounded by locked Sprint 1 scope. The archive store (FR-OBS-004) introduces a *new* RBAC grant ("integration forensics") to restrict who can read partner payloads — this *strengthens* RBAC rather than weakening it. |
| XVIII. Plain-Text Certificate Storage Policy | N/A | No certificates touched. |
| XIX. Secure On-Prem Deployment | PASS | No deployment-topology change. The archive table lives on the same PostgreSQL instance with the same backup posture. |
| XX. Reliability and Recovery | PASS — **load-bearing** | FR-OBS-003 / FR-OBS-005 codify "archive before ingest" — the platform refuses any payload it cannot first archive (HTTP 5xx). This satisfies XX.1 ("Document state MUST be persisted before external submission begins") *literally* — though here the "external submission" already happened (it's the ERP's submission to the authority); we treat the inbound payload itself as the persistable artefact. XX.5 (no silent disappearance) is satisfied because every inbound request leaves an archive row regardless of outcome. |
| XXI. Document Preservation | PASS | Existing `invoice_artifacts` append-only invariants are preserved (we don't write to it from the gateway path). The new `inbound_payload_archive` is similarly append-only and immutable — no UPDATE or DELETE permitted (enforced at the application layer per the locked smart-app / dumb-DB pattern from 010). |
| XXII. Signed License Enforcement | N/A | Phase 2 only. |
| XXIII. Change Control | PASS | Feature is traceable to `Docs/sprint-1-ingestion-gateway-implementation-plan.md` (the implementation plan + the 18 locked decisions of 2026-05-25). Lifecycle / numbering / signing / payload-generation rules are untouched. |
| XXIV. Testing | PASS | Per-endpoint integration tests (happy-path, dedup, FR-011 cross-field, FR-OBS-003 archive coverage) + tenancy-isolation tests (Constitution XXIV.6) + the new SC-009 / SC-010 verifications. Authority-engine golden-file tests are *not affected* by this feature (no engine code changes). |
| **XXV. Performance** | **PASS — with caveat** | No per-endpoint SLO declared (Q5 → A). XXV.1's 5-second submission target is scoped to internal submission flows, not the gateway. XXV.2 (compound indexes on `(company_id, authority_environment_id)`) is already satisfied by V53/V57. The added archive-write on the hot path is acknowledged; if regressions appear, the Sprint 2 SLO feature will address them. |
| XXVI. Source of Truth | PASS | This plan honours the locked `spec.md` + `Docs/sprint-1-ingestion-gateway-implementation-plan.md`. |

**No unjustified violations.** Post the 2026-05-27 `/speckit-analyze` remediation (FR-009c + FR-009d), the constitution-check ledger narrows to **two** caveats (VIII, XVII) — both express the same root: Sprint 1's locked `permitAll()` decision. They converge in the Sprint 2 API-key feature. XXV remains an honest deferral (no SLO declared). Five principles previously caveated (VII, VIII, XV, XVII, XXV) are now: VII PASS, XV PASS, XVII caveat, VIII caveat, XXV caveat.

## Project Structure

### Documentation (this feature)

```text
specs/011-erp-ingestion-gateway/
├── spec.md                       # Locked feature spec with Session 2026-05-27 clarifications
├── plan.md                       # This file
├── research.md                   # Phase 0 — auth posture, archive-write transactional pattern, MDC plumbing, environment cross-product
├── data-model.md                 # Phase 1 — DTO record tree, mapping tables to existing entities, the new InboundPayloadArchive entity
├── quickstart.md                 # Phase 1 — local-dev path: prerequisites, seed company, sample curl per endpoint, OpenAPI smoke check
├── contracts/
│   ├── openapi.yaml              # OpenAPI 3.1 spec — the four POST endpoints with shared component schemas (enums, response, exceptions, party DTOs)
│   └── error-codes.md            # Catalogue of every error code FR-001..024 + FR-OBS-* can surface, mapped to HTTP status
└── tasks.md                      # Phase 2 — generated by /speckit-tasks (NOT created here)
```

### Source Code (repository root)

```text
platform-api/
├── pom.xml                                                            # Add springdoc-openapi-starter-webmvc-ui:2.8.8
├── src/main/java/com/einvoice/api/
│   ├── integration/
│   │   ├── EtaIngestionController.java                                # NEW — 2 endpoints (receipts, invoices)
│   │   ├── ZatcaIngestionController.java                              # NEW — 2 endpoints (standard, simplified)
│   │   ├── dto/
│   │   │   ├── shared/
│   │   │   │   ├── IntegrationEnvironment.java                        # NEW enum: SANDBOX, PREPROD
│   │   │   │   ├── IntegrationDocumentStatus.java                     # NEW enum: 8 values (FR-016)
│   │   │   │   └── DocumentIngestionResponse.java                     # NEW record: {id, documentNumber, erpReferenceId, internalStatus, message}
│   │   │   ├── eta/
│   │   │   │   ├── EtaReceiptIngestionRequest.java                    # NEW — SDK v1.2 shape, ~12 nested records
│   │   │   │   └── EtaInvoiceIngestionRequest.java                    # NEW — SDK v1.0 shape, ~5 nested records
│   │   │   └── zatca/
│   │   │       ├── ZatcaStandardInvoiceIngestionRequest.java          # NEW — UBL shape, buyer required, ~3 nested records
│   │   │       └── ZatcaSimplifiedInvoiceIngestionRequest.java        # NEW — UBL shape, buyer optional, ~3 nested records
│   │   ├── service/
│   │   │   ├── CompanyResolutionService.java                          # NEW — (regNumber, authority, environment) → (company_id, authority_environment_id)
│   │   │   ├── EtaIngestionService.java                               # NEW — orchestrates resolve → dedup → archive lookup → save → audit
│   │   │   ├── ZatcaIngestionService.java                             # NEW — same, for the two ZATCA paths
│   │   │   └── InboundPayloadArchiveService.java                      # NEW — REQUIRES_NEW write before main ingest tx (FR-OBS-003)
│   │   └── filter/
│   │       └── IngestionPayloadArchiveFilter.java                     # NEW — OncePerRequestFilter: reads body, writes archive row, sets MDC payloadArchiveId, caches body via ContentCachingRequestWrapper
│   ├── config/
│   │   └── OpenApiConfig.java                                         # NEW — GroupedOpenApi `integration-gateway`
│   └── error/
│       └── GlobalExceptionHandler.java                                # EDIT — add handlers for CompanyNotFoundException + AuthorityEnvironmentNotFoundException
│
platform-core/
├── src/main/java/com/einvoice/core/
│   ├── error/
│   │   ├── CompanyNotFoundException.java                              # NEW — code COMPANY_NOT_FOUND, 404
│   │   └── AuthorityEnvironmentNotFoundException.java                 # NEW — code AUTHORITY_ENVIRONMENT_NOT_FOUND, 404
│   ├── domain/ingestion/
│   │   ├── entity/InboundPayloadArchive.java                          # NEW JPA entity — backs the V62 table
│   │   └── repository/InboundPayloadArchiveRepository.java            # NEW — single save() + findById() for forensics
│   └── repository/
│       ├── company/CompanyRepository.java                             # EDIT — add findByTaxNumberAndIsActiveTrue(String)
│       └── zatca/
│           ├── ZatcaStandardHeaderRepository.java                     # EDIT — add existsByCompanyIdAndAuthorityEnvironmentIdAndInvoiceNumber + findBy... for orig-doc resolve
│           └── ZatcaSimplifiedHeaderRepository.java                   # EDIT — same pair
└── src/main/resources/db/migration/
    └── V62__inbound_payload_archive.sql                               # NEW — single new table + BRIN index on received_at + the "integration_forensics" role grant
│
platform-security/
└── src/main/java/com/einvoice/security/
    └── SecurityConfig.java                                            # EDIT — add `.requestMatchers("/api/integration/v1/**").permitAll()` before anyRequest().authenticated()
│
backend/
└── src/test/java/com/einvoice/api/integration/
    ├── EtaReceiptIngestionIT.java                                     # NEW — US1 acceptance scenarios (happy, dedup, validation, uuid)
    ├── EtaInvoiceIngestionIT.java                                     # NEW — US2 (happy, credit-note known/unknown ref, missing-orig, status mapping)
    ├── ZatcaStandardIngestionIT.java                                  # NEW — US3 (happy, transactionTypeCode pattern, exemption-reason, anonymous-buyer-rejected, VAT pattern)
    ├── ZatcaSimplifiedIngestionIT.java                                # NEW — US4 (happy anonymous, dedup, wrong-shape transactionTypeCode)
    ├── ObservabilityIT.java                                           # NEW — SC-009 archive coverage, SC-010 audit log row
    ├── TenancyIsolationIT.java                                        # NEW — Constitution XXIV.6 across ingested rows
    └── OpenApiSurfaceIT.java                                          # NEW — SC-008 group + enum values
│
frontend/                                                              # NO CHANGES — read-side reuses existing list/detail/artifact paths (FR-023)
```

**Structure Decision**: The existing four-module layout (`platform-api`, `platform-core`, `platform-security`, plus `frontend/`) is retained. All new ingestion code lives under `platform-api/.../integration/` so the layer boundary is enforced by package: controllers + DTOs + ingestion services + the archive filter belong to the API module; entities, repositories, and exceptions live in `platform-core`; the `permitAll()` rule lives in `platform-security`. The single new Flyway migration (V62) lives next to V58–V61 in `platform-core/src/main/resources/db/migration/`. No new top-level module is created.

## Complexity Tracking

| Caveat | Why Needed | Simpler Alternative Rejected Because |
|---|---|---|
| **`permitAll()` on `/api/integration/v1/**` (VIII, XVII)** | Sprint 1 is explicitly scoped to ship the four endpoints without authentication, behind a network envelope (private VPC / allowlist). Q3 + locked decisions in the source plan §10 freeze this. The seeded `INTEGRATION_GATEWAY` user (FR-009c) exists to populate `TenantContext.user_id` but is intentionally unpermissioned — no `user_company_transaction_roles` rows for it. Sprint 2's API-key feature introduces partner identity AND the role grants that close VIII. | API-key / HMAC authentication needs a per-partner identity table, secret rotation, and replay protection — a meaningful feature in its own right. Bundling it with Sprint 1 doubles the surface and delays validator-clean ingest. Sprint 2 is reserved for this. |
| **No per-endpoint latency SLO (XXV)** | Constitution XXV.1's 5-second target is scoped to internal *submission*, not gateway ingest. The user explicitly declined a per-endpoint SLO at Q5 to keep Sprint 1 scope tight and let real traffic shape the target. | A premature SLO (e.g., p95 ≤ 500 ms) without representative load data would either be vacuously satisfied or force premature optimisation. The Sprint 2 SLO feature will set targets against measured traffic. |

**Resolved by 2026-05-27 `/speckit-analyze` remediation** (no longer caveated):
- *Constitution VII* — Resolved by FR-009d. `TenantContext.company_id` is non-null at every operational write call site (resolved per FR-006 before persistence); resolution failures return 404 before any operational repository touches the DB.
- *Constitution XV* — Resolved by FR-009c + FR-009d. The seeded `INTEGRATION_GATEWAY` user row supplies the `TenantContext.user_id` that XV.6 mandates; all seven `TenantContext` fields are populated for every gateway request.
- *`INTEGRATION_GATEWAY` literal in author columns* — Not a caveat: the literal is the Q1-locked author value (FR-009a), parallel to and distinct from the seeded user row's UUID consumed by `TenantContext.user_id`. The two policies coexist without constitution tension.
- *No payload-size / line-count / rate-limit caps* — Not a constitution conflict; XVII speaks to permission rules, not abuse limits. Q3's deferral stands as an explicit Sprint 1 product decision (FR-021a), not a constitution caveat.

These caveats are **explicitly bounded by Sprint 1 scope** and converge in the same Sprint 2 follow-up (API-key authentication + per-partner identity + abuse limits + concrete SLOs). They are tracked together so the constitution-check ledger reads cleanly for that feature.
