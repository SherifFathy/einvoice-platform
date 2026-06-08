# GLM5 Handoff — Company-less Login & Cross-Company Document Visibility

**Branch:** `012-wave9-dashboard-logs-hardening`
**Author of handoff:** Claude (analysis only — no code changed)
**Date:** 2026-06-02
**Status:** Ready for implementation

---

## 1. Goal (product decision)

Remove company selection from the login screen and let **any authenticated user see all
documents that exist in the chosen authority + environment**, regardless of which company
owns them or whether that company appears on the Companies screen.

### Decisions locked with the product owner

| # | Decision | Choice |
|---|----------|--------|
| D1 | Company selector on login | **Removed for all users.** Login establishes only `authority` + `environment`. |
| D2 | Reads (list / detail / dashboard / logs) | **Cross-company.** Show every document in the active `authority+environment` across all companies. |
| D3 | Writes (create / edit / submit / cancel / retry / delete) | **Still company-scoped.** The owning company is chosen in the create form; edit/submit/etc. follow the document's own company. |
| D4 | VIEW permission gate on document screens | **Dropped.** Any logged-in user (in that authority+environment) can view documents. Per-type VIEW permission is no longer required for read endpoints. |
| D5 | Write permissions (CREATE/EDIT/DELETE/SUBMIT/CANCEL/RETRY) | **Preserved**, but evaluated against the **selected/owning company**, not a login-time company. |

> ⚠️ This **reverses the per-company tenant isolation** that is currently a core security
> control, and it **contradicts** `specs/002-platform-foundation-tenancy` and `specs/012-.../spec.md`
> **FR-010**. Those specs must be updated (Section 6) or the next wave will revert this.

---

## 2. Current architecture (what exists today)

The platform is company-scoped end to end. Key pieces:

### 2.1 Login & JWT
- `platform-api/.../auth/dto/LoginRequest.java` — record `(email, password, authority, environment, companyId)`. `companyId` is optional in the DTO but enforced at runtime.
- `platform-api/.../auth/AuthService.java` `login()` (lines ~132-191):
  - Super user + `companyId == null` → `TenantContext.Mode.ADMIN_MODE`.
  - Super user + `companyId` → `OPERATIONAL_MODE`.
  - **Non-super user + `companyId == null` → throws `CompanyContextRequiredException` (`COMPANY_CONTEXT_REQUIRED`).**
  - Non-super user must have an active `UserCompanyTransactionRole` for that company+authEnv, else `UnauthorizedContextException`.
- `platform-security/.../jwt/JwtTokenProvider.java`:
  - `createToken(userId, email, isSuperUser, authority, environment, authEnvId, companyId, mode)`.
  - Claims: `email, isSuperUser, authority, environment, authorityEnvironmentId, mode`, and `companyId` **only if non-null** (line ~61).
  - `parseToTenantContext()` rebuilds the `Holder`.

### 2.2 Tenant context & enforcement
- `platform-security/.../tenant/TenantContext.java` — `Holder(userId, companyId, authorityEnvironmentId, authority, environment, mode, isSuperUser, issuedAt, jti)`; `Mode { ADMIN_MODE, OPERATIONAL_MODE }`.
- `platform-security/.../jwt/JwtAuthenticationFilter.java` — parses token → `TenantContext.set(holder)`.
- `platform-security/.../tenant/TenantFilter.java` (`@Order(2)`):
  - Bypasses `/api/auth/**` and `/api/session/context`.
  - **In `ADMIN_MODE`, only `/api/admin/**` is allowed; everything else → 403 `COMPANY_CONTEXT_REQUIRED`.**
- `platform-security/.../permission/PermissionAspect.java` (`@Around @RequiresPermission`):
  - Super user in `OPERATIONAL_MODE` → bypass.
  - `ADMIN_MODE` → rejected.
  - Else requires non-null `ctx.companyId()` and `permissionService.hasPermission(userId, companyId, authEnvId, txType, action)`.
- `platform-security/.../operational/OperationalModeAspect.java` (`@RequireOperationalMode`) — rejects `ADMIN_MODE`.

