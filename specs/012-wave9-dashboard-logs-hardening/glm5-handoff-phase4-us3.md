# GLM5 Handoff — Spec 012, Phase 4 (US3: Unified submission log across all four classes)

**Branch:** `012-wave9-dashboard-logs-hardening`
**Prereqs done (in working tree, may be uncommitted):** Phase 1 (T001 schema-at-V64, T002 `Wave9Fixtures`),
Phase 2 (T003 `UtcDateRange`, T004 package skeletons), Phase 3 / US1 (T005–T016 — dashboard implemented
company-less; `DashboardController`, `DashboardQueryService`, `EtaReadController`, `ZatcaReadController`,
`SubmissionAttemptRepository` dashboard queries, `TenantContext.Mode.AUTHORITY_SCOPED`).
**Your job:** Implement Phase 4 tasks **T017–T024** — one paginated, filterable submission log spanning
ETA Invoice / ETA Receipt / ZATCA Standard / ZATCA Simplified.

---

## 0. READ THIS FIRST — scoping model decision (overrides the written contract)

The product owner has chosen the **company-less (ADR-001) model**, **not** the per-company model written in
`contracts/submission-log.md` / `data-model.md` / `tasks.md`. ADR-001 explicitly lists **FR-010 / FR-011a /
FR-013** as superseded. Phase 3 already shipped the dashboard this way; **be consistent with it.**

**Apply these deltas to every Phase-4 task. Where this section conflicts with the contract files, this section wins.**

| Topic | Old contract (`contracts/submission-log.md`) | What you implement (company-less) |
|------|----------------------------------------------|-----------------------------------|
| Scope | viewer's *assigned* companies | **All attempts in the active `authority_environment_id`, across every company.** `authority_environment_id` is the **only** hard boundary — never leak across environments. |
| Mode | `OPERATIONAL_MODE` only | Allow **`AUTHORITY_SCOPED`** (the company-less read mode). Mirror exactly how `EtaReadController`/`DashboardController` is reached. |
| `COMPANY_CONTEXT_REQUIRED` 403 in Admin Mode | required | **Drop it.** No login-time company exists. `ADMIN_MODE` is already rejected upstream by the tenant filter. |
| `@RequiresPermission` VIEW gate | required | **Removed** (ADR-001 D4). Any authenticated user in the authority+environment may view. |
| Company column / filter "when >1 accessible" | conditional | Company-less reads are inherently cross-company, so **render the Company column and Company filter whenever more than one company is present** (in practice: always, in the env). The optional `companyId` param narrows to one company. |

Everything else in the contract — query params, `submittedAt DESC` default, the `transactionType → detail
route` map, the `IN_FLIGHT` outcome, the "cancelled doc still renders" edge case — **still applies.**

**Same-PR doc obligation:** update `contracts/submission-log.md` (and the `tasks.md` notes) to match the
company-less behaviour and the **actual envelope shape** you ship (see §1), so the contract never lags the code.

---

## 1. Envelope discrepancy — resolve it the repo's way (important)

`contracts/submission-log.md` shows a `PageResponse` envelope with `content` + `totalPages`. **That type does
not exist in this codebase.** The real Wave-9 read controllers (`EtaReadController`, `ZatcaReadController`)
return a plain map:

```java
return ResponseEntity.ok(Map.of(
        "items", result.getContent(),
        "page", result.getNumber(),
        "size", result.getSize(),
        "totalElements", result.getTotalElements()));
```

**Use this `items / page / size / totalElements` envelope** for `GET /api/submission-log` so the submission log
matches the rest of Wave 9 (the frontend paginator derives `totalPages` from `totalElements / size`). Then
edit the contract's JSON sample to match. Do **not** invent a `PageResponse` class.

---

## 2. Reference patterns already in the repo (copy these, don't invent)

- **Company-less, env-scoped, optional-company read service:**
  `platform-api/.../eta/invoice/service/EtaInvoiceService.java#list()` — resolves
  `Short authEnvId = TenantContext.getAuthorityEnvironmentId()`, builds a `Specification` via
  `OperationalRepositorySupport.authorityEnvironmentIdEquals(authEnvId)`, applies an **optional**
  `filterCompanyId`, returns a `Page`. **This is the exact shape your `/api/submission-log` query should follow** —
  scope by env, narrow by optional `companyId`, no company-set resolution needed for the list itself.
- **Company-less read controller + envelope:** `platform-api/.../eta/read/EtaReadController.java`
  (the `Map.of("items", …)` envelope and how `AUTHORITY_SCOPED` reaches the endpoint).
