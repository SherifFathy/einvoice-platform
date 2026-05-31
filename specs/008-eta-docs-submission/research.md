# Phase 0 — Research: Wave 7 ETA Document Tables and Submission Engine

**Branch**: `008-eta-docs-submission` | **Date**: 2026-05-11

## Purpose

This document resolves the implementation-detail decisions that `spec.md` deliberately deferred to the planning phase and consolidates the load-bearing technical choices for Wave 7. The five clarifications already integrated into `spec.md` (Q1 rejection-terminal + clone-to-new-draft, Q2 manual check-status with bulk refresh, Q3 no platform-side cancellation window, Q4 optimistic concurrency, Q5 English-only UI chrome) are not reopened here.

The Outstanding / Deferred items from `/speckit.clarify` — audit-log retention duration, finer-grained submission performance budget — are decided here. Several plan-shaping choices (state-machine matrix, transaction-type discriminator semantics, JSON serializer construction, signing approach, bulk-refresh execution model) are also documented.

---

## Decision 1 — Lifecycle state model

**Decision**: Both `EtaInvoiceHeader` and `EtaReceiptHeader` carry a `state` column of seven values: `DRAFT`, `SUBMITTING`, `IN_REVIEW`, `VALID`, `REJECTED`, `SUBMISSION_AMBIGUOUS`, `CANCELLED`. The allowed transition matrix is encoded in a pure-function helper `LifecycleTransitions.allowed(state, action) → boolean`, used by both backend enforcement and Angular button-gating (the Angular side calls the same matrix via a compile-time-generated TypeScript constant). The matrix:

| From state           | Allowed actions                                                                                  | Target state                       |
|----------------------|---------------------------------------------------------------------------------------------------|------------------------------------|
| `DRAFT`              | `EDIT`, `DELETE`, `SUBMIT`                                                                       | DRAFT, ∅, SUBMITTING               |
| `SUBMITTING`         | (engine-only) `MARK_VALID`, `MARK_REJECTED`, `MARK_IN_REVIEW`, `MARK_AMBIGUOUS`                  | VALID, REJECTED, IN_REVIEW, SUBMISSION_AMBIGUOUS |
| `IN_REVIEW`          | `CHECK_STATUS`                                                                                    | VALID, REJECTED (per ETA)          |
| `VALID`              | `CANCEL`                                                                                          | CANCELLED (if ETA accepts)         |
| `REJECTED`           | `CLONE_TO_NEW_DRAFT` (creates a new `DRAFT` row; rejected row unchanged)                          | (new row in DRAFT)                 |
| `SUBMISSION_AMBIGUOUS`| `RETRY` (calls ETA again, lands in VALID / REJECTED / IN_REVIEW / SUBMISSION_AMBIGUOUS)         | per ETA outcome                    |
| `CANCELLED`          | — (terminal)                                                                                      | ∅                                  |

**Rationale**: This is the smallest set of states that:
- expresses every distinct user-visible status the spec calls for (FR-007, FR-010, FR-011, FR-012, FR-013),
- makes Q1's "rejection is terminal" enforceable as `REJECTED` → ∅ in a single matrix lookup rather than scattered `if` statements,
- and gives the engine (`SUBMITTING` → one of four) a single chokepoint for outcome attribution.

A single source of truth (`LifecycleTransitions`) eliminates the well-known class of bugs where the UI hides a button but the backend lets the action through (or vice versa) — Constitution X.2 explicitly forbids that gap. Generating the TypeScript constant from the Java enum at build time (via a small Maven `exec-maven-plugin` invocation) keeps the two sides bit-for-bit identical.

**Alternatives considered**:
- A single `status` string with free-form values (the Wave 7 SQL in the implementation plan uses `VARCHAR(40)`): rejected for the type-safety reason above; the DB column stays `VARCHAR(40)` for forward-compat with future authority-specific sub-statuses, but the application layer reads/writes via the enum exclusively.
- Separate state enums per document type (invoice vs. receipt): rejected — invoices and receipts share the same lifecycle exactly; duplicating two enums would be churn without benefit.
- Storing the transition matrix in the DB: rejected — the transitions are domain rules, not configuration; they must travel with the deployed code.

---

## Decision 2 — Optimistic concurrency mechanics