### 2.3 Document endpoints (the 4 in scope)
All nested under a company path with `@PathVariable UUID companyId`, `verifyContext(companyId)`, and `@RequiresPermission`:

| Doc type | Controller | Base path |
|----------|-----------|-----------|
| ETA Invoice | `eta/invoice/EtaInvoiceController.java` | `/api/companies/{companyId}/eta/invoices` |
| ETA Receipt | `eta/receipt/EtaReceiptController.java` | `/api/companies/{companyId}/eta/receipts` |
| ZATCA Standard | `zatca/standard/ZatcaStandardController.java` | `/api/companies/{companyId}/zatca/standard` |
| ZATCA Simplified | `zatca/simplified/ZatcaSimplifiedController.java` | `/api/companies/{companyId}/zatca/simplified` |

Services resolve scope from `TenantContext.getCompanyId()` (e.g. `ZatcaStandardService` lines ~100-440: `list()`, `findById()`, `create()` all use the tenant company). Repositories filter by `company_id` + `authority_environment_id`.

> Related company-scoped controllers **out of scope for the read change** but listed for awareness: items, customers, config, submission, artifact — all under `/api/companies/{companyId}/...`.

### 2.4 Session context
- `platform-api/.../session/SessionContextController.java` → `/api/session/context`.
- `platform-api/.../session/SessionContextAssembler.java`:
  - `ADMIN_MODE` → returns empty `companies[]`.
  - Otherwise builds `companies[]` with per-module permission flags. `activeCompanyId = holder.companyId()`.
  - Document modules: `invoice, receipt, standard, simplified`.

### 2.5 Frontend
- Login: `frontend/src/app/auth/auth.component.ts` (+ `.html`) — has `companyId` form control, `listEnvironments`, `listCompanies`, `isSuperUser` logic, `loginDisabled` requires a company unless super user.
- `frontend/src/app/shared/services/auth.service.ts` — `login(email,password,authority,environment,companyId)` posts `companyId`; `SessionContext.activeCompanyId`.
- `frontend/src/app/shared/services/session-context.service.ts` — holds `SessionContext`.
- Document services build URLs from `companyId` (e.g. `standard/services/zatca-standard.service.ts` lines ~122-196).
- List/detail/form components read `context()?.activeCompanyId` to build the path companyId. Examples:
  - `standard/zatca-standard-list.component.ts:178,228`, `…-detail.component.ts:193,224,233,242,…`, `…-form.component.ts:149,163,198`.
  - Same pattern in `simplified/*`, `invoices/eta/*`, `receipts/eta/*`.
- `frontend/src/app/shared/guards/operational-mode.guard.ts` and `app.routes.ts` gate operational routes.

---

## 3. Target behavior

1. **Login** — form collects email, password, authority, environment only. Token carries no company.
2. **Reads** — list, detail, dashboard, and logs return data for the active authority+environment **across all companies**; available to any authenticated user; no VIEW permission check.
3. **Writes** — unchanged company scoping: the create form selects the owning company; edit/submit/cancel/retry/delete operate on that document's company and still enforce the per-company write permission.

---

## 4. Backend changes

### 4.1 Login / token (allow company-less context)
- `LoginRequest` — keep `companyId` optional; it will normally be absent.
- `AuthService.login()`:
  - Remove the `COMPANY_CONTEXT_REQUIRED` throw for non-super users with no company.
  - When `companyId == null` (any user), issue a token with **`companyId = null`** and a context that is allowed to reach **read** endpoints. **Recommended:** add a new mode `TenantContext.Mode.AUTHORITY_SCOPED` (company-less, cross-company read). Keep `OPERATIONAL_MODE` for company-scoped writes and `ADMIN_MODE` for `/api/admin`.
  - When `companyId != null` (e.g. a write context elevation, if you choose that route) → `OPERATIONAL_MODE` as today.
- `TenantContext.Mode` — add `AUTHORITY_SCOPED`.
- `JwtTokenProvider` — no signature change needed; `mode` claim already serialized. Ensure `AUTHORITY_SCOPED` round-trips.

