---

description: "Task list for Wave 3 — Bulk Operations, Dashboard, Logs & Deployment"
---

# Tasks: Bulk Operations, Dashboard, Logs & Deployment

**Input**: Design documents from `specs/004-bulk-ops-dashboard-deployment/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/ (bulk-submission, jobs, dashboard, logs, eta-bulk-packages, invoice-artifacts, invoice-export), quickstart.md

**Tests**: Constitution XVI mandates tests for state machine, tenant isolation, authority contracts, and critical rules. Tests are included in each story phase below but implementation is not strict TDD-first — write tests alongside each component.

**Organization**: Tasks are grouped by user story (US1–US6) to enable independent implementation and demo. Setup (Phase 1) and Foundational (Phase 2) are shared prerequisites. Final Phase covers polish and cross-cutting concerns.

## Path Conventions

Multi-module Maven backend + Angular workspace at repo root. Prefixes used below:

- `platform-core/src/main/resources/db/migration/` — Flyway SQL
- `platform-jobs/src/main/java/com/einvoice/jobs/` — new backend module for bulk + pollers
- `platform-api/src/main/java/com/einvoice/api/` — controllers, DTOs
- `platform-pdf/src/main/java/com/einvoice/pdf/` — conditional PDF module
- `frontend/src/app/` — Angular feature modules
- `deploy/` — new on-prem deployment assets at repo root
- `Docs/` — operator documentation

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Bring in new backend dependencies, scaffold the deploy directory, and establish configuration properties used by later phases.

- [ ] T001 [P] Add ShedLock 5.x (`shedlock-spring` + `shedlock-provider-jdbc-template`) dependency to `platform-jobs/pom.xml` and document the `shedlock` table bootstrap in a parent POM comment.
- [ ] T002 [P] Add Apache POI streaming (`poi-ooxml` already present — confirm version + add `commons-io` if needed) to `platform-api/pom.xml` for streaming exports.
- [ ] T003 [P] Add OpenHTMLToPDF 1.0.x (`com.openhtmltopdf:openhtmltopdf-pdfbox`) as an **optional** dependency block in `platform-pdf/pom.xml`, gated behind the Maven property `zatca.pdf.enabled` (default `false`) so it only activates when the Wave 2 decision requires platform-generated ZATCA PDFs.
- [ ] T004 Add new config properties to `platform-api/src/main/resources/application.yml` under a new `einvoice.wave3` tree: `jobs.poll-interval=2s`, `jobs.parallelism=8`, `jobs.shedlock-table=shedlock`, `eta-bulk-package.poll-interval=60s`, `artifacts.root=/var/lib/einvoice/artifacts`, `dashboard.cert-expiry-window-days=30`.
- [ ] T005 [P] Create the top-level `deploy/` directory skeleton: `deploy/docker/`, `deploy/docker/nginx/`, `deploy/docker/nginx/tls/.gitkeep`, `deploy/scripts/`, `deploy/env/` with empty `.gitkeep` files so subsequent tasks can drop artifacts into known paths.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Schema, domain model, shared services, and scheduler infrastructure that every user story depends on.

**⚠️ CRITICAL**: All user-story work (Phase 3+) is blocked until Phase 2 completes.

### Database migrations

- [ ] T006 Author `platform-core/src/main/resources/db/migration/V20__create_jobs_and_job_items.sql` — create enums `job_type`, `job_status`, `job_item_status`; tables `jobs` (with CHECK on aggregate counts) and `job_items` (with UNIQUE `(job_id, sequence_index)`); indexes `jobs_company_status_created_at`, `jobs_status_created_at`, `job_items_job_status`, `job_items_invoice`.
- [ ] T007 Author `platform-core/src/main/resources/db/migration/V21__create_eta_bulk_package_requests.sql` — enum `eta_package_status`; table `eta_bulk_package_requests`; indexes `eta_pkg_requests_status_updated`, `eta_pkg_requests_company_created`.
- [ ] T008 Author `platform-core/src/main/resources/db/migration/V22__extend_artifact_types.sql` — add `ETA_CUSTOMER_PDF` and `ETA_BULK_PACKAGE` to the `artifact_type` enum. If the Wave 2 ZATCA PDF decision is "generate", also add `ZATCA_CUSTOMER_PDF`; if not, skip that line and annotate the migration with the decision reference.
- [ ] T009 Author `platform-core/src/main/resources/db/migration/V23__add_index_tuning_for_wave3.sql` — indexes `invoices_company_authority_issue_date`, `submission_attempts_company_completed_at`, `authority_configs_expiry` (partial WHERE not null), `audit_logs_cursor (company_id, timestamp DESC, id DESC)`, `submission_attempts_cursor (company_id, completed_at DESC, id DESC)`.
- [ ] T010 Add the ShedLock bootstrap migration at `platform-core/src/main/resources/db/migration/V24__create_shedlock_table.sql` per the ShedLock 5 JDBC schema (`name`, `lock_until`, `locked_at`, `locked_by`).

### Backend domain model & repositories

- [ ] T011 [P] Create JPA entity `platform-jobs/src/main/java/com/einvoice/jobs/domain/Job.java` with fields from data-model.md, `@Version` optimistic lock, lifecycle timestamps, `@Enumerated(STRING)` for `type`/`status`.
- [ ] T012 [P] Create JPA entity `platform-jobs/src/main/java/com/einvoice/jobs/domain/JobItem.java` with `@ManyToOne` back to `Job`, unique constraint on `(job_id, sequence_index)`.
- [ ] T013 [P] Create JPA entity `platform-jobs/src/main/java/com/einvoice/jobs/domain/EtaBulkPackageRequest.java`.
- [ ] T014 [P] Create Spring Data repositories `JobRepository`, `JobItemRepository`, `EtaBulkPackageRequestRepository` under `platform-jobs/src/main/java/com/einvoice/jobs/repository/` — each with tenant-scoped methods (`findByCompanyId(...)`) and one cursor-paginated signature where relevant.
- [ ] T015 [P] Create shared cursor-pagination utility `platform-api/src/main/java/com/einvoice/api/common/CursorPaging.java` (encodes `(timestamp, id)` pairs as URL-safe base64, decodes for WHERE predicates).

### Scheduler & locking infrastructure

- [ ] T016 Create `platform-jobs/src/main/java/com/einvoice/jobs/config/JobsSchedulingConfig.java` — `@EnableScheduling`, `@EnableSchedulerLock(defaultLockAtMostFor="PT5M")`, `LockProvider` bean backed by `JdbcTemplateLockProvider` on the app datasource, `ThreadPoolTaskExecutor` (core=4, max=8, queue=100) named `einvoiceJobsExecutor`.
- [ ] T017 Create `platform-jobs/src/main/java/com/einvoice/jobs/config/JobsProperties.java` (`@ConfigurationProperties("einvoice.wave3.jobs")`) binding the config keys added in T004.
- [ ] T018 Extend the existing `SubmissionOrchestrator` (Wave 2) in `platform-core` with a `submitForBulk(invoiceId, bulkContext)` entry point that: acquires the `(branch, environment)` advisory lock for ZATCA, increments the hash chain inside the same TX, and returns a `SubmissionResult` without touching the orchestrator's existing single-submit path.

### Shared infrastructure — audit + security

- [ ] T019 Register new audit event constants in the existing `AuditAction` enum (`BULK_JOB_CREATED`, `BULK_JOB_CANCELLED`, `BULK_JOB_REPORT_DOWNLOADED`, `ETA_BULK_PACKAGE_REQUESTED`, `ETA_BULK_PACKAGE_DOWNLOADED`, `ETA_BULK_PACKAGE_ABANDONED`, `INVOICE_EXPORT_REQUESTED`, `DEPLOYMENT_UPGRADE_APPLIED`) in `platform-core/src/main/java/com/einvoice/core/audit/AuditAction.java`.
- [ ] T020 Create `platform-api/src/main/java/com/einvoice/api/config/WaveThreeSecurityConfig.java` — method-security annotations wiring for new controllers; default-deny filter rule; explicit Super-Admin bypass only for `/api/dashboard/super-admin`.

### Frontend shared pieces

- [ ] T021 [P] Create `frontend/src/app/shared/alerts/alert-card.component.ts` (standalone component) rendering an `AlertItem` (severity chip, message, optional link). Used by dashboard and later screens.
- [ ] T022 [P] Create `frontend/src/app/shared/streaming/sse-client.ts` — thin RxJS wrapper over `EventSource`, returns an `Observable<{event: string, data: any}>`, supports bearer token via query param or fallback polling if the server rejects SSE.
- [ ] T023 [P] Create `frontend/src/app/shared/streaming/blob-download.ts` — helper to stream a binary response into a saved `Blob` with `Content-Disposition`-derived filename; used by job report, export, package download.
- [ ] T024 [P] Add cursor-pagination types and a small `CursorPaged<T>` interface to `frontend/src/app/shared/models/cursor-page.model.ts`.

**Checkpoint**: Foundation ready — all user stories can start.

---

## Phase 3: User Story 1 — Bulk submit draft invoices (Priority: P1) 🎯 MVP

**Goal**: An accountant selects 50+ validated invoices and submits them either in the foreground (streamed progress) or as a durable background job that survives restart, enforces ZATCA serialization, respects cancel, and produces a spreadsheet report.

**Independent Test**: From the invoice list, multi-select 50 ZATCA-ready drafts → click "Submit in background" → confirm the Jobs screen shows progress, cancel mid-run works, and the downloaded report lists per-invoice outcomes with no cross-tenant leakage.

### Tests for User Story 1

- [ ] T025 [P] [US1] Write `JobStateMachineTest` in `platform-jobs/src/test/java/com/einvoice/jobs/domain/JobStateMachineTest.java` covering every valid transition (QUEUED→RUNNING→COMPLETED/FAILED/CANCELLED, RUNNING→CANCELLED, QUEUED→CANCELLED, auto-resume re-entry QUEUED→RUNNING after restart) and rejecting invalid transitions.
- [ ] T026 [P] [US1] Write `BulkOrchestrationIntegrationTest` using Testcontainers-Postgres at `platform-jobs/src/test/java/com/einvoice/jobs/BulkOrchestrationIntegrationTest.java`:
  - 50-invoice background job happy path.
  - Cancel mid-run stops further submissions within 3 s; already-submitted invoices keep their state.
  - ZATCA serialization: launch 2 concurrent bulk jobs against the same branch; verify hash chain is strictly sequential (no shared chain positions).
- [ ] T027 [P] [US1] Write `JobRestartReconciliationIntegrationTest` — start a job, kill the application mid-item (simulate by advancing state to `IN_PROGRESS` then restarting `JobPoller`), assert the item is reconciled against a WireMock-backed authority stub and transitions to the correct terminal state.
- [ ] T028 [P] [US1] Write contract tests `BulkSubmissionContractTest` and `JobsContractTest` in `platform-api/src/test/java/com/einvoice/api/contract/` validating MockMvc responses against `contracts/bulk-submission.yaml` and `contracts/jobs.yaml` (use `swagger-request-validator-spring-mock-mvc`).
- [ ] T029 [P] [US1] Write `JobsTenantIsolationTest` — two tenants, each creates a job; cross-tenant `GET /api/jobs/{id}` returns 404.

### Backend implementation

- [ ] T030 [P] [US1] Create DTOs `BulkSubmitRequest`, `ProgressEvent`, `JobSummary`, `JobDto`, `JobItemDto`, `JobListPage`, `JobDetail` under `platform-api/src/main/java/com/einvoice/api/bulk/dto/` and `platform-api/src/main/java/com/einvoice/api/jobs/dto/`, Jackson-serializable to match the contracts verbatim.
- [ ] T031 [US1] Implement `platform-jobs/src/main/java/com/einvoice/jobs/service/BulkSubmissionService.java` with two entry points:
  - `streamForeground(request, SseEmitter)` — processes items synchronously, emits `progress`/`summary` events.
  - `enqueueBackground(request)` — creates a `Job` + `JobItem` rows inside a single transaction, skips invoices already in terminal accepted state (increments `skippedCount` and records the reason), returns the job id.
  Both paths share a private `processOne(JobItem, BulkContext)` that delegates to the Wave 2 `SubmissionOrchestrator.submitForBulk(...)`.
- [ ] T032 [US1] Implement `platform-jobs/src/main/java/com/einvoice/jobs/service/JobPoller.java` with a `@Scheduled(fixedDelayString = "${einvoice.wave3.jobs.poll-interval}")` + `@SchedulerLock(name="einvoice-job-poller")` method that: picks the oldest `QUEUED` or `RUNNING` job, claims the next `PENDING` `JobItem`, groups ZATCA items by `(branch, environment)` and dispatches them through the advisory lock, fires ETA items through the `TaskExecutor` bounded to per-branch parallelism.
- [ ] T033 [US1] Implement `platform-jobs/src/main/java/com/einvoice/jobs/service/JobReconciliationService.java` with `@EventListener(ApplicationReadyEvent.class)` — for every `job_items` row in `IN_PROGRESS` or `NEEDS_RECONCILIATION`, query authority state (via existing `ZatcaStatusService` / `EtaStatusService`) and transition to SUCCEEDED, FAILED, or mark for manual review (`SUBMISSION_AMBIGUOUS` on the invoice state machine).
- [ ] T034 [US1] Implement `platform-jobs/src/main/java/com/einvoice/jobs/service/JobReportService.java` — produces a streaming XLSX report for a given job id using Apache POI `SXSSFWorkbook`; columns: sequenceIndex, invoiceNumber, status, authority, environment, submittedAt, errorSummary.
- [ ] T035 [US1] Implement `platform-api/src/main/java/com/einvoice/api/bulk/BulkSubmissionController.java` with:
  - `POST /api/invoices/bulk-submit` returning `SseEmitter` (foreground)
  - `POST /api/jobs/bulk-submit` returning `202 Accepted` with `JobAcceptedResponse`
  Both enforce `@PreAuthorize("hasAnyRole('ACCOUNTANT','COMPANY_ADMIN','SUPER_ADMIN')")` + tenant scope + environment permission. Records `BULK_JOB_CREATED` audit entry.
- [ ] T036 [US1] Implement `platform-api/src/main/java/com/einvoice/api/jobs/JobController.java` with `GET /api/jobs` (cursor-paginated), `GET /api/jobs/{id}` (paginated items), `POST /api/jobs/{id}/cancel`, `GET /api/jobs/{id}/report`. Cancel emits `BULK_JOB_CANCELLED`; report download emits `BULK_JOB_REPORT_DOWNLOADED`.

### Frontend implementation

- [ ] T037 [P] [US1] Create feature module scaffolding `frontend/src/app/jobs/jobs.routes.ts` and register lazy route `/jobs` in `frontend/src/app/app.routes.ts`.
- [ ] T038 [P] [US1] Create `frontend/src/app/jobs/services/jobs.service.ts` — typed HTTP client for `/api/jobs` (list, detail, cancel, download report), uses `CursorPaged<JobDto>` from shared types.
- [ ] T039 [US1] Create `frontend/src/app/jobs/jobs-list/jobs-list.component.ts` with Angular Material table, status chips, filter form (Reactive Forms: status select, date range), polls list every 5 s while mounted.
- [ ] T040 [US1] Create `frontend/src/app/jobs/job-detail/job-detail.component.ts` — displays job header, paginated item table, cancel button (disabled on terminal states), download-report button using `blob-download` utility.
- [ ] T041 [P] [US1] Create `frontend/src/app/invoices/components/bulk-submit-dialog/bulk-submit-dialog.component.ts` (Angular Material dialog) with two peer buttons "Submit" and "Submit in background"; always renders both regardless of selection size (FR-002a); emits the chosen mode to the caller.
- [ ] T042 [US1] Create `frontend/src/app/invoices/components/bulk-progress-modal/bulk-progress-modal.component.ts` that consumes the SSE stream from `/api/invoices/bulk-submit` via the shared `sse-client`, renders a per-invoice progress table and a final summary; handles transient disconnect by surfacing a recoverable error (FR-012/FR-037).
- [ ] T043 [US1] Extend `frontend/src/app/invoices/invoices-list/invoices-list.component.ts` — add multi-select column, action bar that opens the bulk-submit dialog with the current selection, disables actions during in-flight requests (FR-038).

### Smoke validation

- [ ] T044 [US1] Run quickstart.md §1 end-to-end against the dev stack: 50-invoice background job → cancel → resume after restart → report download. Commit the run's observed numbers (duration, success/failed counts) into `specs/004-bulk-ops-dashboard-deployment/.run-log` so deferred polish tasks can compare.

**Checkpoint**: User Story 1 is fully functional and demo-ready as the MVP increment.

---

## Phase 4: User Story 2 — Dashboard triage (Priority: P2)

**Goal**: Company Admin logs in, sees KPI cards (today/month by status/authority), recent activity feed, and actionable alerts (failed submissions, cert expiring within 30 days, incomplete configs). Super Admin sees cross-tenant view.

**Independent Test**: Seed a tenant to 100k invoices and flip a cert `expiry_date` to `NOW() + 20 days`; load dashboard and confirm KPIs, activity feed, and cert-expiry alert render; p95 page load < 2 s.

### Tests for User Story 2

- [ ] T045 [P] [US2] Write `DashboardServiceTest` at `platform-api/src/test/java/com/einvoice/api/dashboard/DashboardServiceTest.java` covering: zero-state tenant, single-authority tenant (ZATCA only), dual-authority tenant, cert within 30 days triggers alert, cert at 31 days does not, failed-submissions count accurate.
- [ ] T046 [P] [US2] Write `DashboardPerformanceTest` — seed 100,000 invoices for one tenant via SQL bulk insert, measure `GET /api/dashboard` p95 over 20 runs; fail if > 2 s.
- [ ] T047 [P] [US2] Write `DashboardContractTest` validating responses against `contracts/dashboard.yaml`.
- [ ] T048 [P] [US2] Write `DashboardTenantIsolationTest` — ensure company B's invoices never leak into company A's dashboard.

### Backend implementation

- [ ] T049 [P] [US2] Create `platform-api/src/main/java/com/einvoice/api/dashboard/dto/` DTOs (`DashboardPayload`, `DashboardKpis`, `KpiBucket`, `AuthorityKpiSlice`, `RecentActivityItem`, `AlertItem`, `SuperAdminDashboardPayload`) matching `contracts/dashboard.yaml`.
- [ ] T050 [US2] Implement `platform-api/src/main/java/com/einvoice/api/dashboard/DashboardService.java` — three parallel aggregation queries (today KPIs, month KPIs, recent activity) using the V23 indexes; alert derivation in `AlertDerivationService` sub-component (cert expiry, failed count, inactive config).
- [ ] T051 [US2] Implement `platform-api/src/main/java/com/einvoice/api/dashboard/DashboardController.java` — `GET /api/dashboard` (tenant-scoped), `GET /api/dashboard/super-admin` (SUPER_ADMIN only).

### Frontend implementation

- [ ] T052 [P] [US2] Extend `frontend/src/app/dashboard/dashboard.service.ts` to call the composite `/api/dashboard` endpoint and the super-admin variant conditionally.
- [ ] T053 [US2] Rewrite `frontend/src/app/dashboard/dashboard.component.ts`: KPI card grid (today + month, grouped by status, authority tabs conditional on `enabledAuthorities`), recent activity feed (last 10 attempts), alerts section rendering `AlertCardComponent` from T021, quick-action buttons (New Invoice, View Invoices).
- [ ] T054 [P] [US2] Create `frontend/src/app/dashboard/super-admin-dashboard.component.ts` routed at `/dashboard/super-admin` with an `ngIf` on the SUPER_ADMIN role guard.

**Checkpoint**: User Stories 1 and 2 are both independently demoable.

---

## Phase 5: User Story 3 — Logs explorer (Priority: P2)

**Goal**: Auditor filters audit log by user/entity/date and submission log by invoice/authority/result; every entry is read-only; tenant-scoped; cursor pagination keeps late-page latency flat under indefinite retention.

**Independent Test**: Filter submission log by a known invoice with 3 attempts → all 3 listed with artifact links; filter audit log by a user and date range → changes listed with before/after; `PATCH /api/audit-logs/{id}` returns 405; a 3-year range without `confirmWide` returns 400.

### Tests for User Story 3

- [ ] T055 [P] [US3] Write `LogsContractTest` validating `GET /api/audit-logs` and `GET /api/submission-logs` against `contracts/logs.yaml`, including 400 when window > 365 days without `confirmWide=true`.
- [ ] T056 [P] [US3] Write `LogsImmutabilityTest` — `PATCH`, `PUT`, `DELETE` on `/api/audit-logs/{id}` and `/api/submission-logs/{id}` all return 405 (method not allowed). No such route should be registered.
- [ ] T057 [P] [US3] Write `LogsTenantIsolationTest` — company B's audit + submission entries do not surface in company A's explorer queries.
- [ ] T058 [P] [US3] Write `LogsCursorPaginationTest` — seed 500 audit entries, walk with `limit=50` + cursor; verify complete, non-overlapping, order-stable pages.

### Backend implementation

- [ ] T059 [P] [US3] Create DTOs `AuditLogEntryDto`, `AuditLogPage`, `SubmissionLogEntryDto`, `SubmissionLogPage` in `platform-api/src/main/java/com/einvoice/api/logs/dto/`.
- [ ] T060 [US3] Implement `platform-api/src/main/java/com/einvoice/api/logs/AuditLogQueryService.java` with tenant-scoped cursor-paginated `find(...)` honoring every filter parameter and the 365-day window rule.
- [ ] T061 [US3] Implement `platform-api/src/main/java/com/einvoice/api/logs/SubmissionLogQueryService.java` — same shape, joins `submission_attempts` to `invoices` and builds artifact href list.
- [ ] T062 [US3] Implement `platform-api/src/main/java/com/einvoice/api/logs/AuditLogController.java` (`GET` only) and `SubmissionLogController.java` (`GET` only). No other HTTP verbs defined; verify via `RequestMappingHandlerMapping` introspection in T056.

### Frontend implementation

- [ ] T063 [P] [US3] Create `frontend/src/app/logs/logs.routes.ts` and register lazy `/logs` route with child routes `/logs/audit` and `/logs/submissions`.
- [ ] T064 [P] [US3] Create `frontend/src/app/logs/services/audit-log.service.ts` and `frontend/src/app/logs/services/submission-log.service.ts` — typed HTTP clients with cursor-pagination wiring.
- [ ] T065 [US3] Create `frontend/src/app/logs/audit-log-explorer/audit-log-explorer.component.ts` — filter form (Reactive Forms: from, to, userId, action, entityType, entityId), Material table with expandable row for payloadBefore/After JSON diff, "Load more" button for next cursor.
- [ ] T066 [US3] Create `frontend/src/app/logs/submission-log-explorer/submission-log-explorer.component.ts` — filter form, table with link to invoice detail and artifact downloads.
- [ ] T067 [US3] Add the "confirm wide window" UX: when from/to exceed 365 days, show a warning + explicit toggle that passes `confirmWide=true`.

**Checkpoint**: User Stories 1–3 all work independently.

---

## Phase 6: User Story 4 — On-prem deployment (Priority: P2)

**Goal**: Single install command brings up the full stack over HTTPS on a clean Linux host; backup/restore is lossless; upgrade is idempotent; every secret comes from env/mounted store.

**Independent Test**: On a clean VM, run `install.sh` → reach login page in <15 min → create a tenant + invoice → `backup.sh` → destroy DB volume → `restore.sh --force` → verify data; run `upgrade.sh` with current tag (no-op) then new tag (applies migrations, restarts).

### Container & compose assets

- [ ] T068 [P] [US4] Create `deploy/docker/Dockerfile.backend` — multi-stage: stage 1 `maven:3.9-eclipse-temurin-17` runs `mvn -B -pl platform-api -am package -DskipTests`; stage 2 `eclipse-temurin:17-jre` copies the fat jar, sets non-root user, exposes 8080, honors `JAVA_OPTS` env.
- [ ] T069 [P] [US4] Create `deploy/docker/Dockerfile.frontend` — multi-stage: stage 1 `node:20-alpine` builds Angular prod bundle; stage 2 `nginx:1.27-alpine` copies `dist/` to `/usr/share/nginx/html` with a minimal static config.
- [ ] T070 [P] [US4] Create `deploy/docker/nginx/nginx.conf` — listen 80 → 301 redirect to 443; listen 443 SSL with `/etc/nginx/tls/fullchain.pem` + `privkey.pem`; proxy `/api` to `backend:8080` with `proxy_buffering off` and `proxy_read_timeout 3600s` (SSE support); proxy `/` to `frontend:80`; emit HSTS, `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, CSP header.
- [ ] T071 [P] [US4] Create `deploy/docker-compose.prod.yml` — four services (`db`, `backend`, `frontend`, `proxy`), named volume `einvoice-pgdata` for Postgres, bind mount `/var/lib/einvoice/artifacts` into backend at `/var/lib/einvoice/artifacts`, bind mount `/etc/ssl/einvoice` into proxy at `/etc/nginx/tls:ro`, `db` on internal network only, all secrets via env file.

