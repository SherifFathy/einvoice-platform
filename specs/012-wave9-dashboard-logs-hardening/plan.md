# Implementation Plan: Wave 9 — Dashboard, Logs, Hardening & Deployment Update

**Branch**: `012-wave9-dashboard-logs-hardening` | **Date**: 2026-06-02 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/012-wave9-dashboard-logs-hardening/spec.md`

## Summary

Wave 9 is read-side and hardening only: it adds aggregation endpoints that drive an operational dashboard (per-company pending/failed counts, certificate-expiry warnings, today/this-month KPIs, a 10-item recent-activity feed) and an Admin-Mode system-stats view; a unified, paginated/filterable submission-log viewer that spans all four document classes with class-correct detail links; and a company + `authority_environment_id` scoping update to the existing audit-log viewer. No new operational tables are introduced — all figures are derived read-models over the existing `submission_attempts`, the four document header tables, `zatca_configs`/certificate data, and `audit_logs`, all consumed through the mandatory `(company_id, authority_environment_id)` isolation key. The wave also hardens loading/empty/error states across data screens, verifies authority and authority-environment isolation, confirms ZATCA same-company chain serialization under concurrency, and brings `Docs/deployment-guide.md` and `Docs/configuration-reference.md` up to the current V64 schema with a verified upgrade path.

## Technical Context

**Language/Version**: Java 17 (backend), TypeScript 5.x / Angular 19 (frontend)
**Primary Dependencies**: Spring Boot 3.4.4 (web, data-jpa, security, validation), Spring Data JPA + Flyway 10.x, PostgreSQL JDBC driver, Jackson, Lombok; Angular Material, RxJS. No new dependencies introduced by this wave.
**Storage**: PostgreSQL 16 (single instance via Docker Compose). Schema is at **V64**; this feature adds **no new migration** — all data is read from existing tables (`submission_attempts`, `eta_invoice_headers`, `eta_receipt_headers`, `zatca_standard_headers`, `zatca_simplified_headers`, `zatca_configs`, `audit_logs`).
**Testing**: JUnit 5 + Spring Boot Test + Testcontainers (backend integration/golden-file/isolation); Jasmine + Karma (`ng test`) for Angular unit tests. `mvn clean verify` and `ng test` are the regression gates.
**Target Platform**: On-prem Linux server (app + DB logically separated), HTTPS mandatory; served as an Angular SPA against the Spring Boot REST API.
**Project Type**: Web application — multi-module Maven backend (`platform-api`, `platform-core`, `platform-eta`, `platform-zatca`, `platform-jobs`, `platform-security`, `platform-pdf`) + Angular frontend (`frontend/`).
**Performance Goals**: Document/list reads of 1,000+ rows return in < 2 s using the existing `(company_id, authority_environment_id[, state])` compound indexes; dashboard aggregation endpoints return within the same envelope on initial load.
**Constraints**: Strict tenant + authority-environment isolation on every operational query (Constitution II, III, XXIV); read-only aggregations must not weaken auditability; "today"/"this month" boundaries computed in **UTC**; certificate-expiry threshold < 30 days; submission/audit logs paginated + filterable; same-company ZATCA submissions remain serialized via the existing `SELECT FOR UPDATE` chain lock.
**Scale/Scope**: ~4 read endpoints (dashboard summary, recent-activity, admin system-stats, unified submission-log) + audit-log scoping change; Angular dashboard fill-in (cards stats, KPI panel, activity feed, admin stats) + new submission-log screen + audit-log scoping; deployment/config doc updates; full Wave 5–8 regression.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| # | Principle | Relevance to Wave 9 | Status |
|---|-----------|--------------------|--------|
| II | Multi-Tenant Isolation | Every new read query (dashboard counts, KPIs, activity feed, submission log) MUST filter by `(company_id, authority_environment_id)`; operators see only assigned companies, Super Users all companies in the active env. | PASS — designed into all contracts |
| III | Authority Environment Architecture | All aggregates are bound to the session's single `authority_environment_id`; no cross-env data. | PASS |
| VII | Admin Mode / Operational Mode Separation | Admin-Mode system-stats endpoint returns global-tier counts only (companies, users, submissions today); operational dashboard endpoints reject `company_id`-less misuse per existing `TenantFilter`. Admin Mode MUST NOT expose operational document content — system-stats are counts only. | PASS — see research R5 |
| VIII | Transaction Module Architecture | Submission log spans all four modules with a `transaction_type` discriminator and per-module detail links; dashboard counts aggregate across the user's assigned companies. | PASS |
| IX | Immutable Audit | Audit-log viewer is read-only and now scoped by company + `authority_environment_id`; no UPDATE/DELETE paths added. | PASS |
| X / XII | Deterministic Lifecycle / ZATCA Chain Integrity | No lifecycle or chain changes; wave only *verifies* same-company serialization under concurrency (FR-019) against existing `SELECT FOR UPDATE`. | PASS — verification only |
| XI | Physical Document Table Strategy | Read-models query the four existing header groups + shared `submission_attempts` discriminator; no unified document table created. | PASS |
| XV | Spring Boot Engineering | New endpoints follow controller→app-service→repository layering; every operational repository query filters by both keys; reuses `TenantContext`. No new migration (no schema change → V-rule N/A). | PASS |
| XIV | Angular Engineering | Dashboard/log screens read from session-context + new endpoints; action buttons remain permission-gated; no business logic moved to frontend; no secrets in responses (certificate expiry is a derived date/flag, never key material). | PASS |
| XXIV | Testing | Add isolation tests (env-1 vs env-2, ETA vs ZATCA), submission-log cross-class link tests, concurrency test for same-company ZATCA; preserve existing golden-file tests. | PASS — Phase 1 test plan |
| XXV | Performance | Reuse compound indexes; aggregation queries grouped by status over indexed columns; 1,000-row list < 2 s. | PASS |
| VI / XVIII | Server-Side Crypto / Cert Storage | Certificate-expiry warning derives only the *expiry date / days-remaining* server-side; no certificate, key, or secret is sent to Angular. | PASS |

**Initial Constitution Check: PASS** — no violations; Complexity Tracking not required.

**Post-Design Constitution Re-check (after Phase 1): PASS** — the design adds only read-only aggregation endpoints + an audit scoping filter, no new tables/migrations, no write paths, and no secret exposure. Isolation `(company_id, authority_environment_id)` is enforced on every new query; the Admin-Mode stats endpoint returns counts only (VII.3 preserved); certificate handling exposes a derived `daysRemaining`/flags only (VI/XVIII). No new violations introduced by the contracts or data model.

## Project Structure

### Documentation (this feature)

```text
specs/012-wave9-dashboard-logs-hardening/
├── plan.md              # This file (/speckit.plan command output)
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output (read-model projections)
├── quickstart.md        # Phase 1 output
├── contracts/           # Phase 1 output (REST endpoint contracts)
│   ├── dashboard.md
│   ├── submission-log.md
│   └── audit-log.md
├── checklists/
│   └── requirements.md  # From /speckit.specify
└── tasks.md             # /speckit.tasks output (NOT created here)
```

### Source Code (repository root)

```text
platform-api/src/main/java/com/einvoice/api/
├── dashboard/                         # NEW — operational dashboard read endpoints
│   ├── DashboardController.java       #   GET /api/dashboard/summary, /recent-activity
│   ├── AdminStatsController.java      #   GET /api/admin/stats (Admin Mode, global counts)
│   └── dto/                           #   CompanyCardDto, DashboardKpiDto, RecentActivityDto, SystemStatsDto
├── submission/                        # NEW — unified submission-log read endpoint
│   ├── SubmissionLogController.java   #   GET /api/submission-log (paged, filterable)
│   └── dto/SubmissionLogRowDto.java
└── audit/                             # EXISTING — extend read path with env scoping
    └── (audit read controller/service: add authority_environment_id filter)