- **Spec-based paging on a write-only repo:** `SubmissionAttemptRepository extends WriteOnlyRepository`, which
  **already exposes** `Page<T> findAll(Specification<T> spec, Pageable pageable)` (it extends
  `JpaSpecificationExecutor`). **Prefer building a `Specification<SubmissionAttempt>` and calling `findAll` over a
  giant nullable-param `@Query`** — it cleanly handles the many optional filters. See
  `OperationalRepositorySupport` for the `authorityEnvironmentIdEquals`-style spec helpers.
- **Resolving company names for rows:** `DashboardQueryService#recentActivity()` (Phase 3) shows the pattern —
  collect the distinct `companyId`s on the page, `companyRepository.findAllById(ids)`, build a
  `Map<UUID,String>` of `id → nameEn`, and look up per row. Reuse this; `SubmissionAttempt` carries `companyId`
  but **not** a company name.
- **Tenant accessors:** `TenantContext.getAuthorityEnvironmentId()`, `getUserId()`, `getMode()`. (`getCompanyId()`
  is `null` under `AUTHORITY_SCOPED` — do **not** use it for scope.)

## 3. Domain facts you need

- `SubmissionAttempt` (`platform-core/.../domain/shared/SubmissionAttempt.java`) fields available for the row:
  `id`, `companyId`, `authorityEnvironmentId`, `transactionType`, `documentId`, `attemptNumber`, `submittedBy`,
  `result` (nullable), `statusCode`, `errorSummary`, `submittedAt` *(`OffsetDateTime`, UTC)*, `completedAt`.
- `SubmissionResult = {SUCCESS, REJECTED, ERROR, TIMEOUT, AMBIGUOUS}`; **`result == null` ⇒ outcome `IN_FLIGHT`.**
- `TransactionType = {INVOICE, RECEIPT, STANDARD, SIMPLIFIED}`.
- **All four document classes already write into the single `submission_attempts` table** (discriminated by
  `transaction_type`), so the unified list is one query over that table — no per-class union needed.

## 4. Business rules (get these exactly right)

- Default sort: `submittedAt DESC`.
- Filters (all optional): `companyId` (single-company narrowing within the env), `transactionType`, `outcome`,
  `dateFrom` (`submittedAt >=`), `dateTo` (`submittedAt <=`), plus `page`/`size` (default `size` = 20 per
  contract; you may align to the Wave-9 read default of 50 if you prefer — pick one and state it in the contract).
- **`outcome` filter must handle `IN_FLIGHT`:** `IN_FLIGHT` ⇒ `result IS NULL`; any other value ⇒ `result = :outcome`.
  In a `Specification` this is two branches; do not try to bind `IN_FLIGHT` as a `SubmissionResult` enum value.
- Row `outcome` field = `result.name()`, or `"IN_FLIGHT"` when `result == null` (mirror
  `DashboardQueryService#outcome`).
- `transactionType → frontend detail route` (verified against the Angular router — these are the live paths):
  - `INVOICE   → /invoices/eta/:documentId`
  - `RECEIPT   → /receipts/eta/:documentId`
  - `STANDARD  → /standard/:documentId`
  - `SIMPLIFIED→ /simplified/:documentId`
- A row whose underlying document was cancelled/removed **still renders**; the link resolves to an appropriate
  state, not an error page.

---

## 5. Tasks T017–T024 (TDD order; tests first and must fail before impl)

> Write the tests to assert the **AUTHORITY_SCOPED / cross-company-in-env / no-VIEW-gate / env-isolation**
> behaviour from §0 — **not** `COMPANY_CONTEXT_REQUIRED` / assigned-only. Build seeds with `Wave9Fixtures`.

### Tests
- **T017 [P]** `SubmissionLogContractTest`
  (`platform-api/src/test/java/com/einvoice/api/submission/SubmissionLogContractTest.java`):
  paging; `transactionType` / `outcome` (incl. `IN_FLIGHT`) / `dateFrom` / `dateTo` filters; `submittedAt DESC`
  default; the **`items/page/size/totalElements` envelope** (§1); reachable under `AUTHORITY_SCOPED` with **no**
  VIEW gate.
- **T018 [P]** `SubmissionLogIntegrationTest`
  (`platform-api/src/test/java/com/einvoice/api/submission/SubmissionLogIntegrationTest.java`):
  attempts from **all four classes across multiple companies** appear in **one** list with the owning company
  identified (`companyId` + `companyName`); the optional `companyId` filter narrows to a single company; and
  **attempts in another `authority_environment_id` never appear** (env isolation — the hard boundary). (FR-010
  becomes "cross-company is the default"; FR-013 env isolation still holds; FR-011a → Company column always on.)

