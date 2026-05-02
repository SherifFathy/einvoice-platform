# Phase 1 Data Model: Bulk Operations, Dashboard, Logs & Deployment

**Feature**: 004-bulk-ops-dashboard-deployment
**Date**: 2026-04-19
**Migration range**: `V20__` through `V23__` (Wave 2 ended at `V19`).

Entities introduced by this wave. Pre-existing entities (`invoices`, `submission_attempts`, `invoice_artifacts`, `authority_configs`, `audit_logs`, `companies`, `branches`, `users`) are reused unmodified, with the noted index additions.

---

## Entity: `jobs` (new)

Represents a bulk operation — today always bulk invoice submission, kept generic so future bulk actions (bulk cancel, bulk export) can reuse it.

| Column | Type | Notes |
|---|---|---|
| `id` | `UUID PRIMARY KEY` | generated application-side |
| `company_id` | `UUID NOT NULL REFERENCES companies(id)` | tenant ownership (Constitution II) |
| `type` | `job_type` enum | currently `BULK_SUBMISSION`; extensible |
| `status` | `job_status` enum | `QUEUED`, `RUNNING`, `COMPLETED`, `FAILED`, `CANCELLED` |
| `total_count` | `INT NOT NULL` | set at enqueue time |
| `success_count` | `INT NOT NULL DEFAULT 0` | updated transactionally per item |
| `failed_count` | `INT NOT NULL DEFAULT 0` | updated transactionally per item |
| `skipped_count` | `INT NOT NULL DEFAULT 0` | invoices already in terminal accepted state (FR-009) |
| `created_by` | `UUID NOT NULL REFERENCES users(id)` | originator |
| `cancelled_by` | `UUID REFERENCES users(id)` | nullable |
| `cancellation_reason` | `TEXT` | nullable, free text |
| `created_at` | `TIMESTAMPTZ NOT NULL DEFAULT NOW()` | |
| `started_at` | `TIMESTAMPTZ` | set when poller picks it up |
| `completed_at` | `TIMESTAMPTZ` | set on terminal transition |
| `poller_node` | `TEXT` | the ShedLock holder that currently owns it, nullable |

**State transitions**:
- `QUEUED → RUNNING`: poller claims the job
- `RUNNING → COMPLETED`: all items processed
- `RUNNING → FAILED`: all items ended in non-retryable failure, or an unrecoverable orchestration error
- `RUNNING|QUEUED → CANCELLED`: user-initiated
- Re-entry into `RUNNING` from `QUEUED` is allowed after an application restart (auto-resume — clarified 2026-04-19)

**Indexes**:
- `PRIMARY KEY (id)`
- `INDEX jobs_company_status_created_at (company_id, status, created_at DESC)` — drives the Jobs list
- `INDEX jobs_status_created_at (status, created_at)` — drives the poller's fetch

**Validation rules**:
- `success_count + failed_count + skipped_count ≤ total_count` enforced via CHECK constraint
- `completed_at IS NOT NULL` required whenever `status IN ('COMPLETED','FAILED','CANCELLED')`

---

## Entity: `job_items` (new)

One row per invoice in a bulk job. Records outcome, error, and timing.

| Column | Type | Notes |
|---|---|---|
| `id` | `UUID PRIMARY KEY` | |
| `job_id` | `UUID NOT NULL REFERENCES jobs(id) ON DELETE CASCADE` | |
| `invoice_id` | `UUID NOT NULL REFERENCES invoices(id)` | |
| `sequence_index` | `INT NOT NULL` | order within the job, 0-based |
| `status` | `job_item_status` enum | `PENDING`, `IN_PROGRESS`, `SUCCEEDED`, `FAILED`, `SKIPPED`, `CANCELLED`, `NEEDS_RECONCILIATION` |
| `submission_attempt_id` | `UUID REFERENCES submission_attempts(id)` | set on SUCCEEDED or FAILED; `NULL` for SKIPPED/CANCELLED |
| `error_summary` | `TEXT` | short human-readable reason on FAILED |
| `processed_at` | `TIMESTAMPTZ` | |

**State transitions**:
- `PENDING → IN_PROGRESS`: poller picks up this item
- `IN_PROGRESS → SUCCEEDED | FAILED`: orchestrator reports result
- `PENDING | IN_PROGRESS → SKIPPED`: detected already-terminal invoice (FR-009)
- `PENDING | IN_PROGRESS → CANCELLED`: parent job cancelled
- `IN_PROGRESS → NEEDS_RECONCILIATION`: application restarted mid-submission; `JobReconciliationService` transitions it to SUCCEEDED/FAILED/pending-retry after checking authority state (clarified 2026-04-19)

**Indexes**:
- `UNIQUE (job_id, sequence_index)` — stable ordering and idempotent resume
- `INDEX job_items_job_status (job_id, status)` — detail view filtering and poller's "next item" lookup
- `INDEX job_items_invoice (invoice_id)` — used to prevent double-enqueue of the same invoice into concurrent jobs

**Validation rules**:
- `status = 'SUCCEEDED'` implies `submission_attempt_id IS NOT NULL`
- `sequence_index >= 0` CHECK
- Uniqueness of `(job_id, invoice_id)` enforced at enqueue time (application-side) to prevent accidental duplicates

---

## Entity: `eta_bulk_package_requests` (new)

Tracks an ETA bulk-document-package request from creation through download.