### Install & operations scripts

- [ ] T072 [P] [US4] Create `deploy/scripts/install.sh` (POSIX) — checks prerequisites (docker ≥24, disk space), loads env file, writes VERSION file, `docker compose -f deploy/docker-compose.prod.yml up -d --wait`, prints the resolved HTTPS URL. Support `--skip-if-running`.
- [ ] T073 [P] [US4] Create `deploy/scripts/install.ps1` — PowerShell equivalent of T072 for Windows hosts.
- [ ] T074 [P] [US4] Create `deploy/scripts/backup.sh` — runs `docker compose exec -T db pg_dump --format=custom --no-owner --dbname=$POSTGRES_DB --username=$POSTGRES_USER` to `./einvoice_$(date +%Y%m%d_%H%M%S).dump` then tars `/var/lib/einvoice/artifacts` alongside; rotates to `$BACKUP_RETENTION_DAYS` (default 30).
- [ ] T075 [P] [US4] Create `deploy/scripts/restore.sh` — refuses to run against a non-empty DB unless `--force`; runs `pg_restore --clean --if-exists`; unpacks artifact tarball to `/var/lib/einvoice/artifacts`; restarts backend.
- [ ] T076 [P] [US4] Create `deploy/scripts/upgrade.sh` — reads current `deploy/VERSION`, compares to `--tag`; if equal, logs "already at $TAG" and exits 0; else pulls images, runs `docker compose run --rm backend java -jar app.jar --spring.flyway.migrate-on-startup=true --flyway-only=true` equivalent migration step, then `docker compose up -d`; on failure, leaves old containers running.
- [ ] T077 [P] [US4] Create PowerShell parity scripts `deploy/scripts/backup.ps1`, `deploy/scripts/restore.ps1`, `deploy/scripts/upgrade.ps1`.

