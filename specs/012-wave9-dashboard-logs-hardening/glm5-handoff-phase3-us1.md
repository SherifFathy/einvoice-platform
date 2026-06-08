# GLM5 Handoff — Spec 012, Phase 3 (US1: Operator dashboard of submission health)

**Branch:** `012-wave9-dashboard-logs-hardening`
**Prereqs done:** Phase 1 (T001 schema-at-V64, T002 `Wave9Fixtures`), Phase 2 (T003 `UtcDateRange`, T004 package skeletons — all committed/staged).
**Your job:** Implement Phase 3 tasks **T005–T016**.

---

## 0. READ THIS FIRST — scoping model decision (overrides the written contracts)

The product owner has chosen the **company-less (ADR-001) model** for this dashboard, **not** the
per-company model written in `contracts/dashboard.md` / `data-model.md` / `tasks.md`. Those docs
predate ADR-001 and are explicitly superseded by it (ADR-001 lists FR-010 / FR-011a / FR-013 as
superseded). The working tree is already mid-migration to this model:
`TenantContext.Mode.AUTHORITY_SCOPED` exists, `EtaReadController`/`ZatcaReadController` are env-scoped
& cross-company, and `EtaInvoiceService.list()` scopes by `authority_environment_id` only.

**Apply these deltas to every Phase-3 task. Where this section conflicts with the contract files, this section wins.**

| Topic | What the old contract says | What you implement (companyless) |
|------|----------------------------|----------------------------------|
| Scope | assigned companies only | **All active companies in the active `authority_environment_id`** |
| Mode | `OPERATIONAL_MODE` only | Allow **`AUTHORITY_SCOPED`** (the companyless read mode). Mirror exactly how `EtaReadController` is reached. |
| `COMPANY_CONTEXT_REQUIRED` 403 in Admin Mode | required | **Drop it** for the summary/recent-activity reads (no login-time company anymore). Admin-only stats stay on `/api/admin/stats` (US2, not your phase). |
| `@RequiresPermission` VIEW gate | required | **Removed** for these read endpoints (ADR-001 D4). Any authenticated user in the authority+environment may view. |

Everything else in the contract (response shapes, the count/cert rules, UTC boundaries, `IN_FLIGHT`)
**still applies** — only the *scope/mode/permission* changes.

**Same-PR doc obligation:** update `contracts/dashboard.md`, `data-model.md`, and the relevant
`tasks.md` notes to match the companyless behaviour you ship (or fold in
`spec-edits-companyless-login.md`), so the contract never lags the code.

---

## 1. Reference patterns already in the repo (copy these, don't invent)

- **Env-scoped read service:** `platform-api/.../eta/invoice/service/EtaInvoiceService.java#list()` —
  resolves `Short authEnvId = TenantContext.getAuthorityEnvironmentId()`, builds a
  `Specification` via `OperationalRepositorySupport.authorityEnvironmentIdEquals(authEnvId)`, applies
  an **optional** `filterCompanyId`. No company list, no permission gate.
- **Companyless read controller:** `platform-api/.../eta/read/EtaReadController.java` (untracked) and
  `.../zatca/read/ZatcaReadController.java` — the controller shape + how `AUTHORITY_SCOPED` reaches it.
