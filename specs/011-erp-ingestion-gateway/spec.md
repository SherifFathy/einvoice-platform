# Feature Specification: ERP Ingestion Gateway

**Feature Branch**: `011-erp-ingestion-gateway`
**Created**: 2026-05-27
**Status**: Draft
**Input**: User description: "ERP ingestion gateway — four REST endpoints that let ERP clients push externally-submitted e-invoice documents (ETA invoices, ETA receipts, ZATCA Standard, ZATCA Simplified) into the platform for archival, status tracking, and reporting. No internal submission to ETA/ZATCA happens in this feature."

**Source documents** (authoritative for scope and resolved decisions):
- `Docs/sprint-1-ingestion-gateway-implementation-plan.md` — full implementation plan with DTO shapes, entity mappings, exception flows, and the locked decisions of 2026-05-25 (see §10).
- `specs/010-authority-spec-alignment/spec.md` — schema-alignment refactor (V58–V61) that this feature builds on. Feature 011 must NOT begin until V58–V61 land and the paired Wave 7/8 entity/service updates are merged.
- `Docs/eta-receipt-sdk-v1-2-alignment.md` — ETA Receipt SDK v1.2 DTO shape.
- `Docs/zatca-spec-alignment.md` — ZATCA Fatoora SDK v2.0.3 DTO shape.

## Clarifications

### Session 2026-05-27

- Q: What value populates `created_by`, `updated_by`, and (where applicable) `submitted_by` on rows inserted through the gateway, given there is no authenticated principal? → A: A fixed literal `INTEGRATION_GATEWAY` stamped into all author/submitter columns on every ingested header, line, and line-tax row. No schema change is required (the columns remain whatever shape Wave 7/8 left them — nullable or NOT NULL with this literal as their value). When API-key authentication ships in Sprint 2, the resolved partner identifier replaces the literal at the same call sites; schema unchanged.
- Q: Does each successful ingest emit an `audit_logs` row, and if so what shape? → A: Yes — exactly one row per successful ingest. Action = `INGESTED` if the existing V52 allow-list trigger accepts the new value; otherwise reuse `CREATED`. Actor = `INTEGRATION_GATEWAY` (matches FR-009a). Tenancy columns (`company_id`, `authority_environment_id`) match the inserted header row. Payload references the new header id and echoes the supplied `erpReferenceId`. No audit-log row is written for validation-failed or duplicate-rejected requests; those are observable from application logs only.
- Q: Does the gateway declare request-body, line-count, or rate-limit caps on the unauthenticated ingestion endpoints? → A: No. Sprint 1 declares no payload-size, line-count, or rate-limit caps. The endpoints inherit Spring Boot's and embedded Tomcat's default request limits, and downstream DB column lengths bound the per-field maximum. Abuse mitigation is explicitly deferred — to be revisited (with concrete numbers) when API-key authentication lands in Sprint 2 or earlier if real traffic shows abuse. Operationally, the network envelope (private VPC / allowlist) is the only gate until then.
- Q: What is the observability contract for each ingest call — log shape, payload retention, metrics? → A: Two-part contract. (1) **Structured logging:** one INFO log line per ingest (success and failure) with MDC fields `endpoint`, `companyTaxNumber`, `environment`, `documentNumber`, `erpReferenceId`, `outcome` (HTTP status), `latencyMs`, and `payloadArchiveId`. Default Spring Boot Actuator metrics only; no custom Micrometer counters in this feature. (2) **Inbound payload archive:** every inbound request (regardless of outcome — success, validation failure, duplicate, resolution failure) MUST persist its raw JSON body into a dedicated `inbound_payload_archive` store (a new RDBMS table is the default; blob storage is an acceptable implementation alternative as long as the contract holds). The archive write is **not** part of the ingest transaction — it MUST succeed (or fail loudly with a 5xx) BEFORE the main ingest transaction begins, so payloads from rolled-back, rejected, or unauthorised requests remain available for forensics. The archive row stores: the generated archive id (returned to the log line via MDC), the request body bytes, the endpoint path, the receive timestamp, the resolved `company_id` and `authority_environment_id` when resolution succeeded (NULL otherwise), and the outcome stamped on completion. Access to the archive store MUST be RBAC-restricted as it may contain partner business data; retention policy is deferred to a follow-up feature.
- Q: Does the gateway declare a per-endpoint performance target (p95/p99 latency, throughput)? → A: No. This feature declares no per-endpoint latency or throughput target. Performance is observed at the platform level via default Actuator HTTP metrics (per FR-OBS-002), and any regression beyond the existing internal-write paths is investigated reactively. Concrete SLOs are deferred to a follow-up "load testing & SLOs" feature; until then there is no CI assertion on ingest latency.
- Q: Does Sprint 1 ship any authentication for the integration endpoints? → A: No. The four `/api/integration/v1/**` routes are `permitAll()` for this feature. API-key / HMAC authentication is a Sprint 2 concern.
- Q: Is idempotent re-submission in scope? → A: No. Re-POSTing the same document number for the same (company, authority_environment) returns HTTP 409 Conflict via the existing `Duplicate*Exception` classes. Idempotent retry semantics are a future-sprint concern.
- Q: What does `companyRegistrationNumber` in the request body resolve against? → A: `companies.tax_number` for both ETA and ZATCA. ZATCA's 15-digit VAT number is stored in `tax_number`; `cr_number` is informational only.
- Q: When a credit / debit note references an `originalInvoiceNumber` the platform has never seen (because the original was filed outside the gateway), what happens? → A: Hybrid resolution. The service always stores the raw string in a new `original_invoice_number VARCHAR(100)` column on every header table, and populates the existing `original_*_id` FK only when a matching row is found locally. Ingestion never fails for an unknown reference.
- Q: Who computes ETA Receipt `header.uuid` (the SHA-256 hex string)? → A: The ERP. The gateway validates format only (`[a-fA-F0-9]{64}`). No canonicalisation algorithm is implemented in the platform.
- Q: Which environments does the gateway accept? → A: `SANDBOX` and `PREPROD` only. `PRODUCTION` is intentionally absent from the `IntegrationEnvironment` enum — Jackson rejects unrecognised values with HTTP 400 automatically. Production cutover is a separate gate.
- Q: Does the gateway perform any submission to ETA / ZATCA, or call any external authority API? → A: No. Documents arrive already-submitted-externally. The gateway only persists them, maps the ERP-reported status to the internal `DocumentState`, and exposes them through the existing read paths. Authority submission engines (Wave 7/8) are not invoked by this feature.
- Q: Are batch / multi-document endpoints in scope? → A: No. Each call carries exactly one document. Batch endpoints, webhooks/callbacks, and dashboard UI for ingested documents are explicitly deferred.

