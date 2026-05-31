# Phase 0 Research: Bulk Operations, Dashboard, Logs & Deployment

**Feature**: 004-bulk-ops-dashboard-deployment
**Date**: 2026-04-19
**Purpose**: Resolve every outstanding technical unknown before Phase 1 design. No entry below is left as NEEDS CLARIFICATION.

---

## R1. Bulk job persistence & crash-safe execution

**Decision**: Persist jobs and job-items in PostgreSQL (`jobs`, `job_items` tables). A single Spring `@Scheduled` poller in `platform-jobs` picks up `QUEUED` / `RUNNING` jobs every 2 s. The poller is guarded by **ShedLock 5.x** using the existing PostgreSQL datasource as the lock provider, so only one application instance processes a given job at a time. Work inside the poller uses the existing `TaskExecutor` (bounded thread pool) so ZATCA work is serialized per `(branch_id, environment)` (via a DB advisory lock) while ETA work parallelizes across branches.

**Rationale**:
- Satisfies Constitution IV (stateless service: state is in DB, not JVM memory) and XIII (restart safety + progress visibility).
- Avoids introducing Quartz or an external broker (Redis/RabbitMQ), which would violate XII's "no cloud-managed dependency" and inflate the on-prem install.
- ShedLock on the same PostgreSQL instance is a proven, zero-new-infrastructure pattern and is compatible with a future HA deployment if the customer scales out.
- Auto-resume (spec FR-010) is implemented by the poller itself: on every tick, any `RUNNING` job with an unclaimed next `QUEUED` item is picked up.

**Alternatives considered**:
- **Spring `@Async` without persisted queue**: rejected — loses all in-flight work on restart, violates FR-010.
- **Quartz Scheduler with its own schema**: rejected — heavier dependency than needed; ShedLock provides the one guarantee we need (mutual exclusion) without bringing a full scheduling engine.
- **External broker (Redis/RabbitMQ/Kafka)**: rejected — new runtime dependency, incompatible with the on-prem "no cloud-managed dependency" rule, and the volumes (≤10,000 items/job, ≤50 concurrent jobs) don't justify it.
- **Postgres `SELECT ... FOR UPDATE SKIP LOCKED` pull pattern** (no ShedLock): viable alternative; rejected only because ShedLock additionally protects auxiliary `@Scheduled` tasks (ETA package poller, certificate-expiry alert recomputation) with a single import.

---

## R2. ZATCA serialization vs ETA parallelization inside a bulk job

**Decision**: Serialize submissions per `(branch_id, environment)` for ZATCA by acquiring a PostgreSQL advisory lock on `hashtext('zatca:' || branch_id || ':' || environment)` before each submission and releasing it after the hash-chain update commits. ETA work inside the same job parallelizes across distinct branches (bounded by the `TaskExecutor` pool size, default 8), and within a branch it batches up to 100 documents per ETA `/documentsubmissions` call per ETA's own contract.

**Rationale**:
- Constitution I and III require hash-chain integrity per branch/environment; advisory locks were already adopted in Wave 2 (see plan 2.4.3) so this extends the existing pattern rather than inventing a new one.
- Advisory locks are session-scoped and survive application logic errors (released when the DB connection closes), giving us crash-safety without manual cleanup.
- ETA has no chain-ordering constraint, so serializing it would throw away throughput for no compliance benefit.

**Alternatives considered**:
- **Row-level lock on `authority_configs`**: works but couples locking semantics to a business row and forces long transactions. Rejected in favour of advisory locks which are explicitly designed for this.
- **Single-threaded bulk worker per process**: rejected — would bottleneck every tenant behind the slowest ZATCA branch.

---

## R3. Foreground streaming protocol

**Decision**: Foreground bulk submission uses **Server-Sent Events (SSE)** over the endpoint `POST /api/invoices/bulk-submit` (content-type `text/event-stream`). Each processed invoice emits one `progress` event; a final `summary` event closes the stream. No WebSocket is introduced.

**Rationale**:
- SSE is natively supported by Spring Boot (`SseEmitter`) and the browser `EventSource` API — zero new client dependencies.
- Fits the "simple streaming progress" use case; request is always one-way server-to-client.
- Works through the Nginx reverse proxy with a standard `proxy_read_timeout` tweak documented in the deployment guide.

**Alternatives considered**:
- **WebSocket**: overkill for one-way updates; adds STOMP/handshake complexity.
- **HTTP long-polling**: client churn and higher DB query pressure.

---

## R4. Dashboard aggregation performance

**Decision**: Dashboard KPIs are computed on demand from existing tables using **targeted indexes**, not from a materialized view. Required indexes (some already in V9, some new in V20):
- `invoices(company_id, status, issue_date)` — already present
- `invoices(company_id, authority, issue_date)` — new in V20
- `submission_attempts(company_id, completed_at DESC)` — new in V20, backs the recent-activity feed
- `authority_configs(branch_id, certificate_expiry_date)` — new in V20, backs the certificate-expiry alert query

