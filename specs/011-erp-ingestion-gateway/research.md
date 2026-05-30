# Phase 0 — Research: ERP Ingestion Gateway

**Branch**: `011-erp-ingestion-gateway` | **Date**: 2026-05-27

The spec arrived fully clarified (Session 2026-05-27 resolved Q1–Q5 + the 18 locked decisions of 2026-05-25 in the source plan). No `NEEDS CLARIFICATION` markers carried into the Technical Context. This document records the *implementation-pattern* choices behind the locked decisions — pinned so that PR reviews and `/speckit-tasks` decomposition don't re-relitigate them.

## R1 — Archive write outside the ingest transaction

**Decision**: A Spring `OncePerRequestFilter` (`IngestionPayloadArchiveFilter`) wraps every `POST /api/integration/v1/**` request with a `ContentCachingRequestWrapper`, reads the cached body, calls `InboundPayloadArchiveService.archive(...)` annotated `@Transactional(propagation = REQUIRES_NEW)`, stamps the returned UUID into MDC as `payloadArchiveId`, and only then passes the request down the chain. The `outcome` column is patched on response committed (via the same filter, in a `finally` block).

**Rationale**:
- The filter runs *before* the controller, so the archive row exists even when DTO deserialisation fails (HTTP 400 from Jackson) or when validation fails (HTTP 400 from `@Valid`). Spec FR-OBS-003 requires "every inbound request regardless of outcome" — that has to include parser-rejected requests, which an `@ControllerAdvice` would arrive too late for.
- `REQUIRES_NEW` makes the archive write commit independent of the ingest service's transaction. When the ingest service rolls back (constraint violation, duplicate, line-save failure), the archive row stays. This is the locked Sprint 1 guarantee.
- The outcome patch is a single-column UPDATE on a row keyed by the same UUID the filter already holds — no second lookup, no contention.

**Alternatives considered**:
- *Aspect on each controller method* — fires after deserialisation, missing the FR-OBS-003 coverage requirement for HTTP 400 parser failures.
- *Async write via `ApplicationEventPublisher`* — violates FR-OBS-005 (archive failure must abort ingest with 5xx); async would make the archive eventually-consistent and break the "refuses to accept any payload it cannot first archive" guarantee.
- *Synchronous write inside the ingest service's transaction* — rolled back rejections lose their archive row, defeating the forensics goal.

## R2 — MDC field plumbing for FR-OBS-001

**Decision**: The same `IngestionPayloadArchiveFilter` populates MDC at request entry: `endpoint`, `companyTaxNumber` (raw, before resolution), `environment` (raw), `payloadArchiveId`, and `outcome` (patched on response committed). `documentNumber`, `erpReferenceId`, and `latencyMs` are set by a small `@ResponseBody` wrapper in the controllers after successful deserialisation (these fields are inside the parsed body). Logback's pattern layout reads the MDC; a single INFO log line per request fires in the filter's `finally` block.

**Rationale**:
- One filter owns request-scoped MDC lifecycle (`MDC.clear()` in `finally`), avoiding leaks across thread reuse.
- The two-step population (filter for envelope fields, controllers for body-derived fields) is the cleanest way to handle the case where the body is unparseable — the log still has `endpoint` / `companyTaxNumber` (if the field happens to deserialise) / `payloadArchiveId` / `outcome=400`.
- Logback is already the platform's logger; no new dependency.

**Alternatives considered**:
- *Micrometer Observation API* — adds Spring Boot 3.4 instrumentation surface but FR-OBS-002 explicitly forbids custom metrics in this feature. The Observation API would compose well in the Sprint 2 SLO feature; deferred.
- *AOP advice for MDC* — works but duplicates the filter's lifecycle. Single source of MDC writes is simpler.

## R3 — `IntegrationEnvironment` enum vs. registry cross-product

**Observation**: Constitution III enumerates the `authority_environments` registry as: ETA × {PRODUCTION, PREPROD}, ZATCA × {PRODUCTION, SIMULATION, SANDBOX}. The locked DTO enum is `SANDBOX | PREPROD`. The cross-product is:

| Endpoint authority | DTO `environment` | Registry row | Outcome |
|---|---|---|---|
| ETA | `PREPROD` | ETA + PREPROD (id=2) | ✓ resolves |
| ETA | `SANDBOX` | none | HTTP 404 `AUTHORITY_ENVIRONMENT_NOT_FOUND` |
| ZATCA | `SANDBOX` | ZATCA + SANDBOX (id=5) | ✓ resolves |
| ZATCA | `PREPROD` | none | HTTP 404 `AUTHORITY_ENVIRONMENT_NOT_FOUND` |

**Decision**: Keep the locked enum as-is. The 404 path for cross-product gaps is *correct and tested* (FR-007, SC-004); the existing active-environment filter is the gate. `SIMULATION` is intentionally absent from the DTO — ZATCA Simulation is a vendor-internal testing harness not exposed to integration partners.

**Rationale**:
- Re-litigating the enum to be authority-specific (one enum per controller) doubles the DTO surface for a constraint that the resolver already enforces.
- The OpenAPI doc will list both values per FR-022; integrators read documentation, not constitution registries. The 404 is the contract; the error code echoes the supplied `authority` and `environment` per the locked exception shape.

**Alternatives considered**:
- *Per-controller enums (`EtaEnvironment` = `PREPROD`, `ZatcaEnvironment` = `SANDBOX`)* — surfaces the constraint structurally, but each controller's DTO bloats with a near-identical enum and Sprint 2's API-key feature would have to merge them back.
- *Include `SIMULATION` in the enum* — exposes a non-integration environment to the partner contract; rejected by source plan §10.

## R4 — `V62` migration scope

**Decision**: The V62 migration creates exactly one table:

```sql
CREATE TABLE inbound_payload_archive (
    id              UUID PRIMARY KEY,
    endpoint        VARCHAR(120) NOT NULL,
    received_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    body            JSONB        NOT NULL,         -- raw inbound JSON
    company_id      UUID,                          -- null when resolution failed before save
    authority_environment_id SMALLINT,             -- null when resolution failed
    outcome         SMALLINT                       -- HTTP status, patched on response committed
);
CREATE INDEX inbound_payload_archive_received_at_brin ON inbound_payload_archive USING BRIN (received_at);
```

Plus a `GRANT SELECT ON inbound_payload_archive TO integration_forensics;` (the role is created in V62 if missing). Smart-app / dumb-DB pattern from 010 applies: no `CHECK`, no `FK`, no `UNIQUE` beyond the PK. RBAC is enforced at the application layer via a new `INTEGRATION_FORENSICS_READ` permission gated on the read API (out of scope for this feature; the table exists, the grant ensures the role *can* read once UI lands).

**Rationale**:
- BRIN on `received_at` is ~80× cheaper than B-tree for append-heavy time-series tables of this shape; the query pattern is range scans by recency. Chosen pattern from PostgreSQL ops best practices for archive tables.
- `JSONB` (not `BYTEA` / `TEXT`) preserves the platform's convention for partner-payload storage (matches `zatca_response_data`, `eta_response_data`).
- `company_id` and `authority_environment_id` deliberately nullable because resolution may fail *after* the archive row exists (the spec requires archive-first).

**Alternatives considered**:
- *Blob storage (S3-compatible) instead of a DB table* — the spec allows this as an "acceptable implementation alternative" but introduces an external dependency and a transactional-boundary mismatch (S3 has no transactions). The DB table is simpler, matches the existing operational backup posture, and satisfies the contract verbatim.
- *Per-endpoint archive tables* — four tables for an infrastructure concern fragments forensic queries; one wide table with an `endpoint` column is cleaner.
- *Add CHECK constraints on `outcome ∈ {201, 400, 404, 409, 500, 503}`* — violates the locked smart-app / dumb-DB pattern from 010 (FR-024 / migrations only add columns + tables + indexes).

## R5 — Original-document FK resolution