**Decision**: `EtaInvoiceHeader` and `EtaReceiptHeader` carry a `version INTEGER NOT NULL DEFAULT 0` column managed by JPA `@Version`. The REST API exposes this version as a strong `ETag` on `GET` responses and requires it as an `If-Match` header on `PUT`. The backend returns `409 Conflict` with the structured body `{ code: "OPTIMISTIC_LOCK_CONFLICT", expectedVersion, actualVersion, current: { ...full current document body... } }` when versions disagree. The Angular form catches this 409, opens the `conflict-resolution.dialog` with both versions, and offers two actions:

- **Discard mine** → close dialog, reload form with the server's `current` body, no further API call.
- **Overwrite with mine** → re-submit `PUT` with `If-Match: <actualVersion>` and the user's edits; if a third party intervenes, the loop repeats.

**Rationale**: HTTP `ETag` / `If-Match` is the idiomatic optimistic-concurrency channel; reusing it (instead of inventing a custom header) means standard HTTP tooling and proxies behave correctly. Including the full current body in the 409 response lets the dialog show a real diff without forcing a separate fetch. The version is JPA-managed so no service-layer code has to touch it.

The line and line-tax child tables do **not** carry their own `@Version` — they are part of the header aggregate. A `PUT` that targets the header replaces the children atomically inside the same transaction.

**Alternatives considered**:
- Last-write-wins: rejected by Q4 clarification.
- Pessimistic locks (advisory or row-level): rejected by Q4 clarification (and would break in distributed-Angular-sessions scenarios where the user closes a browser tab without releasing the lock).
- Field-level concurrency with merge: rejected — the user-visible artifact is the whole document; line-level merge is more complex than the underlying ETA flow warrants.

---

## Decision 3 — Append-only enforcement on `invoice_artifacts` and `audit_logs`

**Decision**: Three-layer enforcement, defence-in-depth:

1. **Repository layer**: `InvoiceArtifactRepository` and `AuditLogRepository` extend a marker interface `WriteOnlyRepository<T>` that exposes only `save(...)`, `findAll(Specification, Pageable)`, and `findById(...)`. JPA `@Modifying` queries are not allowed on these repos (enforced by a unit test that reflects over the repository hierarchy).
2. **Service layer**: There is no `update` or `delete` method on `AuditService` or on the `InvoiceArtifactService` (the latter only has `record(...)` and `findByDocument(...)`).
3. **Database layer**: V52 attaches a `BEFORE UPDATE OR DELETE` trigger to both tables that raises `EXCEPTION 'append_only_table'`. The trigger is the final safety net — if anyone bypasses JPA (e.g. via raw JDBC or a future migration), the database itself rejects the write.

**Rationale**: Constitution IX.3 and XXI.4 forbid any UPDATE or DELETE on these tables "at any layer." A single layer of enforcement is fragile (a future developer adds a "fix typo" repository method and the safety vanishes); three layers means the constitution is upheld no matter which entry point is used. The trigger is the only layer the application code cannot accidentally remove during refactor.

**Alternatives considered**:
- Database-only enforcement: rejected because failed inserts surface as opaque PSQLException stack traces; the repository/service layers should fail fast with a clean exception type instead.
- Application-only enforcement: rejected because a future raw-JDBC migration or data-fix script could silently bypass it.

---

## Decision 4 — Submission flow: state ordering and idempotency

**Decision**: Every submission (invoke from `POST /submit` or `POST /retry`) follows this exact order, inside a single Spring `@Transactional` boundary on the **start** and **finish** halves separated by the outbound HTTP call:

1. Load the document, validate state (`DRAFT` → SUBMIT allowed; `SUBMISSION_AMBIGUOUS` → RETRY allowed; reject otherwise).
2. Validate totals consistency (`TotalsInconsistentException` on failure → FR-024).
3. Validate certificate availability for the company+environment (`NoCertificateConfiguredException` on failure → FR-008).
4. Set `state = SUBMITTING`, bump `version`, and **insert** a `SubmissionAttempt` row with `result = NULL` (in-flight marker) and the next `attempt_number`. **Commit the transaction here.** The document is now persisted as in-flight before any outbound call (Constitution XX.1).
5. Outside the transaction: serialize → sign → call ETA via `EtaHttpClient`.
6. On any response (success / rejected / timeout / network error): open a new `@Transactional` block, update the `SubmissionAttempt` row with the final outcome, write the signed payload + ETA response into `invoice_artifacts` (two rows per attempt — `SIGNED_JSON` and `ETA_RESPONSE`), update the header state per the engine's outcome, write one `audit_logs` row capturing the action and before/after state, and commit.
7. On thread interruption / hard JVM crash between steps 4 and 6: the next time the user (or operator) interacts with the document, the visible state is `SUBMITTING` with an attempt row whose `result IS NULL`; the **Check status** action (single or bulk) reconciles by calling ETA's status endpoint with the stored submission ID. If ETA has no record, the attempt is finalised as `result = AMBIGUOUS` and the document moves to `SUBMISSION_AMBIGUOUS`.