### Env + secrets templates

- [ ] T078 [P] [US4] Create `deploy/env/.env.example` documenting every required variable: `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`, `EINVOICE_ENCRYPTION_KEY`, `EINVOICE_JWT_SECRET`, `SPRING_PROFILES_ACTIVE=production`, `EINVOICE_ARTIFACTS_ROOT=/var/lib/einvoice/artifacts`, `EINVOICE_PUBLIC_URL`, `BACKUP_RETENTION_DAYS=30`, `EINVOICE_TLS_CERT_PATH`, `EINVOICE_TLS_KEY_PATH`.
- [ ] T079 [P] [US4] Create `deploy/env/secrets.example` illustrating the structure for Docker secrets (`/run/secrets/einvoice_encryption_key`, etc.) for customers that prefer mounted files.

### Documentation

- [ ] T080 [P] [US4] Write `Docs/deployment-guide.md` — prerequisites, step-by-step install, TLS cert sourcing, environment variables, upgrade procedure, rollback procedure.
- [ ] T081 [P] [US4] Write `Docs/backup-recovery.md` — backup frequency recommendation, restore drill procedure, verification checklist, off-host archival guidance.
- [ ] T082 [P] [US4] Write `Docs/configuration-reference.md` — exhaustive env variable reference table (name, purpose, default, example, security sensitivity).