### Implementation
- **T019 [US3]** Add the paged + filtered query to
  `platform-core/.../repository/shared/SubmissionAttemptRepository.java`. **Preferred:** expose a
  `Specification<SubmissionAttempt>` builder (env equals + optional `companyId` + optional `transactionType` +
  optional `outcome`/`IN_FLIGHT` + optional `submittedAt` bounds) and call the inherited
  `findAll(spec, pageable)` (sort `submittedAt DESC`). Mirror `EtaInvoiceService.list()` /
  `OperationalRepositorySupport`. (If you instead add a `@Query`, it must return `Page<SubmissionAttempt>` with an
  explicit `countQuery`, and still solve the `IN_FLIGHT` null-result case.)
- **T020 [P] [US3]** `SubmissionLogRowDto` + mapper in
  `platform-api/.../submission/dto/SubmissionLogRowDto.java` (Java record). Fields:
  `attemptId, companyId, companyName, transactionType, documentId, attemptNumber, outcome, statusCode,
  errorSummary, submittedAt, completedAt, submittedBy`. Mapper resolves `companyName` via the batched
  `CompanyRepository.findAllById` pattern from `DashboardQueryService#recentActivity` (don't N+1 per row).
- **T021 [US3]** `SubmissionLogController` (`platform-api/.../submission/SubmissionLogController.java`):
  `GET /api/submission-log`; **company-less** — allow `AUTHORITY_SCOPED`, **no** `@RequiresPermission`,
  **no** `COMPANY_CONTEXT_REQUIRED`; resolve env from `TenantContext.getAuthorityEnvironmentId()`; return the
  `items/page/size/totalElements` envelope. Depends on T019, T020. (A thin `SubmissionLogQueryService` in
  `platform-core` is acceptable if you'd rather keep the spec-building + name-resolution out of the controller —
  optional, but keep it consistent with the dashboard layering.)
- **T022 [P] [US3]** `frontend/src/app/submission-log/services/submission-log.service.ts`: typed call to
  `/api/submission-log` (passes the filter/paging params, reads the `items/…/totalElements` envelope) plus the
  `transactionType → detail route` map from §4.
- **T023 [US3]** `submission-log.component.ts` + `.html` (`frontend/src/app/submission-log/`): Material table +
  paginator; filters for `transactionType`, `outcome`, `dateFrom`, `dateTo`, **and a Company filter**; a
  **Company column** (always shown — company-less is cross-company); a per-row detail link built from the route
  map (class-correct); **loading / empty / error** view-states. Register the route in
  `frontend/src/app/app.routes.ts` next to the dashboard route, matching the dashboard's guard treatment. Populate
  the Company filter dropdown from the same company set the dashboard uses (the session company list). Depends on T022.
- **T024 [P] [US3]** `submission-log.component.spec.ts`: assert transaction-type rendering and that each row links
  to the correct class detail route.

---

## 6. Constraints / gotchas

- **No Flyway migration, no new dependency** (schema stays at V64).
- **Env isolation is the only hard boundary** — every query filters on `authority_environment_id`; never leak
  across environments. Cross-company within an env is intended.
- The list query is **one** query over `submission_attempts` (do not union the four header tables — they're only
  needed for the dashboard counts, not here).
- `submittedAt`/`completedAt` are UTC `OffsetDateTime`; serialize as ISO-8601 `Z`. Any future date math stays UTC.
- No secrets, payloads, or cert material in the response or logs — only the row fields in §5/T020.
- Keep DTOs as records; match existing controller/JSON conventions and the §1 envelope.

## 7. Definition of done

1. T017–T024 implemented; tests written first, failed, then pass.
2. `mvn -pl platform-core,platform-api test` green (at least the new `SubmissionLog*` tests);
   `cd frontend && ng test` green for `submission-log.component.spec.ts`.
3. `mvn -q -pl platform-api checkstyle:check` clean for new files.
4. `contracts/submission-log.md` (+ `tasks.md` notes) updated **in the same change** to the company-less model and
   the actual `items/…/totalElements` envelope (§0, §1).
5. Commit per logical group, e.g. `feat(012): US3 unified submission log (T017–T024, companyless scope)`.

## 8. Out of scope (do NOT do here)

- US2 `/api/admin/stats` (Phase 5, T025–T030), US4 audit scoping (Phase 7), US5 hardening/isolation/perf/docs
  (Phase 6), Phase 8 polish.
- Any change to the write path or the broader company-less write refactor
  (`glm5-handoff-companyless-login.md`). Only consume the read-side `AUTHORITY_SCOPED` plumbing that already
  exists from Phase 3.