## Overview

Four POST endpoints under `/api/integration/v1/` let an ERP push exactly one externally-submitted document per call into the platform's existing operational tables:

| # | Endpoint | Target tables |
|---|---|---|
| 1 | `POST /api/integration/v1/eta/receipts` | `eta_receipt_headers` + `eta_receipt_lines` |
| 2 | `POST /api/integration/v1/eta/invoices` | `eta_invoice_headers` + `eta_invoice_lines` + `eta_invoice_line_taxes` |
| 3 | `POST /api/integration/v1/zatca/standard` | `zatca_standard_headers` + `zatca_standard_lines` |
| 4 | `POST /api/integration/v1/zatca/simplified` | `zatca_simplified_headers` + `zatca_simplified_lines` |

Each request carries (a) a `companyRegistrationNumber` + `environment` pair the gateway resolves into `(company_id, authority_environment_id)`, (b) an authority-specific document payload shaped to match the official SDK (ETA Receipt SDK v1.2 / ETA Invoice SDK v1.0 / ZATCA Fatoora SDK v2.0.3), (c) an `erpReferenceId` echoed back in the response and persisted on the new V58 column, and (d) a `status` enum the service maps to the platform's `DocumentState` lifecycle.

Successful ingest returns HTTP 201 with `{id, documentNumber, erpReferenceId, internalStatus, message}`. Once persisted, ingested documents are indistinguishable from documents produced by the internal UI for the purposes of search, reporting, audit, and artifact download — they flow through the existing repositories and read paths unchanged.

## User Scenarios & Testing *(mandatory)*

### User Story 1 — ERP pushes an ETA receipt that has already been accepted by ETA (Priority: P1)

A retail-POS ERP submits a receipt to ETA, gets a `VALID` response and an ETA-generated UUID, and posts the full receipt payload (matching SDK v1.2 root-level fields: `header`, `documentType`, `seller`, `buyer`, `itemData`, totals, `taxTotals`, `paymentMethod`) plus `companyRegistrationNumber=<seller-tax-number>`, `environment=PREPROD`, `status=VALID`, `erpReferenceId=<ERP-side ID>` to `POST /api/integration/v1/eta/receipts`. The platform resolves the company and environment, dedupes against `(company_id, authority_environment_id, receipt_number)`, persists the receipt with `state=ACCEPTED`, and returns 201.

**Why this priority**: ETA receipts are the highest-volume document for the platform's POS-driven customer segment. Without this endpoint, ERP integrations cannot land any data, which blocks every downstream feature (reporting, dashboard, audit re-query).

**Independent Test**: With a known active company whose `tax_number` exists in `companies`, POST a single SDK v1.2-shaped receipt with `status=VALID`. Verify HTTP 201, response contains a new `id`, and a SELECT against `eta_receipt_headers` shows the row with `state=ACCEPTED`, `eta_receipt_uuid` populated from `header.uuid`, and `erp_reference_id` populated. Lines and JSONB party blocks are populated.

**Acceptance Scenarios**:

1. **Given** an active company with `tax_number=100200300` and an active `(ETA, PREPROD)` authority environment, **When** the ERP posts a valid receipt with `companyRegistrationNumber=100200300`, `environment=PREPROD`, `status=VALID`, **Then** the response is HTTP 201 and `eta_receipt_headers` contains a new row with `state=ACCEPTED` and the supplied `erp_reference_id`.
2. **Given** the same payload is posted a second time with the same `header.receiptNumber`, **When** the request reaches the service, **Then** the response is HTTP 409 with code `DUPLICATE_RECEIPT_NUMBER`; no second row is inserted.
3. **Given** a payload that omits `header.receiptNumber`, **When** validation runs, **Then** the response is HTTP 400 with a field-level error pointing to `header.receiptNumber`.
4. **Given** a payload with `header.uuid` that is not 64 hex chars, **When** validation runs, **Then** the response is HTTP 400 with a regex-pattern error.

---

### User Story 2 — ERP pushes an ETA tax invoice (B2B), including credit/debit notes (Priority: P1)

A finance ERP submits a B2B invoice (or credit note / debit note) to ETA, receives `etaUuid`/`etaLongId`/`etaSubmissionId`, and posts the full invoice payload shaped to ETA Invoice SDK v1.0 (`seller`/`buyer` as `TaxpayerParty` records, lines with `UnitValue` and `LineTax` nested records) plus the ETA reference triple. Credit / debit notes additionally include `originalInvoiceNumber`. The gateway always stores the raw string; when the referenced invoice exists in the same `(company_id, authority_environment_id)`, the FK `original_document_id` is also populated.

**Why this priority**: B2B invoices are the second-largest document class by count and the largest by value. Credit/debit-note handling is required because most accounting cycles need return processing and price-correction flows on day one.

**Independent Test**: POST a valid invoice with `documentType=I`, then POST a follow-up credit note with `documentType=C` and the previous invoice's `invoiceNumber` as `originalInvoiceNumber`. Verify both return 201; the credit-note row has `original_document_id` populated; the underlying invoice row is unchanged. Then POST a credit note whose `originalInvoiceNumber` is unknown — verify 201 with `original_invoice_number` populated and `original_document_id` NULL.

**Acceptance Scenarios**:

1. **Given** a valid invoice payload with `documentType=I`, **When** posted, **Then** response is HTTP 201; row appears with `etaUuid`/`etaLongId`/`etaSubmissionId` populated.
2. **Given** a credit note with `documentType=C` referencing a known prior invoice, **When** posted, **Then** response is 201, `original_document_id` is set, `original_invoice_number` is set, and totals/lines persist.
3. **Given** a credit note with `documentType=C` and `originalInvoiceNumber` missing, **When** posted, **Then** response is HTTP 400 with code `MISSING_ORIGINAL_DOCUMENT`.
4. **Given** a credit note referencing an `originalInvoiceNumber` not present locally, **When** posted, **Then** response is 201; `original_invoice_number` is set; `original_document_id` is NULL.
5. **Given** `status=INVALID`, **When** persisted, **Then** the row's `state` is `REJECTED`.

---

### User Story 3 — ERP pushes a ZATCA Standard B2B invoice that has been cleared (Priority: P1)

A Saudi-VAT ERP submits a Standard tax invoice to ZATCA Fatoora, receives a clearance status with `invoiceCounterValue`, `previousInvoiceHash`, `invoiceHash`, `qrCodeBase64`, and `clearanceStatus=CLEARED`, then posts the full UBL-shaped payload (`seller`/`buyer` 15-digit VATs, `lineExtensionAmount`/`allowanceTotalAmount`/`taxExclusiveAmount`/`taxAmount`/`taxInclusiveAmount`/`prepaidAmount`/`payableAmount` legal-monetary-total block, per-line `vatCategoryCode`/`vatRate`/`vatAmount`, exemption reason fields where applicable) to `POST /api/integration/v1/zatca/standard` with `status=CLEARED`. The platform persists the header, the lines, and the chain/QR fields, and surfaces the document through the existing ZATCA read paths.

**Why this priority**: Standard invoices unblock the Saudi-side launch. Without ingestion, no B2B ZATCA document can be archived through the platform.

**Independent Test**: POST a Standard payload with one S-rated line and one E-exempt line (the latter carrying `exemptionReasonCode` + `exemptionReasonText`). Verify 201, `zatca_standard_headers` row has `clearance_status=CLEARED`, `invoice_counter_value`, `previous_invoice_hash`, `invoice_hash`, `qr_code_base64` populated, and `zatca_standard_lines` has both line rows.

**Acceptance Scenarios**:

1. **Given** a valid Standard payload with `transactionTypeCode` matching the Standard pattern (`1[01]{3}0000`), **When** posted, **Then** response is HTTP 201.
2. **Given** a payload with `transactionTypeCode` starting with `0` (Simplified shape), **When** posted to the Standard endpoint, **Then** response is HTTP 400 with a regex-pattern error.
3. **Given** a line with `vatCategoryCode=E` but missing `exemptionReasonCode` or `exemptionReasonText`, **When** validated, **Then** response is HTTP 400 with code `VAT_EXEMPTION_REASON_REQUIRED`.
4. **Given** `buyer=null` (anonymous), **When** posted to the Standard endpoint, **Then** response is HTTP 400 (Standard requires a buyer).
5. **Given** `vatNumber` not matching the Saudi 15-digit pattern `3[0-9]{14}`, **When** validated, **Then** response is HTTP 400.

