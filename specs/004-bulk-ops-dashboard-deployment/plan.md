# Implementation Plan: Bulk Operations, Dashboard, Logs & Deployment

**Branch**: `004-bulk-ops-dashboard-deployment` | **Date**: 2026-04-19 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `specs/004-bulk-ops-dashboard-deployment/spec.md`

## Summary

Wave 3 turns the per-invoice platform delivered in Wave 2 into an operations-grade product. It adds two submission modes (foreground streaming + background async), a Jobs monitor, an operational dashboard with KPIs and alerts, read-only audit and submission log explorers, ETA bulk packages, conditional ZATCA PDF generation, a spreadsheet invoice export, and a Docker-based on-prem deployment with install/backup/upgrade scripts. The technical approach reuses the `SubmissionOrchestrator`, `AuthorityEngine`, and immutable artifact store already built; it introduces a crash-safe job queue (DB-persisted `jobs` + `job_items` with a `ShedLock`-coordinated poller) that serializes ZATCA work per `(branch, environment)` via an existing PostgreSQL advisory lock and parallelizes ETA work across branches. All new surfaces are additive, tenant-scoped, and never expose a mutation path into audit or submission logs.

## Technical Context

**Language/Version**: Java 17 (backend), TypeScript 5.x / Angular 19 (frontend)
**Primary Dependencies**: Spring Boot 3.4.4 (web, data-jpa, security, validation, scheduling, async), Spring Data JPA + Flyway (PostgreSQL 16), Apache POI 5.x (spreadsheet export/import), Jackson, Lombok, BouncyCastle (already present), ShedLock 5.x for distributed-safe schedulers, OpenHTMLToPDF 1.x (used only if the Wave 2 ZATCA-PDF spike requires platform-generated PDFs), Angular Material, RxJS
**Storage**: PostgreSQL 16 (primary), filesystem volume under `/var/lib/einvoice/artifacts` mounted into the backend container for artifact and PDF retention, filesystem volume for backups
**Testing**: JUnit 5 + Spring Boot Test + Testcontainers (PostgreSQL, wiremock), Karma/Jasmine for Angular unit tests, Playwright or Cypress for the end-to-end bulk/jobs/dashboard smoke (optional), golden-file tests (carried forward from Wave 2) remain untouched
**Target Platform**: On-premise Linux server (kernel 5.x+, systemd, container runtime Docker 24+ or Podman 4+); deployment packaged via Docker Compose
**Project Type**: Web application — Maven multi-module backend (`platform-core`, `platform-security`, `platform-api`, `platform-zatca`, `platform-eta`, `platform-pdf`, `platform-jobs`) + Angular workspace
**Performance Goals**: Dashboard load under 2 s at p95 with up to 100,000 invoices in the active company (SC-004); foreground bulk submission streams per-invoice progress in near-real-time; background bulk of 10,000 invoices completes without degrading concurrent single-invoice submissions; spreadsheet export of 10,000 rows under 60 s (SC-011); single-invoice submission remains under 5 s per Constitution XVII
**Constraints**: Strict ZATCA hash-chain sequentiality per `(branch, environment)` under any concurrent job load; indefinite log retention with append-only guarantees (FR-022); HTTPS mandatory at perimeter; all secrets from env/mounted secret store (FR-030); no cloud-managed dependency; polling-only for ETA bulk packages (no webhooks)
**Scale/Scope**: Up to 100,000 invoices per company, up to 10,000 invoices per bulk job, up to ~50 concurrent bulk jobs system-wide; 13 success criteria, 41+ functional requirements, 6 prioritized user stories

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

Evaluated against Constitution v1.0.0 (2026-04-07). All 18 principles pass; no violations requiring Complexity Tracking.