### Admin integration

- [ ] T083 [US4] Add `POST /api/admin/deployment/upgrade-applied` endpoint in `platform-api/src/main/java/com/einvoice/api/admin/DeploymentController.java` — accepts a small JSON body (`{from, to, appliedAt}`), records a `DEPLOYMENT_UPGRADE_APPLIED` audit event, requires SUPER_ADMIN. Hook this endpoint from `upgrade.sh` after a successful upgrade.

### Validation drill

- [ ] T084 [US4] Run quickstart.md §4 against a clean Linux VM. Capture timings for install-to-login and record them in `specs/004-bulk-ops-dashboard-deployment/.run-log`.

**Checkpoint**: The platform is shippable on-prem.

---

## Phase 7: User Story 5 — ETA PDF + ETA bulk package + conditional ZATCA PDF (Priority: P3)

**Goal**: Download the ETA-issued PDF for a single cleared invoice (cached after first fetch), request and download an ETA bulk document package for a date range (background-polled), and — only if the Wave 2 decision required it — download a bilingual ZATCA customer PDF with an embedded QR.

**Independent Test**: Cleared ETA invoice → click "Download PDF" → authority hit once, second click served from cache. Bulk package request for >100 documents → status transitions REQUESTED→PREPARING→READY → download archive. For ZATCA (conditional): verify PDF bilingual content and QR decode match signed XML.