---

### User Story 4 — ERP pushes a ZATCA Simplified B2C invoice that has been reported (Priority: P1)

A Saudi POS ERP submits a Simplified invoice to ZATCA's reporting API, receives `reportingStatus=REPORTED` plus chain/QR fields, and posts the payload to `POST /api/integration/v1/zatca/simplified`. The buyer object is permitted to be entirely absent (anonymous retail). The platform persists the document the same way as Standard, except it writes `reportingStatus` rather than `clearanceStatus`.

**Why this priority**: Simplified is the dominant document class for POS retail. The anonymous-buyer flow is uniquely required here and absent from the other three endpoints.

**Independent Test**: POST a Simplified payload with `buyer` omitted. Verify 201, `zatca_simplified_headers` row created with `reporting_status=REPORTED`, no buyer fields populated in the JSONB block.

**Acceptance Scenarios**:

1. **Given** a valid Simplified payload with `transactionTypeCode` matching `0[01]{3}0000` and `buyer` omitted, **When** posted, **Then** response is HTTP 201.
2. **Given** the same payload sent twice with the same `invoiceNumber`, **When** the second request is processed, **Then** response is HTTP 409 with `DUPLICATE_SIMPLIFIED_NUMBER`.
3. **Given** `transactionTypeCode` starting with `1` (Standard shape) on the Simplified endpoint, **When** validated, **Then** response is HTTP 400.

---

### User Story 5 — ERP integrator discovers, explores, and tests the API contract from a generated OpenAPI spec (Priority: P2)

An external ERP developer hits `/swagger-ui/index.html` and `/v3/api-docs`, sees the four ingestion endpoints documented with full request schemas (including the nested SDK records and the enum value sets for `IntegrationEnvironment` and `IntegrationDocumentStatus`), uses the Try-It-Out feature against a local dev backend, and produces a working integration without needing to read Java sources.

**Why this priority**: Reduces integration time per ERP partner. Not blocking, but the absence of generated docs forces every onboarding into bespoke handholding.

**Independent Test**: Start the backend locally, open `/swagger-ui/index.html`, confirm an `integration-gateway` group lists the four POST endpoints with their request/response schemas. Execute a Try-It-Out call against a known seed company; verify 201.

**Acceptance Scenarios**:

1. **Given** the backend is running, **When** an integrator visits `/swagger-ui/index.html`, **Then** the four integration endpoints are listed under an `integration-gateway` group with full request models.
2. **Given** an integrator inspects the OpenAPI JSON, **When** they read the `IntegrationEnvironment` enum schema, **Then** only `SANDBOX` and `PREPROD` are listed (no `PRODUCTION`).

---

### Edge Cases

- **Unknown `companyRegistrationNumber`** → HTTP 404 with code `COMPANY_NOT_FOUND` and `registrationNumber` echoed in the error payload.
- **Inactive company (`is_active = false`)** → HTTP 404 with code `COMPANY_NOT_FOUND` (active filter is part of the resolver).
- **`environment=PRODUCTION` sent in JSON** → HTTP 400 (Jackson unrecognised-enum error); no service code reached.
- **No active `authority_environments` row for the requested `(authority, environment)` pair** → HTTP 404 with code `AUTHORITY_ENVIRONMENT_NOT_FOUND`.
- **ETA Receipt `buyer.type=B` but `buyer.id` or `buyer.name` null** → HTTP 400 from service-layer cross-field check.
- **ETA Receipt `buyer.type=P` with `totalAmount ≥ 150_000 EGP` and `buyer.id`/`buyer.name` null** → HTTP 400 from service-layer cross-field check.
- **Payload validation failure (any `@NotBlank`/`@NotNull`/`@Pattern`/`@Size`/`@DecimalMin`/`@DecimalMax` violation)** → HTTP 400 with code `VALIDATION_ERROR` and per-field error list; no partial persistence.
- **Persistence failure mid-save (constraint violation, FK miss, etc.)** → transaction rolls back; nothing is left in `*_headers`, `*_lines`, or `*_line_taxes`. Returned status depends on the existing `GlobalExceptionHandler` mapping for the underlying exception.
- **Status `CANCELLED`** persists with `state=CANCELLED`; the document is queryable but excluded from standard active-document reports per existing read-side conventions.
- **Inbound payload archive write failure** (storage unavailable, disk full) → HTTP 5xx; the main ingest transaction is NEVER reached. The platform refuses to accept any payload it cannot first archive, by design (FR-OBS-005).

## Requirements *(mandatory)*

### Functional Requirements

**Endpoints and contract**