### 4.2 Tenant filter / aspects (let company-less reads through)
- `TenantFilter`:
  - Allow `AUTHORITY_SCOPED` to reach the new **read** endpoints (Section 4.3). Continue to 403 it on company-scoped **write** paths (or rely on the write aspects below).
  - Leave `ADMIN_MODE` behavior unchanged.
- `PermissionAspect` / `@RequiresPermission`:
  - Read endpoints will **not** carry `@RequiresPermission` (D4), so no change needed for reads.
  - For writes (D5), evaluate permission against the **path/selected company** instead of a token company. Recommended: introduce a variant that reads `companyId` from the request path and calls `permissionService.hasPermission(userId, pathCompanyId, authEnvId, txType, action)`. This lets a company-less session still perform writes for a company it has rights on.
- `OperationalModeAspect` — unchanged; keep it on write-only flows if you keep `@RequireOperationalMode` there.

### 4.3 New cross-company READ endpoints (the core of D2/D4)
Add company-less read endpoints for the 4 doc types, filtered by the token's `authorityEnvironmentId` only, **without** `@RequiresPermission`:

```
GET /api/{authority}/{type}            → list (filter: authorityEnvironmentId, optional status/date/company)
GET /api/{authority}/{type}/{docId}    → detail
```
e.g. `GET /api/zatca/standard`, `/api/zatca/simplified`, `/api/eta/invoices`, `/api/eta/receipts`.

- New service methods: `listAll(authEnvId, filters…)` and `findByIdCrossCompany(docId, authEnvId)` that filter by `authority_environment_id` and **not** by `company_id`. Reuse existing response DTOs.
- Keep an optional `?company=` filter so the UI can still narrow voluntarily.
- Submissions/artifacts for **detail** reads should also be reachable cross-company (read-only) — add company-less read variants or relax the existing ones for `AUTHORITY_SCOPED`.

> Alternative (smaller diff, less clean): keep the existing `/api/companies/{companyId}/...` GET routes but ignore `companyId` for filtering when mode is `AUTHORITY_SCOPED`. **Not recommended** — it muddies the security model and the path lies about scope. Prefer dedicated read routes.

### 4.4 Writes (unchanged scoping, D3/D5)
- Keep `POST/PUT/DELETE/submit/cancel/retry` on `/api/companies/{companyId}/...`.
- Create form supplies the chosen `companyId` in the path; permission evaluated against it (4.2).
- `*Service.create()` must take the company from the path/argument rather than `TenantContext.getCompanyId()` (which is now null for a company-less session). Audit each service for `TenantContext.getCompanyId()` reads on the write path and replace with the explicit company.

### 4.5 Session context
- `SessionContextAssembler.assemble()`:
  - Handle `AUTHORITY_SCOPED`: return `activeCompanyId = null` and a `companies[]` list suitable for the **create-form company picker** (the companies the user may write to, or all active companies in the authEnv — confirm with PO; default: companies where the user has any active CREATE assignment, plus all-active for super users).
  - The UI no longer needs an `activeCompanyId` for reads.

### 4.6 Dashboard & logs (012, forward-looking)
- No `DashboardController`/log controller exists yet on this branch. When the 012 dashboard/log **backend** endpoints are implemented, scope them by `authority+environment` only (per revised FR-010), not by company.

---

## 5. Frontend changes

1. **Login** (`auth/auth.component.ts` + `.html`): remove the company `mat-select`, `companyOptions`, `listCompanies`, `companyLoad$`, `isSuperUser`, and the company clause in `loginDisabled`. Submit with `companyId = null`.
2. **auth.service.ts**: `login()` drops the `companyId` argument (or always sends `undefined`). `SessionContext.activeCompanyId` becomes nullable/unused for reads.
3. **Document services** (`standard`, `simplified`, `invoices/eta`, `receipts/eta`):
   - List/detail/submissions calls → new company-less read URLs (`/api/{authority}/{type}…`).
   - Create/edit/submit/cancel/retry/delete → keep `/api/companies/{companyId}/...`, where `companyId` is the **selected/owning** company (from the create form or the loaded document), not `activeCompanyId`.