- **Resolving "all companies in env":** `SessionContextAssembler.buildSuperUserCompanies()` already
  does it: `uctrRepo.findDistinctCompanyIdsByAuthorityEnvironmentIdAndIsActiveTrue(authEnvId)` →
  `companyRepository.findAllById(ids)` filtered by `isActive`. **Use this as the card set** so idle-but-
  registered companies still render zero-count cards (matches the contract's "Beta Co" example).
  > Decision flag: if ingested documents can belong to companies with no UCTR row, that company would
  > be missing from the cards. If the PO wants those shown too, union in the distinct `company_id`s from
  > the four header tables for the env. Default = UCTR set; raise it if unsure.
- **Tenant accessors:** `TenantContext.getAuthorityEnvironmentId()`, `getUserId()`, `getMode()`,
  `isSuperUser()`. (`TenantContext` carries a single `companyId`, which is `null` in `AUTHORITY_SCOPED` —
  do **not** rely on it for scope.)
- **UTC boundaries:** `com.einvoice.core.util.UtcDateRange` (Phase 2). Use `todayRange(clock)` /
  `thisMonthRange(clock)` for the KPI windows; bind `from`/`to` into `column >= :from AND column < :to`.
  Inject a `java.time.Clock` (register a `@Bean Clock systemUTC` or default to `Clock.systemUTC()` in the
  service) so tests can pin it.

## 2. Domain facts you need

- `DocumentState = {DRAFT, SUBMITTING, SUBMITTED, IN_REVIEW, ACCEPTED, REJECTED, CANCELLED}`
  (`platform-core/.../domain/shared/DocumentState.java`).
- `SubmissionResult = {SUCCESS, REJECTED, ERROR, TIMEOUT, AMBIGUOUS}`; `result == null` ⇒ outcome `IN_FLIGHT`.
- `TransactionType = {INVOICE, RECEIPT, STANDARD, SIMPLIFIED}`.
- `SubmissionAttempt` (`.../domain/shared/SubmissionAttempt.java`): `companyId`, `authorityEnvironmentId`,
  `transactionType`, `documentId`, `attemptNumber`, `result`, `submittedAt` **(`OffsetDateTime`, UTC)**, `completedAt`.
- Four header tables each have `state`, `companyId`, `authorityEnvironmentId`, a doc timestamp, number.
  Repos: `EtaInvoiceHeaderRepository`, `EtaReceiptHeaderRepository` (`repository/eta/`),
  `ZatcaStandardHeaderRepository`, `ZatcaSimplifiedHeaderRepository` (`repository/zatca/`).
- **Certificate:** `platform-core/.../domain/config/ZatcaConfig.java` →
  `LocalDate certificateExpiryDate`, `Boolean isActive`, plus `complianceCertificate` /
  `productionCertificate` (TEXT). **NEVER** return the certificate strings or any key material — only the
  derived `{daysRemaining, expiringSoon, expired}`. ETA has no signing cert ⇒ `certificate = null`.

## 3. Business rules (unchanged from contract — get these exactly right)

- `pendingCount` = COUNT documents with `state ∈ {SUBMITTING, SUBMITTED, IN_REVIEW}`.
- `failedCount` = COUNT documents with `state = REJECTED` **OR** whose latest `SubmissionAttempt.result ∈ {ERROR, TIMEOUT}`.
  (Don't double-count a doc that is both.)
- KPI counts exclude nothing — `byStatus` reports **all seven** states; `total` = sum. `today` window =
  `UtcDateRange.todayRange`, `thisMonth` = `thisMonthRange`, keyed on the header doc timestamp.
- Cert: `daysRemaining = certificateExpiryDate − todayUTC`; `expiringSoon = 0 <= d < 30`; `expired = d < 0`.
- Recent activity: top 10 attempts across the accessible company set in the env, `ORDER BY submittedAt DESC`;
  fewer than 10 ⇒ return all; empty ⇒ `entries: []`.

---

## 4. Tasks T005–T016 (TDD order; tests first and must fail before impl)

> Adjust the test expectations from the contract per §0: assert the **AUTHORITY_SCOPED / all-companies-in-env /
> no-VIEW-gate** behaviour, **not** `COMPANY_CONTEXT_REQUIRED` / assigned-only. Build seeds with `Wave9Fixtures`.

### Tests
- **T005 [P]** `DashboardSummaryContractTest` (`platform-api/src/test/java/com/einvoice/api/dashboard/`):
  response shape, pending/failed rules, `certificate=null` for ETA, UTC KPI boundaries, AUTHORITY_SCOPED access.
- **T006 [P]** `RecentActivityContractTest`: top-10, newest-first, `IN_FLIGHT` outcome, <10 returns all.
- **T007 [P]** `DashboardSummaryIntegrationTest`: counts match seeded docs; cert warning < 30 days;
  **all companies in the env appear (cross-company), and a company/data in another `authority_environment_id`
  never appears** (env isolation is now the hard boundary).

### Implementation
- **T008 [P]** Add status-grouped count queries (`GROUP BY state` filtered by `(companyId, authorityEnvironmentId)`,
  and an env-only variant for the KPI aggregate) to the four header repos.
- **T009 [P]** Add to `SubmissionAttemptRepository` (`repository/shared/`): (a) "latest attempt result per
  document" for the failed-by-ERROR/TIMEOUT rule, and (b) "top-10 recent attempts for a set of company_ids in
  an env" for the activity feed. Filter `company_id IN (:ids) AND authority_environment_id = :env`.
- **T010** `CertificateExpiryEvaluator` (`platform-core/.../service/dashboard/`): reads active
  `ZatcaConfig.certificateExpiryDate`; emits `{daysRemaining, expiringSoon, expired}`; `null` for ETA;
  never exposes key material.
- **T011** `DashboardQueryService` (`platform-core/.../service/dashboard/`): resolve accessible company set
  (§1), assemble cards (counts + cert) and the KPI breakdown via `UtcDateRange`. Depends on T008–T010.
- **T012 [P]** DTOs in `platform-api/.../dashboard/dto/`: `CompanyCardDto`, `CertificateStatusDto`,
  `DashboardKpiDto`, `StatusBreakdownDto`, `RecentActivityDto` (Java records). Shapes per `contracts/dashboard.md`.
- **T013** `DashboardController` (`platform-api/.../dashboard/`): `GET /api/dashboard/summary`,
  `GET /api/dashboard/recent-activity`. **Companyless:** allow `AUTHORITY_SCOPED`, **no** `@RequiresPermission`,
  **no** `COMPANY_CONTEXT_REQUIRED`. Depends on T011, T012.
- **T014 [P]** `frontend/src/app/dashboard/services/dashboard.service.ts`: typed calls to `/api/dashboard/summary`
  and `/api/dashboard/recent-activity`.
- **T015** Fill `dashboard.component.ts` + `.html`: today the component only renders identity cards from
  `SessionContext.companies` — wire it to the live endpoints for pending/failed counts, cert-expiry indicator,
  KPI panel, and the 10-item activity feed, with **loading / empty / error** states. Depends on T014.
- **T016 [P]** Extend `dashboard.component.spec.ts` for stats rendering + empty state.

---

## 5. Constraints / gotchas

- No Flyway migration, no new dependency (schema stays at V64).
- Every operational query filters on `authority_environment_id`; per-card counts also filter on that card's
  `company_id`. **Env isolation is now the only hard boundary — never leak across `authority_environment_id`.**
- All "today"/"this month" math in **UTC** via `UtcDateRange` — no `LocalDate.now()`/server-local time.
- No secrets/cert bytes in any response or log.
- Keep DTOs as records; match existing controller/JSON conventions (the read controllers return
  `Map.of("items", ..., "page", ...)`-style envelopes — follow `contracts/dashboard.md` for the dashboard's
  own shapes, which are object-typed, not paged).

## 6. Definition of done

1. T005–T016 implemented; tests written first, failed, then pass.
2. `mvn -pl platform-core,platform-api test` green (at least the new dashboard tests +
   `UtcDateRangeTest`); `cd frontend && ng test` green for the dashboard spec.
3. `mvn -q -pl platform-api checkstyle:check` clean for new files.
4. Contract/spec docs updated in the same change to reflect the companyless model (§0).
5. Commit per logical group, e.g. `feat(012): US1 operator dashboard (T005–T016, companyless scope)`.

## 7. Out of scope (do NOT do here)

- US2 `/api/admin/stats` (T025–T029), US3 submission-log, US4 audit scoping, US5 hardening.
- The broader companyless write-path refactor (that's `glm5-handoff-companyless-login.md`). Only consume the
  read-side `AUTHORITY_SCOPED` plumbing that already exists.