- **FR-001**: The system MUST expose `POST /api/integration/v1/eta/receipts` accepting a JSON payload shaped to ETA Receipt SDK v1.2 (root: `header`, `documentType`, `seller`, `buyer`, `itemData`, totals, `taxTotals`, `paymentMethod`, optional `contractor`/`beneficiary`) plus the four gateway-routing fields (`companyRegistrationNumber`, `environment`, `status`, `erpReferenceId`). Successful ingest returns HTTP 201 with `DocumentIngestionResponse`.
- **FR-002**: The system MUST expose `POST /api/integration/v1/eta/invoices` accepting a JSON payload shaped to ETA Invoice SDK v1.0 (header fields, `seller`/`buyer` `TaxpayerParty`, `lines[]` with `UnitValue` + `LineTax` children, optional ETA-generated reference triple, optional `originalInvoiceNumber`) plus the four gateway-routing fields. Returns 201 with `DocumentIngestionResponse`.
- **FR-003**: The system MUST expose `POST /api/integration/v1/zatca/standard` accepting a UBL-shaped Standard invoice payload with the legal-monetary-total block, `seller`/`buyer` 15-digit VAT parties, `lines[]` with VAT category/rate/amount and exemption reason fields where applicable, optional chain/QR fields, optional `originalInvoiceNumber`, plus the four gateway-routing fields. `buyer` is required (Standard is B2B). Returns 201.
- **FR-004**: The system MUST expose `POST /api/integration/v1/zatca/simplified` accepting a payload identical in shape to Standard except `buyer` is optional and the status field is named `reportingStatus`. Returns 201.
- **FR-005**: Every successful ingest response MUST include the platform-generated `id` (UUID of the new header row), the `documentNumber` echoed from the payload, the `erpReferenceId` echoed back unchanged, the resolved `internalStatus` (the post-mapping `DocumentState`), and a human-readable `message`.

**Resolution and isolation**

