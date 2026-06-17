# Quickstart: Wave 9 — Dashboard, Logs, Hardening & Deployment Update

This wave is **read-side + hardening only** — no Flyway migration, no new dependency, no new module. You extend existing repositories with aggregate/paged queries, add a few read endpoints, fill the dashboard skeleton, add a submission-log screen, scope the audit viewer, and update deployment docs.

## Prerequisites

- Schema at **V64** (current). Confirm: `SELECT MAX(version) FROM flyway_schema_history;` → `64`.
- Stack runs: `docker compose up` brings up PostgreSQL + backend + frontend.
- Seed data with at least: two companies assigned to one operator; documents across all four classes in mixed states (`SUBMITTING/SUBMITTED/IN_REVIEW`, `REJECTED`, `ACCEPTED`); a ZATCA company with a certificate expiring < 30 days; submission attempts including `ERROR`/`TIMEOUT`/`AMBIGUOUS` results.

## Build & test gates

```bash
# Backend (from repo root)
mvn clean verify                          # unit + integration + golden-file + isolation tests
mvn test -pl platform-zatca,platform-eta  # golden-file regression (ETA JSON, ZATCA UBL, QR TLV)

# Frontend
cd frontend && ng test                    # Angular unit tests
```

## Manual verification (maps to spec Success Criteria)

1. **Operator dashboard (US1 / SC-001..SC-004)** — log in as the operator into the seeded authority+environment with both companies. Confirm: two cards with correct `pendingCount`/`failedCount`; the ZATCA company shows a certificate-expiry warning, the other does not; KPI panel shows today/this-month by status (UTC boundaries); recent-activity feed lists the latest 10 attempts across both companies, newest first. A non-assigned company never appears.
2. **Admin dashboard (US2 / SC-005)** — log in as Super User (Admin Mode). Confirm a card for every company in the env, a "Create Company" action, and `GET /api/admin/stats` totals matching seeded counts.
3. **Submission log (US3 / SC-006)** — open the submission log; confirm rows for all four classes with a transaction-type indicator; each link opens the correct class detail screen. Switch company/env and confirm no cross-context rows.
4. **Audit log (US4 / SC-007)** — generate audit entries under two contexts; confirm only the active context's entries appear.
5. **Hardening (US5 / SC-008..SC-011)** —
   - Throttle/empty/fail each data screen → confirm loading / empty / error states (never blank).
   - Load a 1,000+ row document list → returns < 2 s.
   - Fire two simultaneous same-company ZATCA submissions → exactly one proceeds, the other `CHAIN_BUSY`; chain counter advances once.
6. **Deployment (US5 / SC-010)** — from a prior-baseline DB, run the documented upgrade; confirm it reaches **V64** with no manual steps; verify `Docs/deployment-guide.md` and `Docs/configuration-reference.md` reflect the current schema.

## Isolation checks (Constitution XXIV.5/6)

- Operator assigned to Company A in env=1 must see no Company A data under env=2 (dashboard, submission log, audit log).
- ETA data must never surface through ZATCA endpoints/screens and vice versa.

## Done when

`mvn clean verify` + `ng test` green, all golden-file tests pass, isolation + concurrency tests pass, the six manual scenarios above check out, and the deployment/config docs are updated and the upgrade verified to V64.