**Rationale**: This sequence makes the persisted state the source of truth for "what happened" at every step. The split transaction is the only way to satisfy Constitution XX.1 ("Document state MUST be persisted before external submission begins") without holding a long-running DB connection while waiting on ETA. The `result IS NULL` marker plus an in-application-layer FK from `submission_attempts.document_id` keeps the per-attempt uniqueness constraint `(document_id, attempt_number)` honest even when retries land out of order.

**Alternatives considered**:
- Single transaction wrapping the network call: rejected — holds row locks across a multi-second external call; under load, contention is severe.
- Outbox pattern with a separate dispatcher: rejected — adds a second service (the dispatcher) that this wave's scope doesn't justify; Wave 9 may revisit it for the reconciliation tooling.
- No in-flight marker: rejected — Constitution XX.1 would not be met; ambiguous outcomes would be invisible until the next check-status.

---

## Decision 5 — Bulk **Check status** execution model

**Decision**: The bulk endpoint is `POST /api/companies/{id}/eta/{invoices|receipts}/check-status` with body `{ documentIds: UUID[] }`. The controller validates the user's REFRESH permission on each document's company+environment context (denied entries are filtered out with `403`-equivalent per-document outcome entries, not a whole-batch 403). The orchestrator dispatches up to **8 concurrent** ETA status calls (bounded by a per-tenant `Executors.newFixedThreadPool(8)` named `eta-bulk-status-pool`); each call updates the corresponding header + writes one `submission_attempts`-style "status check" sub-row (modeled as a new `SubmissionAttempt` with `attempt_number = max + 1` only when the status actually changes; otherwise a single `audit_logs` row records the no-op check). The response is a JSON array, one entry per input document, with `{ documentId, beforeState, afterState, etaResultCode, errorSummary? }`.

The single-document **Check status** endpoint reuses the same controller method with a one-element array, for contract symmetry.

**Rationale**: 8 concurrent calls is the right balance between throughput and ETA-side rate-limit risk; it's bounded so a 200-document bulk doesn't open 200 HTTP connections. Returning per-document outcomes (rather than a whole-batch summary) lets the Angular `bulk-status-check.dialog` show a live progress list. Writing a new attempt row only on state change keeps `submission_attempts` from bloating with no-op checks (auditing a no-op via `audit_logs` is enough).

**Alternatives considered**:
- Sequential calls: rejected — 200 sequential ETA calls at 1–2s each is unacceptable UX.
- Background job (`@Scheduled` or `@Async` fan-out with WebSocket progress updates): rejected — Q2 explicitly defers background polling to Wave 9; a synchronous fan-out keeps Wave 7's surface small and doesn't preclude Wave 9 from adding a scheduler that calls the same orchestrator.
- One thread per document up to 1000: rejected — uncontrolled fan-out is exactly the failure mode ETA rate limits exist to punish.

---

## Decision 6 — ETA HTTP client and token caching

**Decision**: `EtaHttpClient` is a thin wrapper over Spring's `RestClient` (Spring 6.1+, already on classpath via Spring Boot 3.4.4). Base URLs come from a constant map keyed by `authority_environments.id`: PRODUCTION → `https://api.invoicing.eta.gov.eg`, PREPROD → `https://api.preprod.invoicing.eta.gov.eg` (placeholder hostnames; verified against ETA SDK docs during implementation). Tokens are cached per `(companyId, authorityEnvironmentId)` in `EtaTokenManager` using a Caffeine cache (already on classpath) with TTL = (token-expiry − 60 seconds). On 401 from ETA the cache entry is invalidated and one refresh attempt is made before propagating the error.