**Decision**: The hybrid resolution from FR-017 — always persist the raw `original_invoice_number` string, populate the existing `original_*_id` FK only when a row exists in the same `(company_id, authority_environment_id)` for the same document type — is implemented in the ingestion services using:

- ETA Invoice: existing `EtaInvoiceHeaderRepository.findByCompanyIdAndAuthorityEnvironmentIdAndInvoiceNumber()` (already returns `Optional`).
- ZATCA Standard: new `ZatcaStandardHeaderRepository.findByCompanyIdAndAuthorityEnvironmentIdAndInvoiceNumber()` (added in this feature).
- ZATCA Simplified: new `ZatcaSimplifiedHeaderRepository.findByCompanyIdAndAuthorityEnvironmentIdAndInvoiceNumber()` (added in this feature).

When the lookup returns empty, the service writes only the raw string; when present, it writes both. The FR-017 invariant "ingest MUST NOT fail because the referenced original is unknown" is enforced by *not* propagating the empty lookup as an error.

**Rationale**: The source plan's §10 locked this 2026-05-25; the only new mechanical work is the two ZATCA repository methods. ETA Receipt does not currently support credit/debit notes via this gateway (the ETA Receipt SDK has no credit-note flow), so no equivalent resolution is needed on the receipt path.

## R6 — DTO record vs. class shape

**Decision**: All request DTOs and nested types are Java 17 `record`s with Bean Validation 3.0 annotations on the components, e.g.:

```java
public record EtaReceiptIngestionRequest(
    @NotBlank @Size(max = 100) String companyRegistrationNumber,
    @NotNull IntegrationEnvironment environment,
    @NotNull IntegrationDocumentStatus status,
    @Size(max = 100) String erpReferenceId,
    @NotNull @Valid Header header,
    ...
) { ... }
```

**Rationale**:
- Records give immutability + canonical-constructor validation for free.
- Spring Boot 3.4.4 + Jackson 2.18 + Hibernate Validator 8 fully support `record` component annotations (validated on every release since Spring 6.0).
- The source plan §6–§10 already specifies the record shape; no need to deviate.

**Alternatives considered**: Lombok `@Value` classes — older platform convention but more boilerplate, no real advantage over records for an inert request DTO.

## R7 — SpringDoc grouping for the OpenAPI surface

**Decision**: One `GroupedOpenApi` bean in `OpenApiConfig`:

```java
@Bean
GroupedOpenApi integrationGateway() {
    return GroupedOpenApi.builder()
        .group("integration-gateway")
        .pathsToMatch("/api/integration/**")
        .build();
}
```

This produces a separate doc at `/v3/api-docs/integration-gateway` and a Swagger UI tab listing exactly the four endpoints. Default docs at `/v3/api-docs` still emit the entire surface (internal + integration); the group is purely a navigation aid for integrators.

**Rationale**: Matches SC-008's "listed under an `integration-gateway` group" verbatim. Default Swagger UI tab switcher exposes the group without further config.

**Alternatives considered**: Filtering with `@Tag` only — works but doesn't deliver a separately downloadable group spec for partners.

## R8 — Constitution-check caveats sanity-check (updated 2026-05-27 post-analyze)

Cross-referenced the constitution against Sprint 1 locked decisions. After the 2026-05-27 `/speckit-analyze` remediation (FR-009c + FR-009d + the seeded `INTEGRATION_GATEWAY` user row), only TWO caveats remain:

- **VII (Admin/Operational Mode Separation)** — RESOLVED. The gateway resolves `(company_id, authority_environment_id)` from the request body before any operational repository call. At every persistence call site, `TenantContext.company_id` is non-null. VII.4's literal reading is satisfied.
- **VIII (Transaction Module Architecture)** — CAVEAT. `permitAll()` means no permission check runs on the gateway path. The seeded `INTEGRATION_GATEWAY` user has no `user_company_transaction_roles` rows by design — Sprint 2's API-key feature introduces the partner-identity row + matching role grants. This caveat is documented in plan.md §Complexity Tracking and bounded by locked Sprint 1 scope.
- **XV (Spring Boot Engineering)** — RESOLVED. The seeded user row supplies `TenantContext.user_id` per FR-009d; all seven `TenantContext` fields are populated for every gateway request. XV.6 satisfied verbatim.
- **XVII (RBAC and Access Control)** — CAVEAT. Same root as VIII: `permitAll()` in Sprint 1. The archive store's `integration_forensics` RBAC grant (FR-OBS-004) *strengthens* XVII rather than weakening it, but the gateway-write side of XVII remains a Sprint-1-bounded gap.
- **XXV (Performance)** — HONEST DEFERRAL. No SLO declared (Q5 → A). Constitution XXV.1's 5-second target is scoped to submission, not gateway ingest.

Two caveats remain (VIII, XVII), both expressing the same root: Sprint 1's locked `permitAll()` decision. They converge in the Sprint 2 API-key feature. XXV is honest deferral, not a violation.

## R9 — Seeded `INTEGRATION_GATEWAY` user row (2026-05-27 post-analyze)

**Decision**: V62 seeds exactly one row in `users` representing the gateway as a JWT-less principal. Fixed UUID (e.g. `00000000-0000-0000-0000-000000000011`), `username='INTEGRATION_GATEWAY'`, `is_active=FALSE`, `is_super_user=FALSE`, password-column NULL or sentinel. The row's UUID is consumed by `TenantContext.user_id` (FR-009d) for every gateway request; it is NOT used as the value of any author / submitted-by column — those continue to receive the literal string `INTEGRATION_GATEWAY` per FR-009a.

**Rationale**:
- Resolves Constitution §XV.6 verbatim ("TenantContext MUST carry: ... user_id") without a structural schema change.
- Keeps the Q1-locked `INTEGRATION_GATEWAY` literal in author columns intact (no breaking change to FR-009a).
- The seeded row is INACTIVE — it cannot log in via the UI, cannot be selected in Admin Mode lists, and is invisible to RBAC permission enumeration. Audit grep is trivial: `created_by='INTEGRATION_GATEWAY'` on authored rows AND `users.username='INTEGRATION_GATEWAY'` on the seeded row both surface the same actor name.
- Sprint 2's API-key feature replaces the static UUID with per-partner identities at the same `TenantContext` plumbing site — no schema migration needed.

**Alternatives considered**:
- *Leave `TenantContext.user_id` null* — strict XV.6 violation, was the previous PASS-with-caveat posture. Analyze flagged as CRITICAL (C2).
- *Synthesise a fake JWT for the gateway thread* — propagates a non-existent user identity through every layer (Spring Security context, JWT-consuming filters, downstream services). Architecturally worse than an explicit seeded inactive principal.
- *Use the literal string `'INTEGRATION_GATEWAY'` as `TenantContext.user_id`* — but the field is typed `UUID` (per Constitution XV.6 and the existing `TenantContext` definition), so type-coerce-to-UUID is not a real option.
- *Use a per-partner user row* — that's exactly what Sprint 2's API-key feature will introduce. Sprint 1 has no partner identity; one row is the minimum.

## Open questions intentionally left open

| Question | Why deferred | When it lands |
|---|---|---|
| Retention policy + automated purge for `inbound_payload_archive` | FR-OBS-004 explicitly defers; depends on legal/compliance review for partner-payload retention | Follow-up "archive retention" feature |
| Per-partner abuse limits (rate + size + line count) | FR-021a + Q3 → A defer; depends on partner identity from API-key feature | Sprint 2 API-key feature |
| Concrete latency / throughput SLOs | Q5 → A defers; needs measured traffic | Sprint 2 SLO feature |
| Re-submission flow (gateway forwards to ETA/ZATCA) | Out of scope by spec Assumptions + Q-2026-05-27 (`gateway does not submit`) | Separate feature if needed; no firm timeline |

All remaining decisions are mechanical implementation steps captured in §Project Structure of `plan.md` and slated for decomposition by `/speckit-tasks`.