| Column | Type | Notes |
|---|---|---|
| `id` | `UUID PRIMARY KEY` | |
| `company_id` | `UUID NOT NULL REFERENCES companies(id)` | |
| `branch_id` | `UUID NOT NULL REFERENCES branches(id)` | |
| `environment` | `environment` enum | ETA_PREPRODUCTION or ETA_PRODUCTION |
| `requested_by` | `UUID NOT NULL REFERENCES users(id)` | |
| `criteria_json` | `JSONB NOT NULL` | date range, document types, filter criteria |
| `eta_package_id` | `TEXT` | authority-assigned id after initial POST |
| `status` | `eta_package_status` enum | `REQUESTED`, `PREPARING`, `READY`, `FAILED`, `ABANDONED` |
| `download_url_ref` | `TEXT` | opaque internal reference once downloaded and stored |
| `artifact_id` | `UUID REFERENCES invoice_artifacts(id)` | nullable; set when package bytes are persisted |
| `error_message` | `TEXT` | populated on FAILED |
| `created_at` | `TIMESTAMPTZ NOT NULL DEFAULT NOW()` | |
| `updated_at` | `TIMESTAMPTZ NOT NULL DEFAULT NOW()` | poller-updated |
| `ready_at` | `TIMESTAMPTZ` | set when status becomes READY |

**State transitions**:
- `REQUESTED → PREPARING`: first poll returned "not ready yet"
- `REQUESTED | PREPARING → READY`: poll returned downloadable package; bytes persisted to `invoice_artifacts` (type `ETA_BULK_PACKAGE`)
- `REQUESTED | PREPARING → FAILED`: authority returned error
- `REQUESTED | PREPARING → ABANDONED`: user cancelled before the package was ready

**Indexes**:
- `INDEX eta_pkg_requests_status_updated (status, updated_at)` — drives the poller
- `INDEX eta_pkg_requests_company_created (company_id, created_at DESC)` — list view

---

## Entity: `invoice_artifacts` (existing — enum extension)

No schema change beyond the `artifact_type` enum. Two values added in `V22__extend_artifact_types.sql`:

- `ETA_CUSTOMER_PDF` — cached proxy of `GET /documents/{id}/pdf`
- `ETA_BULK_PACKAGE` — archived bytes of a ready ETA bulk package
- `ZATCA_CUSTOMER_PDF` — **only added if the Wave 2 PDF decision is "generate"**; otherwise this value is omitted and the migration skipped

Immutability (no UPDATE) remains enforced by the existing `V14` migration's trigger.

---

## Entity: `audit_logs` (existing — new event types)

No schema change. FR-041 requires new `action` values to be recorded when Wave 3 features are exercised:

- `BULK_JOB_CREATED`, `BULK_JOB_CANCELLED`, `BULK_JOB_REPORT_DOWNLOADED`
- `ETA_BULK_PACKAGE_REQUESTED`, `ETA_BULK_PACKAGE_DOWNLOADED`, `ETA_BULK_PACKAGE_ABANDONED`
- `INVOICE_EXPORT_REQUESTED`
- `DEPLOYMENT_UPGRADE_APPLIED` (recorded by the upgrade script calling a dedicated admin endpoint that stamps the audit log)

---

## Index additions on existing tables (`V20__`)

Introduced to meet the p95 < 2 s dashboard target (SC-004) and keep log-explorer pagination flat-latency:

- `INDEX invoices_company_authority_issue_date ON invoices (company_id, authority, issue_date DESC)`
- `INDEX submission_attempts_company_completed_at ON submission_attempts (company_id, completed_at DESC)`
- `INDEX authority_configs_expiry ON authority_configs (branch_id, certificate_expiry_date) WHERE certificate_expiry_date IS NOT NULL`
- `INDEX audit_logs_cursor ON audit_logs (company_id, timestamp DESC, id DESC)` — cursor pagination
- `INDEX submission_attempts_cursor ON submission_attempts (company_id, completed_at DESC, id DESC)` — cursor pagination for the submission log explorer

---

## Derived views (no DDL — application-layer projections)

These are not persisted tables; they are on-demand aggregations served by `DashboardService`:

- **`DashboardKpiSnapshot`** — fields: `invoicesToday`, `invoicesMonth`, grouped by `status` and `authority`; computed with two aggregation queries against `invoices` using the indexes above.
- **`RecentActivityItem`** — last 10 rows from `submission_attempts` joined to `invoices` for display.
- **`AlertItem`** — derived from three queries: failed-retryable and failed-non-retryable counts from `invoices`; `authority_configs` rows with expiry within 30 days (clarified window); `authority_configs` rows with `is_active = false` or missing credentials.

Each projection is assembled in a single service call and returned as the dashboard's composite response.

---

## Data volume & retention

- **Indefinite retention** (clarified 2026-04-19): `audit_logs`, `submission_attempts`, `invoice_artifacts`, `jobs`, `job_items`, `eta_bulk_package_requests` all accumulate without automatic purge. Deployment documentation (Phase 1 `quickstart.md`) describes storage-growth planning; no MVP purge job is introduced.
- **Hot rows**: `jobs` and `job_items` for in-flight work — expected peak ~50 jobs × 10,000 items = 500,000 rows concurrently; safely handled by the indexes above.
- **Export**: spreadsheet invoice export is a streamed workbook with no persistent row.

---

## Migration order (for `/speckit.tasks`)

1. `V20__create_jobs_and_job_items.sql` — tables, enums, indexes, CHECK constraints.
2. `V21__create_eta_bulk_package_requests.sql` — table, enums, indexes.
3. `V22__extend_artifact_types.sql` — `ETA_CUSTOMER_PDF`, `ETA_BULK_PACKAGE`, and conditionally `ZATCA_CUSTOMER_PDF`.
4. `V23__add_index_tuning_for_wave3.sql` — index additions on existing tables.

Each migration is additive and reversible via drop; no existing column is mutated.