### Tests for User Story 5

- [ ] T085 [P] [US5] Write `EtaPdfProxyIntegrationTest` — first call fetches from ETA mock, persists `invoice_artifacts` row of type `ETA_CUSTOMER_PDF`; second call serves cache (authority mock assertion: called exactly once).
- [ ] T086 [P] [US5] Write `EtaBulkPackageLifecycleTest` using WireMock for ETA — request created → poller transitions REQUESTED→PREPARING→READY → downloadable row with persisted artifact; separate test for cancel path transitioning to ABANDONED.
- [ ] T087 [P] [US5] Write `EtaBulkPackagesContractTest` against `contracts/eta-bulk-packages.yaml`.
- [ ] T088 [P] [US5] (Conditional) Write `ZatcaInvoicePdfGoldenTest` — given a canonical cleared-invoice fixture, render the PDF and assert: Arabic and English strings present in extracted text, QR decodes to the expected TLV, metadata stamps match. Skip test registration entirely if `zatca.pdf.enabled=false`.

### Backend implementation

- [ ] T089 [P] [US5] Create DTOs `CreatePackageRequest`, `PackageRequestDto`, `PackageRequestPage` in `platform-api/src/main/java/com/einvoice/api/etapkg/dto/`.
- [ ] T090 [US5] Extend existing `EtaClient` (Wave 2) with `POST /documentpackages`, `GET /documentpackages`, `GET /documentpackages/{id}` in `platform-eta/src/main/java/com/einvoice/eta/client/EtaClient.java`.
- [ ] T091 [US5] Implement `platform-jobs/src/main/java/com/einvoice/jobs/service/EtaBulkPackagePoller.java` — `@Scheduled` + `@SchedulerLock` at configured interval; for every non-terminal `eta_bulk_package_requests` row polls `GET /documentpackages/{id}`; transitions status; on READY, downloads the archive bytes and persists as an `invoice_artifacts` row of type `ETA_BULK_PACKAGE`.
- [ ] T092 [US5] Implement `platform-api/src/main/java/com/einvoice/api/etapkg/EtaBulkPackageController.java` — all four endpoints per `eta-bulk-packages.yaml`; cancel records `ETA_BULK_PACKAGE_ABANDONED`; download records `ETA_BULK_PACKAGE_DOWNLOADED`.
- [ ] T093 [US5] Implement `platform-api/src/main/java/com/einvoice/api/artifact/InvoiceArtifactController.java` `GET /api/invoices/{id}/eta-pdf` — cache-first lookup against `invoice_artifacts`; on miss, fetch `GET /documents/{documentId}/pdf` via the existing ETA client, persist as immutable artifact, return bytes.
- [ ] T094 [US5] (Conditional) Implement `platform-pdf/src/main/java/com/einvoice/pdf/ZatcaInvoicePdfRenderer.java` + Thymeleaf template `platform-pdf/src/main/resources/templates/zatca-invoice.html` (bilingual AR/EN, RTL for Arabic block, QR `<img>` from the ZXing-rendered PNG artifact). Use OpenHTMLToPDF with PdfBoxRenderer.
- [ ] T095 [US5] (Conditional) Implement `platform-pdf/src/main/java/com/einvoice/pdf/service/ZatcaPdfService.java` — generates PDF, persists as `invoice_artifacts` row of type `ZATCA_CUSTOMER_PDF` with content hash; idempotent (repeat calls return existing artifact).
- [ ] T096 [US5] (Conditional) Extend `InvoiceArtifactController` with `GET /api/invoices/{id}/zatca-pdf` — returns the persisted PDF; 404 if the feature flag is off.