- **FR-006**: The system MUST resolve `companyRegistrationNumber` against `companies.tax_number` (with `is_active = true`) for both ETA and ZATCA paths. If no active row is found, the system MUST return HTTP 404 with code `COMPANY_NOT_FOUND` and echo the supplied registration number in the error payload.
- **FR-007**: The system MUST resolve `environment` (combined with the implicit authority of each endpoint — `ETA` or `ZATCA`) against `authority_environments` (with `is_active = true`). If no row matches, return HTTP 404 with code `AUTHORITY_ENVIRONMENT_NOT_FOUND` and echo the supplied authority and environment.
- **FR-008**: The system MUST accept only `SANDBOX` and `PREPROD` for the `environment` field. The `PRODUCTION` value MUST NOT be deserialisable; an attempt MUST result in HTTP 400 from Jackson.
- **FR-009**: Persisted rows MUST carry the resolved `company_id` and `authority_environment_id` so they are isolated by the existing tenancy filters (Constitution Principles II / III).
- **FR-009a**: The system MUST stamp the literal `INTEGRATION_GATEWAY` into `created_by`, `updated_by`, and (where the table has it) `submitted_by` on every header, line, and line-tax row produced by the four ingestion endpoints. This MUST NOT require any schema change: the literal is written verbatim regardless of whether the underlying column is nullable. Sprint 2's API-key authentication replaces this literal with the resolved partner identifier at the same call sites without altering the schema.
- **FR-009b**: Each successful ingest MUST emit exactly one row into `audit_logs` within the same transaction as the header insert. The row carries: action = `INGESTED` (locked — see FR-009e for the V62 migration's allow-list trigger extension), actor = `INTEGRATION_GATEWAY`, `company_id` and `authority_environment_id` matching the inserted header, and a JSONB payload referencing the new header id and the supplied `erpReferenceId`. Validation-failed (HTTP 400) and duplicate-rejected (HTTP 409) requests MUST NOT write to `audit_logs`; they are observable only through application logs.
- **FR-009c**: The V62 migration MUST seed exactly one `users` row representing the gateway's system principal: `username = INTEGRATION_GATEWAY`, `is_active = false` (prevents UI login), `is_super_user = false`, password column NULL (or whatever sentinel the existing schema requires to forbid authentication). The row's `id` is the UUID consumed by FR-009d. This row exists to satisfy Constitution §XV.6 (`TenantContext` MUST carry `user_id`); it MUST NOT appear in any list of selectable users in Admin Mode.
- **FR-009d**: For every gateway request that reaches an ingestion service, the platform MUST populate `TenantContext` with: `company_id` (resolved per FR-006), `authority_environment_id` (resolved per FR-007), `authority` (the literal `ETA` or `ZATCA` derived from the endpoint path), `environment` (raw enum from the request), `mode = OPERATIONAL`, `user_id = <UUID of the FR-009c seeded row>`, `is_super_user = false`. This satisfies Constitution §XV.6 verbatim. Author / submitted-by columns continue to receive the literal `INTEGRATION_GATEWAY` per FR-009a (the two policies coexist: TenantContext for in-memory plumbing, the literal string for the author audit columns).
- **FR-009e**: The V62 migration MUST extend the existing V52 `audit_logs` allow-list trigger to accept the new action value `INGESTED`. This is a single `ALTER FUNCTION` (or equivalent — depending on how V52 expressed the allow-list) and is part of the same migration as the archive table and the FR-009c seeded user row, so the three changes ship atomically.

**Validation**

- **FR-010**: The system MUST enforce structural validation declaratively on the request DTOs (`@NotBlank`, `@NotNull`, `@Size`, `@Pattern`, `@DecimalMin`, `@DecimalMax`, `@Valid` on nested records). Validation failures return HTTP 400 with code `VALIDATION_ERROR` and a per-field error list. No service code runs on a structurally invalid request.
- **FR-011**: The system MUST enforce these service-layer cross-field rules with their existing exceptions:
  - ETA Invoice `documentType ∈ {C, D}` requires non-null `originalInvoiceNumber` → `MissingOriginalDocumentException`.
  - ZATCA `invoiceTypeCode ∈ {381, 383}` requires non-null `originalInvoiceNumber` → `MissingOriginalDocumentException`.
  - ZATCA line `vatCategoryCode ∈ {E, O}` requires both `exemptionReasonCode` and `exemptionReasonText` → `VatExemptionReasonRequiredException`.
  - ETA Receipt `buyer.type = B` requires `buyer.id` and `buyer.name`; `buyer.type = P` AND `totalAmount ≥ 150_000 EGP` requires the same.
- **FR-012**: ETA Receipt `header.uuid` MUST be validated against `^[a-fA-F0-9]{64}$`. The platform MUST NOT compute or canonicalise the UUID — only the format is checked.
- **FR-013**: ZATCA `transactionTypeCode` MUST be `1[01]{3}0000` on the Standard endpoint and `0[01]{3}0000` on the Simplified endpoint (pattern enforced declaratively).

**Deduplication**

- **FR-014**: For each endpoint the system MUST check for an existing row in the relevant header table keyed by `(company_id, authority_environment_id, <document_number>)` BEFORE inserting. If found, return HTTP 409 with the existing code:
  - ETA Receipt → `DUPLICATE_RECEIPT_NUMBER`
  - ETA Invoice → `DUPLICATE_INVOICE_NUMBER`
  - ZATCA Standard → `DUPLICATE_STANDARD_NUMBER`
  - ZATCA Simplified → `DUPLICATE_SIMPLIFIED_NUMBER`
- **FR-015**: Idempotent re-submission (return existing row on duplicate) is OUT OF SCOPE.

**Status mapping**

- **FR-016**: The system MUST map the inbound `IntegrationDocumentStatus` to the internal `DocumentState` per this table; no other values may produce a successful ingest:
  - `DRAFT` → `DRAFT`
  - `VALID` | `CLEARED` | `REPORTED` → `ACCEPTED`
  - `INVALID` | `REJECTED` | `FAILED` → `REJECTED`
  - `CANCELLED` → `CANCELLED`

**Reference handling**

- **FR-017**: For credit/debit notes carrying `originalInvoiceNumber`, the system MUST always persist the raw string into a new `original_invoice_number VARCHAR(100)` column on every header table. The system MUST additionally populate the existing `original_*_id` FK column WHEN a matching row exists in the same `(company_id, authority_environment_id)` for the same document type; otherwise leave the FK NULL and continue. Ingest MUST NOT fail because the referenced original is unknown.

**Persistence**

- **FR-018**: Each ingest call MUST be transactional. The full save (header + child lines + child line-taxes where applicable) MUST be atomic. Any failure during line persistence MUST roll back the header insert.
- **FR-019**: The system MUST persist the supplied `erpReferenceId` (when present, length ≤ 100) into the V58 `erp_reference_id` column on the header row. This column MUST also exist in the read-side path (no separate query layer; reuse existing repositories).
- **FR-020**: Structured DTO records that map to existing JSONB columns (`seller_data`, `buyer_data`, `unit_value` where retained per V60) MUST be converted to `Map<String, Object>` preserving field order and field names exactly as the SDK / spec defines, so that downstream serialisers and the existing read-side rendering remain unchanged.

**Security and surfacing**

- **FR-021**: All four endpoints MUST be reachable without authentication for this feature (`permitAll()` in `SecurityConfig` on `/api/integration/v1/**`). API-key / HMAC authentication is OUT OF SCOPE and reserved for a follow-up feature.
- **FR-021a**: The gateway MUST NOT declare any feature-specific request-body, line-count, or rate-limit caps in Sprint 1. Default Spring Boot / embedded Tomcat request limits apply unchanged, and downstream DB column lengths bound per-field maxima. Payload-size, line-count, and rate-limit policy are explicitly deferred to the Sprint 2 API-key feature (or sooner if abuse appears); until then the network envelope (private VPC / allowlist) is the only abuse gate.

**Observability**

- **FR-OBS-001**: Each ingest call MUST emit exactly one structured INFO log line at the controller boundary, written for both success (HTTP 201) and failure (HTTP 400 / 404 / 409 / 5xx) paths. The line MUST include the following MDC fields: `endpoint` (the request path), `companyTaxNumber` (raw value supplied; never normalised away), `environment` (raw enum), `documentNumber` (echoed from the payload when parseable; absent otherwise), `erpReferenceId` (when supplied), `outcome` (HTTP status code), `latencyMs` (wall-clock from controller entry to response committed), and `payloadArchiveId` (the id assigned by FR-OBS-003).
- **FR-OBS-002**: This feature MUST NOT add custom Micrometer counters, gauges, or timers. The default Spring Boot Actuator HTTP metrics are sufficient; partner-scoped metrics are a Sprint 2 concern (they require the partner identity that Sprint 1 does not have).
- **FR-OBS-003**: The system MUST persist the raw inbound request body of every ingest call into an `inbound_payload_archive` store before the main ingest transaction begins. This applies REGARDLESS of outcome — successful ingests, validation failures, duplicate rejections, unknown-company / unknown-environment failures, and 5xx errors all MUST leave an archive row behind. The archive write MUST run outside the ingest transaction (a separate `REQUIRES_NEW` transaction or a pre-controller filter) so an ingest-transaction rollback does NOT roll back the archive.
- **FR-OBS-004**: Each archive row MUST carry: `id` (UUID, returned to the log line via MDC `payloadArchiveId`), `endpoint` (request path), `received_at` (timestamp), the raw JSON body bytes, the resolved `company_id` and `authority_environment_id` (NULL when resolution did not complete), and `outcome` (the final HTTP status code, written on completion). The archive store MUST be RBAC-restricted; only operators with an explicit "integration forensics" grant may read it. Retention policy and automated purging are explicitly deferred to a follow-up feature.
- **FR-OBS-005**: If the archive write itself fails (storage unavailable, disk full, etc.), the ingest MUST fail with **HTTP 503** and response body `{code: "ARCHIVE_WRITE_FAILED", ...}` (matching `contracts/error-codes.md`), and MUST NOT proceed to the main ingest transaction. The platform refuses to accept any payload it cannot first archive, by design — this guarantees the archive is a faithful record of every inbound request. 503 (not 500) signals "transient; retry" to the partner.
- **FR-022**: The system MUST expose the four endpoints in an OpenAPI document accessible at `/v3/api-docs` and a Swagger UI at `/swagger-ui/index.html`. The four endpoints MUST be grouped under an `integration-gateway` group.
- **FR-023**: After successful ingest, the persisted documents MUST appear in the existing internal read paths (search, list, detail, artifact download where applicable) on equal footing with documents created by the internal UI. No separate `/api/integration` read endpoint is added.

**Schema dependency**

- **FR-024**: This feature depends on the V58 migration (and the V58–V61 sequence delivered by `specs/010-authority-spec-alignment/`) being applied first. Implementation MUST NOT begin until the V58–V61 entity, repository, and serialiser updates have merged.
- **FR-024a**: This feature adds exactly ONE new Flyway migration — the migration that creates `inbound_payload_archive` and its supporting RBAC grant (per FR-OBS-003/004). It is the next sequential migration after whatever V61 leaves behind (expected V62, confirmed at implementation time). No other migrations are introduced by this feature; any schema gap discovered while consuming the V58–V61 model lands as its own downstream migration, not in this feature.

### Key Entities

- **`IntegrationEnvironment`** (enum) — `SANDBOX` | `PREPROD`. Routing field on every request DTO. The intentional absence of `PRODUCTION` enforces a hard gate.
- **`IntegrationDocumentStatus`** (enum) — `DRAFT | VALID | INVALID | CLEARED | REPORTED | REJECTED | FAILED | CANCELLED`. The ERP-reported lifecycle, mapped to internal `DocumentState` per FR-016.
- **`DocumentIngestionResponse`** (record) — `{id: UUID, documentNumber: String, erpReferenceId: String, internalStatus: String, message: String}`. Returned by all four endpoints on success.
- **`EtaReceiptIngestionRequest`** — request DTO shaped to ETA Receipt SDK v1.2 plus the four gateway-routing fields. Nested records: `Header`, `DocumentType`, `Seller`, `BranchAddress`, `Buyer`, `Discount`, `ItemData`, `TaxableItem`, `TaxTotal`, `Contractor`, `Beneficiary`.
- **`EtaInvoiceIngestionRequest`** — request DTO shaped to ETA Invoice SDK v1.0 plus routing fields. Nested records: `TaxpayerParty`, `PartyAddress`, `InvoiceLine`, `UnitValue`, `LineTax`.
- **`ZatcaStandardInvoiceIngestionRequest`** — request DTO shaped to ZATCA Fatoora SDK v2.0.3 (Standard / B2B) plus routing fields. Nested records: `SellerParty`, `BuyerParty`, `LineItem`. Buyer required.
- **`ZatcaSimplifiedInvoiceIngestionRequest`** — same shape as Standard except buyer optional and status field carries `reportingStatus`. Anonymous retail allowed.
- **`CompanyNotFoundException`** — new exception with code `COMPANY_NOT_FOUND`, carries the supplied registration number, mapped to HTTP 404.
- **`AuthorityEnvironmentNotFoundException`** — new exception with code `AUTHORITY_ENVIRONMENT_NOT_FOUND`, carries the supplied authority + environment, mapped to HTTP 404.
- **`CompanyResolutionService`** — internal service that resolves `(companyRegistrationNumber, authority, environment)` into `(company_id, authority_environment_id)` for downstream persistence. Read-only transaction.
- **`InboundPayloadArchive`** — new persistence-side entity backing the archive store described in FR-OBS-003/004. Stores the raw inbound JSON body plus routing metadata (`endpoint`, `received_at`, `company_id` nullable, `authority_environment_id` nullable, `outcome`). RBAC-restricted: only operators with the "integration forensics" grant may read. Default backing is a new Flyway-managed RDBMS table (a migration introduced by this feature, the only schema change 011 adds beyond what 010 delivers); blob storage is an acceptable implementation alternative provided the contract holds.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: An ERP integrator can complete a successful end-to-end ingest of an ETA receipt and an ETA invoice from a local dev backend using only the OpenAPI doc as guidance, with no source-code reading, in under 30 minutes from cold start.
- **SC-002**: For each of the four endpoints, the happy-path integration test posts an SDK-shaped payload against a seeded company/environment, observes HTTP 201, and confirms (via repository query) that the header row is present with `state` matching the FR-016 mapping. All four tests pass on `mvn verify` against a Testcontainers Postgres.
- **SC-003**: For each of the four endpoints, the deduplication test posts the same `documentNumber` twice in succession and observes HTTP 201 then HTTP 409 with the correct `DUPLICATE_*_NUMBER` code; only one row is persisted.
- **SC-004**: Unknown `companyRegistrationNumber`, inactive company, and unknown `(authority, environment)` each produce HTTP 404 with the correct error code and the offending value echoed in the error body — verified by three error-path integration tests.
- **SC-005**: `environment=PRODUCTION` and structurally invalid payloads (missing required fields, regex-pattern violations, range violations) produce HTTP 400 with code `VALIDATION_ERROR` and a per-field error list — verified by representative error-path tests covering each `@Pattern` rule the implementation plan calls out (`receiptType`, `header.uuid`, `transactionTypeCode` (both shapes), `vatNumber`, `countryCode`).
- **SC-006**: A credit note ingested with an unknown `originalInvoiceNumber` is persisted with the raw string preserved and the FK left NULL; a credit note ingested with a known reference is persisted with the FK populated. Both verified by integration tests.
- **SC-007**: Documents ingested through the gateway appear in the existing internal read endpoints (list, detail, search) without any code change to those endpoints — verified by a follow-up integration test that ingests one document per endpoint and queries each via the existing read API.
- **SC-008**: The OpenAPI doc at `/v3/api-docs` lists the four endpoints under an `integration-gateway` group; `IntegrationEnvironment` schema enumerates exactly `SANDBOX` and `PREPROD`; `IntegrationDocumentStatus` enumerates exactly the eight values in FR-016.
- **SC-009**: For every ingest call (success or failure across HTTP 201/400/404/409/5xx), exactly one `inbound_payload_archive` row exists carrying the raw JSON body and matching the `payloadArchiveId` MDC field in the corresponding structured log line — verified by integration tests that POST one payload per outcome class and grep the test-time log capture for the archive id.
- **SC-010**: Every successful ingest produces exactly one `audit_logs` row with `action=INGESTED`, `actor=INTEGRATION_GATEWAY`, matching tenancy columns, and a payload referencing the new header id — verified by an integration test per endpoint.
- **SC-011**: When the inbound payload archive write fails, the ingest returns HTTP 503 with code `ARCHIVE_WRITE_FAILED` and zero rows are written to any `eta_*` or `zatca_*` operational table — verified by a fault-injection integration test that forces `InboundPayloadArchiveService.archive(...)` to throw.

## Assumptions

- The four-migration sequence V58–V61 from `specs/010-authority-spec-alignment/` has merged and the paired Wave 7/8 entity / repository / serialiser updates are in place. This feature does NOT include any Flyway migrations of its own beyond the `erp_reference_id` column (V58, already delivered by 010) being available on every header table. Schema gaps discovered during this work land as separate downstream migrations (V62+), not by reopening 010.
- Existing `Duplicate*Exception`, `MissingOriginalDocumentException`, `VatExemptionReasonRequiredException`, and `GlobalExceptionHandler` mappings remain authoritative and are reused. No new error-handling infrastructure is introduced.
- The platform's existing tenancy filters key on `(company_id, authority_environment_id)`. Ingested rows participating in those filters appear in tenant-scoped reads automatically.
- ERP partners generate their own ETA Receipt `header.uuid` SHA-256 hex string per the SDK; the platform performs format validation only and is not responsible for the canonicalisation algorithm.
- ERP-side authentication for these endpoints is not required for this feature. Network-level controls (private VPC, allowlist) are assumed to gate access until Sprint 2 introduces API-key validation.
- The OpenAPI dependency (`springdoc-openapi-starter-webmvc-ui` 2.8.8) is acceptable to add to `platform-api/pom.xml`; no conflicts with the current Spring Boot 3.4.4 baseline are expected.
- Documents ingested through the gateway are NEVER re-submitted to ETA / ZATCA by the platform. The ERP owns the authority-side lifecycle; the platform owns the archived representation, the search index, and the read-side audit. Re-submission flows, if needed later, are a separate feature.
- No per-endpoint latency or throughput target is declared. Performance regressions, if any, are surfaced reactively through default Actuator HTTP metrics; concrete SLOs are deferred to a downstream load-testing / SLO feature.