platform-core/src/main/java/com/einvoice/core/
├── repository/shared/
│   ├── SubmissionAttemptRepository.java   # EXTEND — paged/filtered + aggregate count queries
│   └── AuditLogRepository.java            # EXTEND — company + authority_environment_id filter
├── repository/{eta,zatca}/                # EXTEND — status-grouped count queries per header table
└── service/dashboard/                     # NEW — DashboardQueryService, CertificateExpiryEvaluator

frontend/src/app/
├── dashboard/                         # FILL-IN — cards get stats, add KPI panel + activity feed + admin stats
│   ├── dashboard.component.ts/html
│   └── services/dashboard.service.ts  # NEW
├── submission-log/                    # NEW — unified submission-log screen
│   ├── submission-log.component.ts/html
│   └── services/submission-log.service.ts
├── logs/                              # EXISTING — audit viewer; scoping handled server-side
└── shared/services/audit-log.service.ts   # EXISTING

tests/  +  platform-*/src/test/java/...    # isolation, cross-class link, concurrency, regression
Docs/deployment-guide.md  +  Docs/configuration-reference.md   # UPDATE for V64 schema
```

**Structure Decision**: Web application (Option 2). Backend lives in repo-root Maven modules (not a `backend/` folder); new read endpoints are added under `platform-api/.../api/dashboard` and `.../api/submission`, backed by new aggregate query methods on existing `platform-core` repositories and a new `platform-core/.../service/dashboard` query service. Frontend fills the existing `dashboard/` skeleton and adds a `submission-log/` feature folder, matching the established per-feature Angular layout. No new module and no Flyway migration are introduced.

## Complexity Tracking

> No Constitution Check violations — section intentionally empty.