### Frontend implementation

- [ ] T097 [P] [US5] Create `frontend/src/app/eta/eta-bulk-packages/eta-bulk-packages.routes.ts` and register `/eta-packages` under the Config module.
- [ ] T098 [US5] Create `frontend/src/app/eta/eta-bulk-packages/eta-bulk-packages-list/eta-bulk-packages-list.component.ts` — list with status chips, polls every 30 s for in-flight rows, cancel + download actions.
- [ ] T099 [US5] Create `frontend/src/app/eta/eta-bulk-packages/request-package-dialog/request-package-dialog.component.ts` — Reactive Form (branch, environment, date range, direction, optional document types), posts to `/api/eta-bulk-packages`.
- [ ] T100 [US5] Extend `frontend/src/app/invoices/invoice-detail/invoice-detail.component.ts` to add the ETA PDF download action (use `blob-download`) and — conditionally via a frontend feature flag toggled by a new `/api/config/features` response — the ZATCA PDF download action.

**Checkpoint**: Archival/artifact story complete; platform now emits every required customer-facing document.

---

## Phase 8: User Story 6 — Filtered invoice spreadsheet export (Priority: P3)

**Goal**: From the invoice list, apply filters → click Export → streamed XLSX with exactly the on-screen rows, up to 10,000 rows in under a minute.

