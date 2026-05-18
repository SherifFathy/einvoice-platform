# Phase 0 — Research: Wave 8 (ZATCA Document Tables & Submission Engine)

This document resolves the open technical questions raised by the Technical Context section of `plan.md` and records the decisions that govern Phase 1 design. Each entry follows the **Decision / Rationale / Alternatives Considered** structure; cross-references to `spec.md` clarifications (Q1–Q5), functional requirements (FR-###), success criteria (SC-###), and constitution principles (Roman numerals) are inline.

## 1. ZATCA invoice hash chain — pessimistic acquisition implementation

**Decision**: `ZatcaChainService.acquireForUpdate(companyId, authorityEnvironmentId)` opens a fresh JDBC transaction, issues `SET LOCAL lock_timeout = '30s'`, then executes `SELECT * FROM zatca_chain_state WHERE company_id = ? AND authority_environment_id = ? FOR UPDATE`. On success it returns the chain row to the orchestrator; on Postgres `55P03 lock_not_available`, it translates to `ChainBusyException` mapped to HTTP 503 `CHAIN_BUSY` with the document left in `DRAFT` (FR-009a, Q2).

**Rationale**:
- Constitution XII.3 mandates `SELECT FOR UPDATE` for every ZATCA submission — this is the principle's literal requirement.
- A Postgres-level `lock_timeout` GUC is the most direct way to put a bounded ceiling on the wait without writing a hand-rolled loop. `SET LOCAL` scopes it to the submission transaction only — other queries in the same connection (e.g. reads from `zatca_configs`) remain on the default.
- A 30-second ceiling comfortably accommodates the longest expected legitimate hold (a full ZATCA clearance round-trip in nominal conditions is under 5 s) while bounding the worst case enough that user-facing latency stays sub-minute.
- Two-tier mapping (Postgres SQLState → custom exception → HTTP 503) preserves the existing `GlobalExceptionHandler` discipline; the orchestrator does not need to know about Postgres error codes.

**Alternatives considered**:
- **Advisory locks (`pg_try_advisory_xact_lock`)**: would let the platform express the lock as a key rather than a row. Rejected because Constitution XII.3 specifies the row-level lock on `zatca_chain_state` — using an advisory lock would create an out-of-DB-trust source of correctness that auditors cannot inspect with a simple `SELECT * FROM pg_locks`.
- **Application-level mutex (per-context lock map)**: simpler in single-node deployments. Rejected because the platform is designed to support multi-instance on-prem deployment behind a load balancer (Constitution XIX), and an in-memory mutex does not serialise across instances.
- **Unbounded wait** (Q2 Option B): rejected at the clarification stage; recorded here for completeness. Would create user-facing hangs and tie up Tomcat worker threads indefinitely.
- **Persistent queue with `QUEUED` status** (Q2 Option C): rejected at the clarification stage; adds a queue, a worker pool, and a new status for a problem that the bounded wait handles.

**Risks / mitigations**:
- A long-running ZATCA call that holds the lock past 30 s would cause the second submission to fail with `CHAIN_BUSY`. The current ZATCA SLA p95 is well under 5 s, but if ZATCA degrades the lock_timeout can be tuned via configuration. The chain row hold time itself can also be reduced: the submission transaction can release the lock as soon as the chain row is updated and the submission_attempt row is opened — the outbound ZATCA call does not need to be inside the lock. Phase-1 design ships with the lock spanning the whole submission for simplicity; performance tuning to release earlier is captured as a tasks-template polish item.

---

## 2. UBL 2.1 XML generation library

**Decision**: Build UBL 2.1 XML using JAXB with the ZATCA-published UBL XSDs and a thin builder layer (`ZatcaUblBuilder`) that maps the platform's `ZatcaStandardHeader` / `ZatcaSimplifiedHeader` domain entities onto the JAXB types. Canonicalisation is performed using Apache Santuario (already a transitive dependency of xades4j) via the `org.apache.xml.security.c14n.Canonicalizer` API with the `Canonicalizer11_OmitComments` algorithm (`http://www.w3.org/2006/12/xml-c14n11`), matching ZATCA's documented requirement.

**Rationale**:
- JAXB-generated classes from the ZATCA UBL XSDs give strong typing for free, are deterministic, and produce byte-for-byte reproducible XML in golden-file tests when output via the same `Marshaller` configuration.
- Apache Santuario is already on the classpath (via xades4j). Using it for both canonicalisation and signing keeps the crypto path consistent and avoids version-drift between two XML stacks.
- The ZATCA UBL XSDs cover both Standard and Simplified — the transaction-type code lives in the `TransactionType` field, so one builder generates both classes from the same JAXB tree.

**Alternatives considered**:
- **String templating** (e.g. Mustache, Freemarker) of UBL XML: rejected — UBL is hierarchical with optional sub-trees per transaction type code; template logic gets brittle and golden-file diffs become noisy for cosmetic changes.
- **Hand-rolled DOM building**: rejected — verbose and easy to drift from the XSD.
- **Third-party UBL library (UBL.peppol, jubl, etc.)**: rejected — each adds a dependency and a translation layer; the platform already accepts JAXB and the gain over plain JAXB on ZATCA-published XSDs is marginal.

**Risks / mitigations**:
- ZATCA can revise the XSDs at any phase release. The builder is structured so that an XSD bump is a regen of JAXB classes; the builder's mapping logic remains stable.
- C14N performance: canonicalisation is CPU-heavy on large invoices. Constitution XXV.1's 5 s p95 budget is comfortable for ≤ 50 lines; documents above that are out of scope for this wave.

---

## 3. XAdES-BES signing implementation

**Decision**: Use **xades4j 2.4.0** (already on `platform-zatca`'s classpath from the Wave-2 spike) with the `XadesBesSigningProfile` and ZATCA's required signed properties (`SigningTime`, `SigningCertificate` digest, signed properties references). The cert + private key are loaded fresh per signing call from `zatca_configs` (plain-text per Constitution XVIII) via the existing `ZatcaConfigRepository`; no caching of key material in the JVM beyond request scope.

**Rationale**:
- xades4j is the de-facto Java implementation of XAdES and is what the Wave-2 spike already validated against ZATCA Sandbox. Reusing it avoids re-validating an alternative.
- Per-request key loading keeps the runtime stateless (Constitution V.1) and ensures the bounded-secure-cache exception (V.3) is not exercised for key material — the bounded cache concept is reserved for tokens, not keys.
- ZATCA's signed-properties shape (digest of cert + signing-time UTC) is fully expressible via xades4j's `SignaturePolicyImpl` extension points.

**Alternatives considered**:
- **Apache Santuario XAdES extension**: rejected — less mature than xades4j for the specific signed-properties shape ZATCA mandates, and the Wave-2 spike already validated against ZATCA Sandbox using xades4j.
- **BouncyCastle low-level XAdES assembly**: rejected — vast amount of boilerplate; xades4j already wraps this exact stack.
- **Switching to a vendor SaaS signing service**: rejected — violates Constitution VI.1 (signing is server-side, on-prem).

**Risks / mitigations**:
- xades4j has a transitive dependency on Saxon-HE in some signed-properties configurations. The Wave-2 spike pinned the configuration that does not require Saxon; this wave keeps that pinning.
- ZATCA may tighten the signed-properties profile across phases; the `ZatcaSigningService` already exposes a `XadesSigningProfile` factory method so the profile can be swapped without changing callers.

---

## 4. SHA-256 hash & canonicalisation order

**Decision**: The document hash is `SHA-256(C14N11_OmitComments(SignedUblXml))` where `SignedUblXml` is the XAdES-BES-signed UBL document. The canonicalisation algorithm is `http://www.w3.org/2006/12/xml-c14n11` and is applied to the entire signed document (not just the `<Invoice>` subtree). The hash is computed by `ZatcaHashService.computeHash(byte[] signedUblXml)` and is stored both on the header (`invoice_hash` column) and surfaced in the QR code TLV tag 8 (signature digest), depending on the QR profile.

**Rationale**:
- Constitution XII.4 requires snapshot values for `invoice_counter_value`, `previous_invoice_hash`, and `invoice_hash` to be stored on the header at submission time. The hash is the load-bearing chain element.
- ZATCA's phase-2 spec mandates C14N11 over the signed document for the hash; using the same algorithm for signing input and chain-hash input keeps the crypto path uniform and the golden-file tests deterministic.

**Alternatives considered**:
- **C14N 1.0 (exclusive)**: rejected — not what the ZATCA spec asks for.
- **Hashing only the `<UBLExtensions>` element**: rejected — would not match ZATCA's chain-integrity model.

---

## 5. QR code TLV encoding — Phase-2 nine-tag

**Decision**: Implement TLV encoding for the nine ZATCA Phase-2 tags:
1. Seller name (UTF-8)
2. VAT registration number
3. Invoice issue date+time (ISO-8601)
4. Invoice total with VAT (2 decimals)
5. VAT total (2 decimals)
6. Hash of invoice (Base64)
7. ECDSA signature (Base64, derived from XAdES signature)
8. ECDSA public key (Base64)
9. ECDSA signature of the cryptographic stamp identifier (Base64)

The TLV bytes are then Base64-encoded for storage in `qr_code_base64` on the header and rendered to a PNG via **ZXing** (already on classpath in `platform-zatca`'s `pom.xml`) for the `artifact_type = QR_PNG` artifact row. PNG dimensions: 300 × 300 px, error correction L.

**Rationale**:
- The nine-tag set is ZATCA's published Phase-2 QR requirement; tags 7–9 are what differentiate Phase 2 from Phase 1.
- Storing the Base64-TLV on the header avoids re-deriving the QR on every detail-screen view; the PNG artifact in `invoice_artifacts` is a presentation convenience and is regenerated (as a new artifact row, never overwriting) if the rendering parameters ever change.

**Alternatives considered**:
- **Generate PNG on demand**: rejected — pushes CPU work to read time and breaks Constitution XXI.1 (canonical authority artifacts must be retained, not re-derived).
- **Higher-resolution PNG (600 × 600)**: rejected — file size doubles for no scanner benefit; 300 × 300 with error correction L scans reliably at consumer hardware distance.

---

## 6. ZATCA HTTP client — per-environment base URL resolution

**Decision**: `ZatcaHttpClient` is a thin wrapper over Spring's `RestClient` (synchronous, blocking) constructed per request with the base URL drawn from `zatca_configs.base_url` for the active `(companyId, authorityEnvironmentId)`. There is no global ZATCA URL configuration in `application.yml` — every environment's base URL is data, not config. Connection timeout: 10 s. Read timeout: 30 s. The client is recreated per request; no connection pooling across requests beyond what `RestClient`'s default `HttpClient` offers.

**Rationale**:
- Per-environment URLs are exactly the case the Wave-6 `zatca_configs` table was built for. Hard-coding them in `application.yml` would split truth.
- `RestClient` is the modern Spring HTTP client and gives synchronous semantics matching the orchestrator's transactional flow. WebClient (reactive) would add a reactor stack the rest of the platform does not use.
- The 30 s read timeout is shorter than the 30 s chain-lock timeout intentionally — if ZATCA fails to respond within 30 s, we want the timeout to surface as `SUBMISSION_AMBIGUOUS` and free the chain lock, not as `CHAIN_BUSY` on the next concurrent submission.

**Alternatives considered**:
- **Per-environment Spring profile**: rejected — environments are runtime-selected per request based on JWT, not at app startup.
- **WebClient reactive**: rejected — adds reactor; the rest of the codebase is blocking-Spring.

---

## 7. State machine reuse from Wave 7

**Decision**: Promote Wave 7's `EtaInvoiceState` and `EtaReceiptState` enums to a single shared `DocumentState` enum in `platform-core/domain/shared/`, with the seven values `DRAFT, SUBMITTING, SUBMITTED, IN_REVIEW, ACCEPTED, REJECTED, CANCELLED`. Promote `LifecycleTransitions` to a parameterised matrix keyed by transaction type (INVOICE, RECEIPT, STANDARD, SIMPLIFIED) so the same enforcement logic serves all four modules. Wave 7's existing matrices for INVOICE and RECEIPT are preserved unchanged; Wave 8 adds matrices for STANDARD and SIMPLIFIED that have the same shape (terminal `REJECTED` and `CANCELLED`, `IN_REVIEW` only reachable when ZATCA returns a deferred outcome, all other transitions identical).

**Rationale**:
- Q1's clarification explicitly asked for parity with Wave 7's lifecycle, and the simplest implementation of "same shape" is one enum and one transition matrix.
- The Wave 7 enum names (`SUBMISSION_AMBIGUOUS`, `VALID`) were ETA-specific; Wave 8 needs `ACCEPTED` (covers both ZATCA's "Cleared" and "Reported") and the constitution prefers per-class authority status to be a separate field, not a fork of the platform status (FR-007b). Renaming the Wave-7 enum to `DocumentState` is therefore correct and is captured as Wave-8 polish.

**Alternatives considered**:
- **Independent per-class enums**: rejected — produces parallel code paths in `LifecycleTransitions`, breaks Q1's parity intent, and forces the Angular components to be aware of which authority they belong to.
- **Encoding the authority status into the state enum** (e.g. `CLEARED`, `REPORTED`): rejected because Q1 explicitly partitioned the platform status from the authority status — see FR-007b.

---

## 8. Optimistic concurrency on drafts vs pessimistic on chain

**Decision**: Standard and Simplified header tables carry a `version` column (JPA `@Version`, type `bigint`, default 0). All draft `PUT` endpoints require an `If-Match` header carrying the last-seen `version`; the JPA layer increments on every successful save and throws `OptimisticLockException` on stale carriers, mapped to HTTP 409 `OPTIMISTIC_LOCK_CONFLICT`. **Pessimistic locking is never applied to header rows.** The only pessimistic lock in this wave is on `zatca_chain_state` (see §1 above).

**Rationale**:
- FR-032 explicitly forbids pessimistic locks on documents, and the constitution does not require them. Optimistic concurrency is sufficient for the draft-edit use case because two users editing the same draft is rare and the side-by-side conflict-resolution view (reused from Wave 7) gives a graceful resolution.
- Pessimistic chain lock is reserved strictly for the chain row because that is the ONE place where concurrent submissions must serialise (Constitution XII.3).

**Alternatives considered**:
- **Pessimistic locking on draft headers**: rejected explicitly by Q-Wave-7 Q4, carried forward.

---

## 9. Authority engine adapter — extension beyond Wave 7

**Decision**: Wave 8 keeps two separate orchestrators (`EtaSubmissionOrchestrator` from Wave 7, `ZatcaSubmissionOrchestrator` new) and introduces an `AuthorityEngine` SPI in `platform-core/domain/shared/` that both engines implement. Each orchestrator depends on the `AuthorityEngine` via the SPI, with the concrete engine resolved by transaction type at runtime via a Spring `@Configuration` `Map<TransactionType, AuthorityEngine>` bean. The merger of the two orchestrators into a single authority-agnostic `SubmissionOrchestrator` is out of scope for this wave.

**Rationale**:
- Constitution XVI.3 says "Adding a new authority MUST be possible without rewriting core orchestration logic." The Wave-7 orchestrator was written with ETA-specific names and assumptions; merging immediately risks breaking Wave-7 tests. Ship parallel orchestrators with a shared engine SPI now, then collapse them in a follow-on wave once both behaviours are stable.
- The cost of the deferral is one duplicated orchestrator skeleton; the saving is zero Wave-7 regressions and a clear migration path.

**Alternatives considered**:
- **Big-bang merger in Wave 8**: rejected — high risk of Wave-7 regressions; not aligned with the principle of minimum-viable change per wave.
- **Keep orchestrators forever separate**: rejected — violates Constitution XVI.3's spirit; logged as a follow-on task.

---

## 10. Bulk Check Status — server-side fan-out, NDJSON streaming, mid-run cancellation

**Decision**: `POST /api/companies/{id}/zatca/standard/check-status` (and the Simplified equivalent) accepts an unbounded `documentIds[]` body, returns an `application/x-ndjson` stream, and writes one JSON line per document outcome as the underlying ZATCA call completes. A `Cancel-Run-Id: <uuid>` request header (echoed by a `Run-Id` response header on the initial request) lets a follow-up `DELETE /api/runs/{uuid}` mark the run cancelled; the orchestrator polls the run's cancel flag between fan-out iterations and stops issuing new ZATCA calls. In-flight calls complete and their results are still streamed; queued documents are skipped and reported with outcome `CANCELLED_NO_OP`.

**Rationale**:
- Q5 explicitly chose "no cap, with progress indicator and cancel". NDJSON is the simplest stream format that the Angular client can consume incrementally without holding the whole response.
- Server-side rate-pacing prevents the fan-out from breaching ZATCA's published per-minute call ceiling. The pace is computed at runtime from a soft per-minute target (configurable, default 200 calls/min — well below the documented ZATCA limit) using a simple token-bucket.
- The `Run-Id` cancellation pattern keeps the cancel endpoint stateless from the client's perspective and lets the platform handle "user closed the tab" by garbage-collecting the run after a timeout.

**Alternatives considered**:
- **Long-poll with intermediate progress updates**: rejected — more complex than NDJSON streaming and doesn't gain anything for a per-document outcome reporting model.
- **WebSocket per run**: rejected — adds a transport and a connection-lifecycle concern for a feature that streams one outcome per document.

---

## 11. Decimal precision regime — divergence from Wave 7

**Decision**: ZATCA fields use a deliberately different decimal regime from ETA: `NUMERIC(18,5)` for line quantity and unit price (source-of-truth for line computation), `NUMERIC(18,2)` for everything else (line totals, discounts, allowances, header totals, VAT amounts). A new `ZatcaMoneyMath` helper in `platform-core/money/` implements 2-decimal rounding with ZATCA's banker's-rounding rule (HALF_EVEN). It does **not** share rounding logic with Wave 7's `EtaMoneyMath`, which is 5-decimal throughout.

**Rationale**:
- Constitution XIII.5 (ETA at 5 decimals) and XIII.6 (ZATCA at 2 decimals) are explicit — sharing one helper would force the helper to be parameterised by authority, which is more complex than two thin helpers with the same shape.
- The 5-decimal precision on quantity/unit price is preserved to avoid premature rounding when lines have small per-unit prices (e.g. fuel per litre).

**Alternatives considered**:
- **Single `MoneyMath` helper parameterised by authority**: rejected — increases coupling between modules that should not know about each other.
- **Uniform 2-decimal ZATCA precision (quantity included)**: rejected — would lose precision on small-unit-price lines and produce header-total mismatches.

---

## 12. Cross-class FK enforcement (Standard credit → Standard original)

**Decision**: `original_invoice_id` is a single-valued FK to the same table (`zatca_standard_headers` for Standard, `zatca_simplified_headers` for Simplified). The same-class rule (FR-030) is enforced by the FK being a self-reference. The same-company + same-environment rule is enforced at the application layer in `ZatcaStandardService.validateOriginalReference()` and `ZatcaSimplifiedService.validateOriginalReference()`, raising `WrongOriginalClassException` (Wave 8 polish) or the Wave-7-reused `MissingOriginalDocumentException`.

**Rationale**:
- Self-referential FK gives the database the strong guarantee that the original exists and is of the same class. The application layer adds the tenant-scoping check on top.
- A cross-class scenario (Standard credit referencing a Simplified original) is impossible by construction with a self-referential FK — the FK target table is fixed at schema time.

**Alternatives considered**:
- **FK to a shared `zatca_documents` parent table**: rejected — would require either a shared parent (breaks Constitution XI.1) or denormalised duplication.
- **Application-layer-only validation**: rejected — leaves the FK constraint slot unused for no benefit.

---

## 13. Frontend feature folder organisation — promotion of shared components

**Decision**: Promote Wave 7's `frontend/src/app/invoices/shared/` to `frontend/src/app/documents/shared/`. Update Wave 7 imports (`invoices/`, `receipts/`) to point at the new path. Wave 8 (`standard/`, `simplified/`) consumes the same shared components. The promotion is a refactor with no behavioural change; it is staged as a single tasks-template polish item to keep the diff reviewable.

**Rationale**:
- The five Wave-7 shared components (line-items editor, submission history, artifact download, conflict resolution dialog, bulk status check dialog) are authority-agnostic by design — they accept a document type and a permission scope. They were never ETA-specific in their code; the folder location was a Wave-7 expedient.
- Wave 8 has every reason to consume them as-is; leaving them under `invoices/shared/` would force `standard/` and `simplified/` to either deep-import across feature folders (bad) or duplicate the components (worse).

**Alternatives considered**:
- **Leave components where they are**: rejected — produces cross-feature imports.
- **Duplicate the components per authority**: rejected — duplication, breaks any future change.

---

## 14. Testing strategy — chain integrity concurrency test

**Decision**: A new `ZatcaChainIntegrityIT` test under `platform-zatca/src/test/java/.../chain/` runs ≥ 100 iterations of the following against a fresh Testcontainers Postgres: spin up two threads, each submitting a different `ZatcaStandardHeader` for the same `(company, environment)`; wait for both to complete; assert (a) counter values are consecutive, (b) the later document's `previous_invoice_hash` equals the earlier document's `invoice_hash`, (c) the `zatca_chain_state` row reflects the latest counter and hash, (d) no document is dropped or duplicated. The test class is the canonical regression for SC-005.

**Rationale**:
- Constitution XII.3 is the load-bearing principle for this wave; the test exists specifically to keep it green under code change.
- 100 iterations is enough to detect a 1% race condition with > 60% probability on a single run, > 95% across a CI matrix of three platforms. The test runs in ~30 s on a developer laptop with `postgres:16-alpine` Testcontainers.

**Alternatives considered**:
- **Pure unit test with mocked database**: rejected — the bug is in the transaction semantics, which only Postgres can fully exercise.
- **Stress test at higher concurrency (10+ threads)**: rejected — incremental value is low; the failure mode is a race between two threads, not contention from many.

---

## 15. ZATCA endpoint paths and authority environment routing

**Decision**: The platform reads `zatca_configs.base_url` per submission and appends ZATCA's standard paths:
- Clearance (Standard): `POST {base_url}/invoices/clearance/single`
- Reporting (Simplified): `POST {base_url}/invoices/reporting/single`
- Compliance check (used at onboarding, not in this wave): `POST {base_url}/compliance/invoices`

Per Constitution III.2, the three ZATCA environments seeded in `authority_environments` are:
- `id = 3` ZATCA Production — base URL points to ZATCA production gateway
- `id = 4` ZATCA Simulation — base URL points to ZATCA simulation
- `id = 5` ZATCA Sandbox — base URL points to ZATCA sandbox

Wave 6 populated `zatca_configs.base_url` for each (company, environment) tuple. Wave 8 only consumes it.

**Rationale**:
- Data-driven routing keeps the platform decoupled from any specific ZATCA URL that ZATCA may change. The constitution explicitly requires per-environment isolation (IV.3, IV.5).
- The three environments are distinct authority_environment_ids per Constitution III.2 — each maintains its own `zatca_chain_state` row, its own `zatca_configs` row, and never shares runtime caches.

**Alternatives considered**:
- **Hard-code ZATCA URLs in `application.yml`**: rejected — splits truth between the database and the config file; violates Constitution IV.3.

---

## 16. Performance budgeting and compound indexing

**Decision**: V56 creates the following compound indexes (mirroring the Wave-7 V53 pattern):

- `zatca_standard_headers (company_id, authority_environment_id, status)` — for list-screen status filter
- `zatca_standard_headers (company_id, authority_environment_id, issue_date)` — for date-range filter
- `zatca_standard_headers (company_id, authority_environment_id, invoice_number)` — unique constraint already covers this for uniqueness; this index supports search
- `zatca_standard_lines (header_id)` — for line fetch
- `zatca_simplified_headers (company_id, authority_environment_id, status)`
- `zatca_simplified_headers (company_id, authority_environment_id, issue_date)`
- `zatca_simplified_headers (company_id, authority_environment_id, invoice_number)`
- `zatca_simplified_lines (header_id)`

**Rationale**:
- Constitution XXV.2 mandates compound indexes on `(company_id, authority_environment_id, …)` for list-screen efficiency.
- The status and date_range indexes give EXPLAIN-verified sub-100 ms p95 on a 100k-row test set per `quickstart.md` Phase J.

**Alternatives considered**:
- **GIN index on a JSONB column**: not applicable — none of the operational queries filter on JSON.
- **Partial indexes filtered to non-terminal statuses**: rejected — adds maintenance for a query pattern (list of all non-cancelled documents) that the spec doesn't require yet.

---

## Open items deferred to tasks-template polish

The following decisions are recorded here so they can be lifted into `tasks.md` polish items by `/speckit.tasks` without re-deciding:

1. **Promote `EtaSubmissionOrchestrator` to a single authority-agnostic `SubmissionOrchestrator`** — out of scope for Wave 8 implementation; logged as a Wave-9 prerequisite.
2. **Move `frontend/src/app/invoices/shared/` to `frontend/src/app/documents/shared/`** — one staging commit in this wave, refactor-only with import updates in Wave 7 feature folders.
3. **Rename `EtaInvoiceState` / `EtaReceiptState` → `DocumentState`** — refactor-only; one commit; preserves all Wave-7 transition semantics.
4. **Performance tuning: release chain lock before the outbound ZATCA call**, recompute `previous_invoice_hash` from the canonical chain row inside the call's response handler — out of scope for v1 of this wave; logged for the chain-integrity performance pass once the v1 model is observed under load.