**Rationale**: Constitution V.3 explicitly permits "bounded secure caches" for authority clients. A Caffeine cache is bounded by size + TTL; tokens are never serialized to disk; the cache key is the same isolation key as everywhere else in the platform. Spring's `RestClient` is the idiomatic synchronous HTTP client in current Spring Boot.

**Alternatives considered**:
- Per-request token fetch (no cache): rejected — adds 1 round-trip to every submission; ETA's token endpoint is itself rate-limited.
- Database-backed token cache: rejected — adds DB load for what is effectively in-memory state; Constitution V.3 explicitly allows this kind of cache.
- Shared cache across `authority_environment_id`s for the same company: rejected — production and pre-prod tokens MUST never co-mingle (Constitution IV.3).

---

## Decision 7 — ETA JSON serializer construction

**Decision**: Two Jackson-based serializers, `EtaInvoiceSerializer` and `EtaReceiptSerializer`, each backed by a DTO tree that mirrors the ETA SDK payload structure exactly (named after the ETA fields, e.g. `documentTypeVersion`, `dateTimeIssued`, `taxpayerActivityCode`, `invoiceLines[].unitValue.currencySold`). The header/lines/taxes entity tree is mapped onto the DTO tree by a dedicated mapper (handwritten — no MapStruct in this wave to keep the Maven build untouched). The DTO tree is serialized with a custom `ObjectMapper` configured for: ISO-8601 timestamps with offset, BigDecimal as plain string with 5 decimal places, no nulls in output, deterministic key ordering (alphabetical within each object) so the signed payload is reproducible bit-for-bit.

**Rationale**: ETA validates payloads strictly; a `null`-suppressing, deterministic-key-order `ObjectMapper` is the only way to make golden-file tests (Constitution XXIV.1) succeed reliably across JVM versions. Deterministic key order also means the CAdES signature attaches to a stable byte sequence — re-serializing the same entity yields the same bytes, which matters for Wave 9 PDF rendering and for any future audit re-verification.

**Alternatives considered**:
- MapStruct: rejected — would add an annotation-processor dependency for a handful of mappers; not worth the build-time cost in this wave.
- Direct entity-to-JSON via Jackson annotations on the entities: rejected — couples the DB schema to the wire format; ETA payload field renames or version bumps would force schema migrations.
- Custom string-builder serializer: rejected — error-prone, no benefit over Jackson with a tight `ObjectMapper`.

---

## Decision 8 — CAdES-BES signing

**Decision**: `EtaSigningService` uses BouncyCastle 1.80 (already on the `platform-eta` classpath) to produce a CAdES-BES detached signature over the canonical JSON bytes from the serializer. The X.509 certificate and private key are read from `eta_configs` (Wave 6, plain-text columns) via `EtaConfigRepository.findActiveConfig(companyId, authorityEnvironmentId)`. Both the signature and the canonical JSON are persisted to `invoice_artifacts` (`SIGNED_JSON` row holds the JSON; `ETA_RESPONSE` row will later hold the ETA acknowledgement). The signing service is stateless — every call resolves the certificate fresh, so a Wave-6 config update is picked up by the next submission.

**Rationale**: CAdES-BES is the ETA-required signature scheme (Constitution I.3). BouncyCastle is the only Java library that produces it without commercial licensing. Reading the cert fresh per call (instead of caching the parsed X.509) avoids stale-after-rotation bugs at negligible CPU cost.

**Alternatives considered**:
- Caching parsed certificates in memory: rejected — Wave 6 config updates would not be picked up until JVM restart; trivial CPU savings, real correctness risk.
- xades4j: rejected — xades4j is XAdES (XML), not CAdES (binary over arbitrary content); wrong scheme for ETA.

---

## Decision 9 — Audit-log retention duration

**Decision**: Audit logs are retained indefinitely in Wave 7. No retention/deletion mechanism is built in this wave. A backlog item tracks "implement audit log archival policy (likely 10 years to satisfy Egyptian tax record-keeping)" for Wave 10+ alongside any operational data-lifecycle work.

**Rationale**: Egyptian tax record-keeping commonly requires 5–10 years; deleting audit logs in-band would risk regulatory exposure. Since Wave 7 isn't establishing any operational data-retention policy and `audit_logs` is partitionable later, "keep everything" is both the safest default and the cheapest implementation.