**Independent Test**: Filter list to "this month, status=Accepted, authority=ETA", click Export, open the file; row count and column values match the list exactly.

### Tests for User Story 6

- [ ] T101 [P] [US6] Write `InvoiceExportParityTest` — for a fixed filter set, compare `GET /api/invoices/list` rows to the spreadsheet export's sheet contents; must match on invoice number, status, authority, totals, issueDate.
- [ ] T102 [P] [US6] Write `InvoiceExportScaleTest` — seed 10,000 invoices; assert export completes under 60 s and heap does not exceed 200 MB (measure via `MemoryMXBean`).
- [ ] T103 [P] [US6] Write `InvoiceExportContractTest` validating `contracts/invoice-export.yaml` (headers, content type).

### Backend implementation

- [ ] T104 [US6] Implement `platform-api/src/main/java/com/einvoice/api/export/InvoiceExportController.java` — accepts the same filter parameters as the existing invoice list endpoint, streams an `SXSSFWorkbook` through `ResponseBodyEmitter`; sets `Content-Disposition: attachment; filename=invoices-YYYYMMDD-HHMMSS.xlsx`; records `INVOICE_EXPORT_REQUESTED` audit entry.
- [ ] T105 [US6] Refactor the shared filter predicate used by `InvoiceListService` and `InvoiceExportController` into `platform-api/src/main/java/com/einvoice/api/invoice/InvoiceFilterSpecs.java` (JPA `Specification`) so the two paths cannot drift.

### Frontend implementation

- [ ] T106 [P] [US6] Extend `frontend/src/app/invoices/services/invoices.service.ts` with `exportFiltered(filters): Observable<Blob>` that builds the query string from the current filter form and invokes `blob-download`.
- [ ] T107 [US6] Add an "Export" button to `frontend/src/app/invoices/invoices-list/invoices-list.component.ts` next to the existing filter bar; disabled while a previous export is in flight; shows a progress snack.

**Checkpoint**: All six user stories independently functional.

---

## Phase 9: Polish & Cross-Cutting Concerns

**Purpose**: Close the last spec requirements (FR-035–FR-039, SC-012, SC-013), run quickstart, and harden the wave.