**Rationale**:
- On-demand aggregation with tuned indexes comfortably meets SC-004 (p95 < 2 s) for 100,000-invoice companies; Postgres 16 handles `GROUP BY status` over that range in the low-hundreds-of-milliseconds range on modest hardware.
- Avoids the consistency risk of a materialized view (stale counters after submission) and the operational burden of a refresh strategy.
- Compatible with Constitution IV — no separate analytics store added.

**Alternatives considered**:
- **Materialized view refreshed on a schedule**: rejected — staleness window would confuse operators triaging failures in near-real-time.
- **In-memory cache with TTL (Caffeine)**: considered as a future optimization; deferred unless real-world measurements show aggregation hot spots.

---

## R5. ZATCA PDF decision (from Wave 2 spike)

**Decision**: The Wave 2 research spike (`Docs/zatca-pdf-decision.md`, owned by Wave 2) produces a binary outcome. This plan is written to accommodate both outcomes:
- **If "generate PDF"**: activate `platform-pdf` with **OpenHTMLToPDF 1.0.x** rendering a bilingual Thymeleaf template; store the resulting PDF as an immutable `invoice_artifact` with type `ZATCA_CUSTOMER_PDF` (new enum value in migration V22). Expose `GET /api/invoices/{id}/zatca-pdf`.
- **If "do not generate"**: `platform-pdf` remains a package-info-only stub, no PDF endpoint is exposed for ZATCA, and the existing `GET /api/invoices/{id}/artifact/xml` is the canonical customer artifact. Migration V22 is not created.

**Rationale for OpenHTMLToPDF (conditional)**:
- Pure Java, no native dependencies — compatible with on-prem Docker image constraints.
- Renders the QR PNG (already generated server-side via ZXing in Wave 2) directly from the signed XML's embedded TLV.
- Supports RTL Arabic layout via CSS `direction` property with no extra plugin, simpler than Flying Saucer's font-configuration gymnastics.
- Smaller attack surface than JasperReports (which ships its own scripting engine).

**Alternatives considered**:
- **iText 7 / 8 Core**: AGPL license imposes redistribution constraints incompatible with a proprietary on-prem deliverable; commercial license cost not justified for a simple invoice layout.
- **JasperReports**: richer but heavier; its scripting engine is an unnecessary risk surface.
- **Flying Saucer**: viable but less actively maintained than OpenHTMLToPDF (which is its direct successor).

---

## R6. ETA bulk package polling cadence

**Decision**: A dedicated `@Scheduled` poller `EtaBulkPackagePoller` runs every 60 s (configurable via `einvoice.jobs.eta-bulk-package.poll-interval`). For each open `eta_bulk_package_requests` row, it calls `GET /documentpackages/{packageId}` and transitions the row through states: `REQUESTED` → `PREPARING` → `READY` (with `download_url_ref`) or `FAILED`. Users see readiness in the UI; no webhook is used (spec assumption confirms polling).

**Rationale**:
- 60 s is slow enough to avoid ETA rate-limit pressure, fast enough to feel responsive for the operator — typical package prep runs in minutes.
- Shares the ShedLock infrastructure from R1 so only one instance polls at a time.

**Alternatives considered**:
- **User-triggered "refresh" button only**: rejected — makes the feature feel inert and shifts the burden to the operator.
- **Exponential backoff polling**: nice-to-have; added to the post-MVP backlog only if ETA begins throttling.

---

## R7. On-prem deployment topology

**Decision**: Two Compose files ship: `docker-compose.yml` (dev, existing, kept unchanged) and `docker-compose.prod.yml` (new). The production composition runs four services:
1. `db` — PostgreSQL 16 official image, persistent volume `einvoice-pgdata`, environment-injected credentials.
2. `backend` — multi-stage Maven build producing a slim Eclipse Temurin 17 JRE image; reads secrets from environment / mounted files; exposes only internal network.
3. `frontend` — multi-stage Node build producing an Nginx image serving the Angular `dist/`; exposes only internal network.
4. `proxy` — Nginx reverse proxy, binds host ports 80/443, redirects 80→443, terminates TLS with operator-provided cert mounted at `/etc/nginx/tls/`, adds HSTS + CSP + standard security headers, proxies `/api` to backend, `/` to frontend. Supports SSE via long-lived connections.

DB and backend run on the same Docker network but distinct containers, honoring Constitution XII.3 ("App server and DB server MUST be logically separated").

**Rationale**: This is the least-surprising shape for operators familiar with on-prem Compose deployments, uses only Free & Open Source Software, and needs zero cloud-managed components.

**Alternatives considered**:
- **Kubernetes / Helm chart**: over-provisioned for a single-tenant on-prem installation; deferred to post-MVP for customers who specifically request it.
- **Bare-metal systemd units**: higher operator burden (managing JVM, Node, Postgres installations); rejected.

---

## R8. Backup and restore strategy