| # | Principle | Status | How Wave 3 Honors It |
|---|-----------|--------|----------------------|
| I | Compliance-First | PASS | Bulk path reuses Wave 2 `SubmissionOrchestrator` and `ValidationService`; no new code bypasses authority validations. |
| II | Multi-Tenant Isolation | PASS | `jobs`, `job_items`, `eta_bulk_package_requests` all carry `company_id`; every new endpoint resolves tenant via existing `TenantContext`; log explorer queries are tenant-scoped at the repository layer. |
| III | Branch + Environment Segregation | PASS | Each job item inherits the invoice's `(branch, authority, environment)` tuple and the orchestrator validates it before submission. ZATCA serialization key is `(branch_id, environment)`. |
| IV | Stateless Service | PASS | Job state lives in `jobs`/`job_items`; the poller reads and writes state each cycle; no in-memory tenant caches added. |
| V | Server-Side Cryptography | PASS | No cryptographic material crosses into Angular; PDF generation (if needed) reads already-signed XML server-side. |
| VI | Immutable Audit | PASS | FR-041 mandates audit entries for every new user-initiated action; no UI or API path edits/deletes audit entries (FR-022). |
| VII | Deterministic Invoice Lifecycle | PASS | Bulk uses existing state machine; no new transitions. Restart reconciliation transitions ambiguous invoices into `SUBMISSION_AMBIGUOUS`, not into silent success. |
| VIII | Validation Layering | PASS | Bulk submit rejects any invoice not already in `READY_FOR_SUBMISSION`; backend remains the source of truth; spreadsheet import path is unchanged. |
| IX | Angular Engineering | PASS | Jobs, Dashboard, Logs feature modules use Reactive Forms for filters; no authority logic leaks into the frontend. |
| X | Spring Boot Engineering | PASS | `platform-jobs` gets controller → application service → domain service → repository layers; Flyway `V20+` migrations mandatory. |
| XI | Authority Adapter | PASS | Bulk orchestrator calls the existing `AuthorityEngine`; no authority-specific branching is added outside engines. |
| XII | Secure On-Prem Deployment | PASS | Docker Compose + install/backup/upgrade scripts are the deliverable; HTTPS enforced via Nginx perimeter; app and DB are separate containers with a dedicated volume for each. |
| XIII | Reliability and Recovery | PASS | Job auto-resume after restart (FR-010), ShedLock prevents duplicate pollers, mid-submission reconciliation against authority state, ambiguous-status fallback preserved. |
| XIV | Document Preservation | PASS | ETA authority PDFs are cached in `invoice_artifacts`; conditional ZATCA PDFs also persisted as immutable artifacts; regeneration never overwrites originals. |
| XV | Change Control | PASS | No invoice-numbering or signing rules change in this wave; new lifecycle behavior for jobs is captured in plan and tasks. |
| XVI | Testing | PASS | New tests: job-state-machine unit tests, crash/restart integration test, tenant-isolation tests on jobs/logs/dashboard, golden-file tests on export columns, contract tests on new REST endpoints. |
| XVII | Performance | PASS | Dashboard SC-004 quantified (p95 < 2 s); bulk submission is asynchronous by default; indexes defined in Phase 1 data model. |
| XVIII | Source of Truth | PASS | Plan sequences the spec's requirements without contradiction; any discrepancy surfaced in review resolves in favor of the spec. |

Re-check after Phase 1 design: see `## Post-Design Constitution Re-Check` at the bottom of this file.

## Project Structure

### Documentation (this feature)

```text
specs/004-bulk-ops-dashboard-deployment/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/           # Phase 1 output
│   ├── bulk-submission.yaml
│   ├── jobs.yaml
│   ├── dashboard.yaml
│   ├── logs.yaml
│   ├── eta-bulk-packages.yaml
│   ├── invoice-artifacts.yaml
│   └── invoice-export.yaml
├── checklists/
│   └── requirements.md  # Generated by /speckit.specify
└── tasks.md             # Generated by /speckit.tasks (not by this command)
```

### Source Code (repository root)

The repository already uses a Maven multi-module backend plus an Angular workspace. Wave 3 adds new Flyway migrations, fills the empty `platform-jobs` and (conditionally) `platform-pdf` modules, extends `platform-api` with new controllers, and adds Angular feature modules for Jobs, Dashboard enrichment, and Logs. Deployment scripts live at the repo root under a new `deploy/` directory.