- [ ] T108 [P] Angular pass: ensure every Wave 3 list (Jobs, Logs/Audit, Logs/Submission, ETA Packages) renders an explicit empty state (FR-036) and every async action shows a loading indicator (FR-035). Record any exceptions inline in `frontend/src/app/shared/ux-audit.md`.
- [ ] T109 [P] Angular pass: global error interceptor in `frontend/src/app/shared/http/error.interceptor.ts` translates backend errors + network failures into user-friendly messages without exposing stack traces (FR-037). Unit-test three error shapes.
- [ ] T110 [P] Angular pass: add a reusable `InFlightDirective` at `frontend/src/app/shared/directives/in-flight.directive.ts` that disables buttons while a passed `Observable` is in flight (FR-038); apply to primary actions added in T039, T040, T041, T053, T098, T107.
- [ ] T111 [P] Responsive layout audit against tablet (1024×768) and desktop (1440×900) for every Wave 3 screen; log any overflow issues and fix in the same pass.
- [ ] T112 [P] Security pass: OWASP top-10 review checklist against Wave 3 endpoints (focus: SSE endpoint doesn't leak tokens in referrer, log explorer parameters are bound via prepared statements, artifact download enforces tenant check). Record findings in `specs/004-bulk-ops-dashboard-deployment/.security-review.md`.
- [ ] T113 Cross-story tenant-isolation integration test `WaveThreeTenantIsolationTest` — two tenants, each exercises jobs + logs + dashboard + exports + bulk packages; assert zero cross-tenant rows leak in every response (SC-013).
- [ ] T114 Run the full `quickstart.md` end-to-end on the dev stack; capture evidence (screenshots or command transcripts) into `specs/004-bulk-ops-dashboard-deployment/.acceptance-evidence/`.
- [ ] T115 Update `Docs/implementation-plan.md` Wave 3 section cross-references to point at `specs/004-bulk-ops-dashboard-deployment/` (plan, research, tasks) so future waves can find the artifacts.

---

## Dependencies & Execution Order

### Phase dependencies

- **Phase 1 (Setup)**: no dependencies.
- **Phase 2 (Foundational)**: after Phase 1; blocks every user story.
- **Phase 3 (US1)**: requires Phase 2.
- **Phase 4 (US2)**: requires Phase 2; independent of US1 (can run in parallel).
- **Phase 5 (US3)**: requires Phase 2; independent of US1/US2.
- **Phase 6 (US4)**: requires Phase 2 artifacts compilable and container-buildable; logically independent of US1–US3 but strongly recommended after US1 so the install demo has bulk submission available.
- **Phase 7 (US5)**: requires Phase 2; minor integration point with US1's artifact handling but otherwise independent.
- **Phase 8 (US6)**: requires Phase 2; no dependency on US1–US5.
- **Phase 9 (Polish)**: after all targeted user stories.

### Within each user story

- Tests alongside or just after the component they validate (tests are included but not strict TDD-first).
- Models/DTOs before services; services before controllers; controllers before frontend bindings.
- Frontend feature module scaffolding (`*.routes.ts`) before components.

### Parallel opportunities

- All `[P]` Setup tasks (T001–T003, T005) run together.
- Foundational model/repo tasks (T011–T015) parallelize; scheduler config (T016–T017) parallelizes with them; frontend shared pieces (T021–T024) parallelize with backend.
- Across user stories: a team of 3 can take US1, US2+US3 (same engineer, adjacent read-only surfaces), and US4 in parallel once Phase 2 is done.
- Within each story: DTOs and tests marked `[P]` parallelize; services/controllers serialize on their dependencies.

---

## Parallel Example: User Story 1

```bash
# Kick off tests and DTOs in parallel:
Task: "Write JobStateMachineTest (T025)"
Task: "Write BulkOrchestrationIntegrationTest (T026)"
Task: "Write JobRestartReconciliationIntegrationTest (T027)"
Task: "Write BulkSubmissionContractTest + JobsContractTest (T028)"
Task: "Write JobsTenantIsolationTest (T029)"
Task: "Create bulk + jobs DTOs (T030)"

# Once DTOs exist, backend services run sequentially (T031 → T032 → T033 → T034 → T035 → T036)
# while frontend scaffolding tasks parallelize:
Task: "Create jobs feature module (T037)"
Task: "Create JobsService (T038)"
Task: "Create bulk-submit-dialog (T041)"
```

---

## Implementation Strategy

### MVP (User Story 1 only)

1. Phase 1 Setup (T001–T005).
2. Phase 2 Foundational (T006–T024).
3. Phase 3 US1 (T025–T044).
4. **STOP**: demo bulk submission + jobs monitoring. This is a shippable MVP increment on top of Waves 1+2.

### Incremental delivery after MVP

- Add US2 (Dashboard) → demo.
- Add US3 (Logs) → demo.
- Add US4 (Deployment) → ship on-prem to the first pilot customer.
- Add US5 (Artifacts) and US6 (Export) in the next sprint.
- Polish phase (T108–T115) runs as the wave closes out.

### Parallel team strategy

With 3 engineers after Phase 2:

- Eng A: US1 (bulk + jobs).
- Eng B: US2 + US3 (read-only dashboard/logs share the filter/pagination patterns).
- Eng C: US4 (deployment) while the other two build; swings into US5/US6 once Compose is stable.

---

## Notes

- `[P]` = different files, no dependency on an incomplete task.
- `[US#]` label binds a task to a spec user story for traceability.
- Conditional tasks (T088, T094, T095, T096) activate only if the Wave 2 ZATCA PDF decision is "generate"; otherwise they are skipped and `V22` drops the `ZATCA_CUSTOMER_PDF` enum value.
- Every new endpoint is tenant-scoped (Constitution II/III); tenant-isolation coverage is enforced both per-story and again in T113.
- Commit after each task or each checkpoint; never amend a completed task's commit.