**Decision**: Use `pg_dump --format=custom` for backups (`backup.sh`) and `pg_restore --clean --if-exists` for restores (`restore.sh`). Backups are written to a host path bind-mounted at `/var/backups/einvoice` (outside the DB volume), timestamped `einvoice_YYYYMMDD_HHMMSS.dump`, and compressed by `pg_dump`'s built-in custom format. The script also snapshots the artifact volume tarball (`/var/lib/einvoice/artifacts`) alongside the SQL dump so signed XML / PDF / QR blobs that live on disk are captured in the same restore atom. Restore refuses to run against a non-empty database unless the operator passes `--force`.

**Rationale**:
- `pg_dump` is the universally supported, well-documented Postgres backup tool; custom format compresses and supports selective restore.
- Bundling the artifact filesystem with the SQL dump prevents a restored database from pointing at missing artifact blobs.
- Forces operator intent to overwrite live data, preventing destructive accidents (spec edge case: restore against newer data).

**Alternatives considered**:
- **Physical base backup + WAL archiving**: richer (point-in-time recovery) but more operator training to restore; deferred to post-MVP unless required by a specific customer.
- **Storing artifacts inside the database as `bytea`**: rejected in Wave 2; keeping artifacts on the filesystem is unchanged here.

---

## R9. Upgrade script idempotency

**Decision**: `upgrade.sh` takes a target image tag, performs: `docker compose pull` → `docker compose run --rm backend java -jar app.jar --flyway-migrate-only` → `docker compose up -d`. It reads the current tag from a persisted `./deploy/VERSION` file; if the requested tag equals the current one, the script logs "already at <tag>" and exits 0 without touching containers. Flyway itself is idempotent on already-applied migrations.

**Rationale**: Satisfies spec FR-033 and SC-010 (idempotent upgrade). Separates migration application from container restart so any migration failure aborts before the user-facing containers are replaced.

**Alternatives considered**:
- **Rolling restart without explicit migration step**: rejected — migration failure would leave the new backend crash-looping.

---

## R10. Log-explorer pagination defaults

**Decision**: Both `/api/audit-logs` and `/api/submission-logs` default to a 30-day window and page size 50 (max 200). An explicit `from` + `to` filter can widen the window up to 365 days; windows beyond 365 days require a `confirmWide=true` query flag, surfaced in the UI via an expanded-scope toggle. Cursor-based pagination over `(timestamp DESC, id DESC)` is used rather than OFFSET to keep late-page latency flat.

**Rationale**:
- Enforced defaults prevent the "no filter on 10 years of logs" edge case.
- Cursor pagination scales with indefinite retention (the clarified policy from session 2026-04-19).
- 50-row pages match the spec's "readable browsing" phrase in Scenario 4 of User Story 3.

**Alternatives considered**:
- **Offset pagination**: simple but degrades as `audit_logs` grows; given the indefinite retention policy, rejected.
- **No defaults, full open-ended query**: rejected — directly contradicts the last edge case ("enforces a default date range and/or pagination").

---

## R11. Invoice spreadsheet export strategy

**Decision**: Use **Apache POI SXSSFWorkbook** (streaming variant) in `InvoiceExportController` with a `ResponseBodyEmitter` writing directly to the response stream. Rows are pulled from the database with a Spring Data `Stream<Invoice>` inside a read-only transaction. The same filter predicates as the Invoice List endpoint are reused so export always matches the on-screen view.

**Rationale**: SXSSF keeps only a rolling window of rows in memory, so a 10,000-row export fits in well under 200 MB of heap. Meets SC-011 (under 1 minute for 10,000 rows on modest hardware).

**Alternatives considered**:
- **Build full workbook in memory**: fine at 1,000 rows but degrades at scale; rejected because the spec's edge case explicitly calls out 10,000-row exports.
- **CSV instead of xlsx**: smaller and faster but breaks parity with existing customer/item import templates (which are xlsx); rejected.

---

## R12. Authority-response cache for ETA PDFs

**Decision**: The proxy endpoint `GET /api/invoices/{id}/eta-pdf` first checks for an existing `invoice_artifacts` row of type `ETA_CUSTOMER_PDF` (new enum value in V22 migration) and returns it if present. If absent, it calls ETA's `GET /documents/{documentId}/pdf`, stores the response as an immutable artifact, and returns the freshly fetched bytes. Cache has no TTL — ETA PDFs are considered final once emitted.

**Rationale**: Fulfills FR-025 (don't re-hit ETA unnecessarily). Using the existing immutable-artifact table keeps Constitution XIV happy (document preservation) and avoids introducing a separate cache store.

**Alternatives considered**:
- **In-memory Caffeine cache**: transient, doesn't satisfy "persist" intent and doesn't survive restart.
- **External object store (S3/MinIO)**: extra infrastructure; rejected for on-prem simplicity.

---

## Summary

All 12 research items resolve to concrete, on-prem-compatible decisions. Zero NEEDS CLARIFICATION markers remain. Phase 1 can proceed to data model and contract design without further ambiguity.