**Alternatives considered**:
- Implementing rolling 5-year retention in Wave 7: rejected — premature; no clear regulatory mandate forces the timing; archival needs Wave-10 cold-storage infrastructure decisions that don't exist yet.
- Per-tenant retention setting: rejected — adds a tenant-config surface for no current use case.

---

## Decision 10 — Performance budgets

**Decision** (concretising `spec.md` SC-001 / SC-007 / Constitution XXV):

| Path                                                        | Budget (p95)     | Notes                                                                                       |
|--------------------------------------------------------------|------------------|---------------------------------------------------------------------------------------------|
| `POST /invoices` (create draft, 5 lines)                     | < 400 ms         | All within one transaction, no outbound calls.                                              |
| `PUT /invoices/{id}` (edit draft, optimistic concurrency)    | < 400 ms         | Same.                                                                                       |
| `POST /invoices/{id}/submit`                                  | < 5 s end-to-end | 400 ms platform + serializer + signer + ETA round-trip + finalise transaction.              |
| `GET /invoices` (list, 1000-row page, with company filter)   | < 2 s            | Compound index on `(company_id, authority_environment_id, issue_datetime DESC)`.            |
| Filter narrowing (company / status / date range)             | < 500 ms         | Same index.                                                                                 |
| `POST /invoices/check-status` (200 documents)                | < 30 s           | 8-way fan-out; per-document outcome streamed back in response array.                        |
| `GET /invoices/{id}` (detail with submission history)        | < 600 ms         | Header + lines + taxes + recent attempts + artifact metadata in one round trip.             |
| `GET /invoices/{id}/artifacts/{type}` (download signed JSON) | < 800 ms         | Single artifact, served as `application/json` with `Content-Disposition: attachment`.       |

**Rationale**: These budgets are derived directly from spec success criteria and Constitution XXV; they are tight enough to be meaningful and loose enough to be defendable on commodity on-prem hardware (Constitution XIX).

---

## Decision 11 — Transaction-type discriminator semantics

**Decision**: `submission_attempts.transaction_type`, `invoice_artifacts.transaction_type`, and `audit_logs.entity_type` are all `VARCHAR` columns drawn from a single `TransactionType` enum: `INVOICE` (ETA), `RECEIPT` (ETA), `STANDARD` (ZATCA — Wave 8), `SIMPLIFIED` (ZATCA — Wave 8). The `document_id` column on `submission_attempts` and `invoice_artifacts` is a `UUID` that references the appropriate header table for the given `transaction_type`; **the FK is enforced at the application layer, not the database** (Constitution XI.6 explicitly says so). A composite index on `(document_id, transaction_type)` supports per-document lookups.

**Rationale**: Constitution XI.6 explicitly mandates this shape. A polymorphic FK with no DB-level enforcement is unusual but is the right trade-off when the table is genuinely shared across multiple authorities with no common parent table to reference.

**Alternatives considered**:
- A separate `submission_attempts` and `invoice_artifacts` table per transaction type: rejected — quadruples table count for no isolation benefit (the rows are already scoped by `company_id` + `authority_environment_id`); would also force Wave 8 to re-decide the same question with no consistency.
- A common parent `documents` table: rejected — pure overhead; no operation works on documents-as-a-superclass.

---

## Coverage check

| Taxonomy category                                    | Status        | Resolved by             |
|------------------------------------------------------|---------------|-------------------------|
| Functional scope & behaviour                          | ✅ Resolved   | spec.md Clarifications Q1/Q5 + this Decision 1 |
| Domain & data model — lifecycle                       | ✅ Resolved   | Decision 1, Decision 2  |
| Domain & data model — append-only tables              | ✅ Resolved   | Decision 3              |
| Submission flow / idempotency                         | ✅ Resolved   | Decision 4              |
| Bulk-refresh execution                                | ✅ Resolved   | Decision 5              |
| ETA HTTP integration                                  | ✅ Resolved   | Decision 6              |
| ETA serialization                                     | ✅ Resolved   | Decision 7              |
| Signing                                               | ✅ Resolved   | Decision 8              |
| Audit retention                                       | ✅ Resolved (deferred to Wave 10+) | Decision 9 |
| Performance budgets                                   | ✅ Resolved   | Decision 10             |
| Shared-table polymorphism                             | ✅ Resolved   | Decision 11             |

All planning-phase ambiguities are now resolved or explicitly deferred with a Wave annotation.