4. **List/detail/form components**: stop sourcing the path company from `context()?.activeCompanyId`. For reads, drop it; for a loaded document, use the document's own `companyId` (ensure detail responses include it); for create, use the form's company picker.
5. **Create forms**: add a company selector (populated from session context's writable companies). This is the only place company is chosen now.
6. **Guards/routes** (`operational-mode.guard.ts`, `app.routes.ts`): document **list/detail** routes must be reachable in `AUTHORITY_SCOPED` (no operational/company requirement). Keep create/edit routes behind the write requirements.
7. **Sidebar/header/dashboard**: remove any "active company" display/switcher tied to reads.

---

## 6. Spec updates (required)

- `specs/002-platform-foundation-tenancy/` (`spec.md`, `contracts/auth-api.md`): document that login is company-less and reads are cross-company; reconcile or deprecate `switch-company`.
- `specs/012-wave9-dashboard-logs-hardening/spec.md` **FR-010** and the dashboard scoping bullets: change "scoped to the companies the viewer can access" → "scoped to the active authority+environment (all companies)".
- Add a short ADR/note capturing D1–D5 and the security trade-off (loss of per-company read isolation) so it isn't reverted.

---

## 7. Tests to add / update

- `RegularUserLoginIT`, `AuthLoginContractTest` — non-super user can now log in with no company (no `COMPANY_CONTEXT_REQUIRED`).
- `TenantIsolationIT` / `TenantInterceptor` tests — reads are intentionally cross-company now; rewrite expectations. **Be deliberate**: these tests encode the isolation guarantee being removed.
- New IT: company-less user lists documents from multiple companies in one authority+environment.
- Write-path ITs: company-less user can create only for companies they have CREATE on; permission denied otherwise.
- `PermissionParityIT`, `NextLoginPermissionRefreshIT` — review for company assumptions.
- Frontend: `auth.component.spec.ts`, `zatca-standard.service.spec.ts` (and the other doc service specs) — URL and form expectations change.

---

## 8. Out of scope / preserve

- Items, customers, config remain company-scoped (no change).
- Admin endpoints (`/api/admin/**`) and `ADMIN_MODE` unchanged.
- ERP ingestion gateway (`/api/integration/v1/...`) unchanged.

---

## 9. Risks & call-outs

1. **Security**: this removes per-company read isolation. Every authenticated user in an authority+environment sees every company's documents. Confirm this is acceptable for the deployment's data-sensitivity before shipping. Consider keeping an audit log of cross-company reads.
2. **Write company resolution**: the trickiest part is that services currently read the owning company from `TenantContext`. Every write path must be switched to the explicit selected company, or you risk `null` company on create. Grep each `*Service` for `TenantContext.getCompanyId()` and triage read vs write.
3. **Detail responses must expose `companyId`** so edit/submit can target the right company path without a login-time company.
4. **`switch-company` / operational-mode** plumbing may become partly dead; clean up to avoid confusion.
5. **Checkstyle**: `platform-api` currently fails checkstyle (162 pre-existing violations); the local run skips it with `-Dcheckstyle.skip=true`. New code should still satisfy the ruleset to not add to the debt.

---

## 10. Suggested implementation order

1. Backend: add `AUTHORITY_SCOPED` mode + `AuthService` login change + `JwtTokenProvider` round-trip. (Login works company-less, ADMIN-style.)
2. Backend: new cross-company READ endpoints + services for the 4 doc types; loosen `TenantFilter` for them.
3. Backend: write-path company resolution from path + permission-by-path; ensure create/edit/submit still secure.
4. Backend: `SessionContextAssembler` for `AUTHORITY_SCOPED` (writable companies for the create picker).
5. Frontend: login screen, auth service, session context.
6. Frontend: document services/components → read URLs; create form company picker; routes/guards.
7. Specs + tests.
8. Build (`mvn … -Dcheckstyle.skip=true` locally) + `ng build`; manual verify per Section 3.

---

*Prepared from a read-only analysis of branch `012-wave9-dashboard-logs-hardening` on 2026-06-02. No source files were modified.*