```text
platform-core/
└── src/main/resources/db/migration/
    ├── V20__create_jobs_and_job_items.sql
    ├── V21__create_eta_bulk_package_requests.sql
    ├── V22__add_zatca_pdf_artifact_type.sql            # only if ZATCA PDF decision = generate
    └── V23__add_job_audit_triggers.sql

platform-jobs/
└── src/main/java/com/einvoice/jobs/
    ├── domain/                                          # Job, JobItem, JobType, JobStatus, JobItemStatus
    ├── service/
    │   ├── BulkSubmissionService.java                   # foreground streaming + background enqueue
    │   ├── JobPoller.java                               # @Scheduled, ShedLock, auto-resume
    │   ├── JobReconciliationService.java                # on-startup mid-submission reconcile
    │   ├── JobReportService.java                        # produces spreadsheet report of a job
    │   └── EtaBulkPackagePoller.java                    # polls ETA /documentpackages readiness
    ├── repository/
    │   ├── JobRepository.java
    │   ├── JobItemRepository.java
    │   └── EtaBulkPackageRequestRepository.java
    └── config/
        ├── JobsSchedulingConfig.java                    # ShedLock, TaskExecutor, cron
        └── JobsProperties.java                          # poll interval, parallelism, thresholds

platform-api/
└── src/main/java/com/einvoice/api/
    ├── bulk/BulkSubmissionController.java               # POST /api/invoices/bulk-submit, /api/jobs/bulk-submit
    ├── jobs/JobController.java                          # GET list, GET detail, POST cancel, GET report
    ├── dashboard/
    │   ├── DashboardController.java                     # GET /api/dashboard/{kpis, activity, alerts}
    │   └── DashboardService.java                        # aggregation queries, alert derivation
    ├── logs/
    │   ├── AuditLogController.java                      # GET /api/audit-logs (read-only)
    │   └── SubmissionLogController.java                 # GET /api/submission-logs (read-only)
    ├── export/InvoiceExportController.java              # GET /api/invoices/export (streaming xlsx)
    ├── etapkg/EtaBulkPackageController.java             # POST/GET /api/eta-bulk-packages
    ├── artifact/InvoiceArtifactController.java          # GET /api/invoices/{id}/{eta-pdf,zatca-pdf,xml,json}
    └── config/WaveThreeSecurityConfig.java              # method security + tenant checks on new endpoints

platform-pdf/                                             # conditional: only if Wave 2 spike says "generate PDF"
└── src/main/java/com/einvoice/pdf/
    ├── ZatcaInvoicePdfRenderer.java                     # OpenHTMLToPDF; uses bilingual Thymeleaf template
    ├── templates/zatca-invoice.html                     # AR/EN layout with QR image placeholder
    └── service/ZatcaPdfService.java                     # persists pdf as invoice_artifact (immutable)

frontend/src/app/
├── jobs/                                                # Jobs list, Job detail, cancel, report download
├── dashboard/                                           # KPI cards, activity feed, alerts, quick actions, super-admin view
├── logs/                                                # Audit log explorer, Submission log explorer (both read-only)
├── invoices/
│   ├── components/
│   │   ├── bulk-submit-dialog/                          # explicit foreground/background choice
│   │   └── bulk-progress-modal/                         # streaming SSE progress for foreground mode
│   └── services/invoice-export.service.ts
├── eta/eta-bulk-packages/                               # request + list + download
└── shared/
    └── alerts/alert-card.component.ts                   # shared alert rendering on dashboard

deploy/                                                   # NEW directory
├── docker/
│   ├── Dockerfile.backend                               # multi-stage Maven -> JRE image
│   ├── Dockerfile.frontend                              # Node build -> Nginx serve
│   └── nginx/
│       ├── nginx.conf                                   # reverse proxy, TLS termination, security headers
│       └── tls/                                         # mount point for operator-provided certs
├── docker-compose.yml                                   # dev (existing) kept; profile-aware
├── docker-compose.prod.yml                              # production profile, HTTPS, separate db container
├── scripts/
│   ├── install.sh / install.ps1
│   ├── backup.sh / backup.ps1
│   ├── restore.sh / restore.ps1
│   └── upgrade.sh / upgrade.ps1
└── env/
    ├── .env.example                                     # every required variable documented
    └── secrets.example                                  # structure for mounted secrets

Docs/
├── deployment-guide.md                                  # NEW: step-by-step on-prem install
├── backup-recovery.md                                   # NEW: backup schedule, restore drill
└── configuration-reference.md                           # NEW: env vars, volumes, ports, profiles
```

**Structure Decision**: The existing web-application layout is kept. Wave 3 occupies the currently-empty `platform-jobs` module (bulk orchestration + pollers), conditionally activates `platform-pdf`, and extends `platform-api` with read-only explorers and one streaming export endpoint. On the frontend, three new feature modules (`jobs/`, `logs/`, enhanced `dashboard/`) plus shared bulk-submit dialog components are added under `frontend/src/app/`. All on-prem deployment assets are gathered in a new top-level `deploy/` directory so the repository root stays uncluttered and the install script can operate on a single known path.

## Post-Design Constitution Re-Check

After completing Phase 1 artifacts (`research.md`, `data-model.md`, `contracts/*.yaml`, `quickstart.md`) and updating the agent context file, the 18 principles were re-evaluated. No design choice introduced a new violation:

- **II, III**: Every new table in `data-model.md` carries `company_id` and, where authority-facing, the full `(branch_id, authority, environment)` tuple.
- **IV, XIII**: The `JobPoller` holds no tenant state between ticks; ShedLock ensures at-most-one active poller across nodes; `JobReconciliationService` runs once on boot and transitions mid-submission items to the appropriate retryable/ambiguous state.
- **VI**: Contracts for audit and submission log endpoints are read-only and paginated; no mutation verb exists in `logs.yaml`.
- **XII**: Deployment contract (`quickstart.md` + `deploy/` tree) enforces HTTPS, externalized secrets, and separated DB container.
- **XVII**: Dashboard aggregation queries rely on indexes planned in `data-model.md` (notably `invoices(company_id, status, issue_date)` and the existing `submission_attempts(invoice_id, attempt_number)`), keeping p95 under the 2 s target.

No entry added to Complexity Tracking.

## Complexity Tracking

*No constitutional violations to justify.*
