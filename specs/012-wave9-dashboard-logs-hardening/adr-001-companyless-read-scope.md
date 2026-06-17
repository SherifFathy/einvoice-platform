# ADR-001 — Company-less Login & Cross-Company Read Scope

**Status:** Accepted
**Date:** 2026-06-02
**Branch:** `012-wave9-dashboard-logs-hardening`
**Deciders:** Product owner (decision), Claude (analysis/record — no code changed)
**Related:** `glm5-handoff-companyless-login.md`, `spec-edits-companyless-login.md`

---

## Context

The platform was designed and built as **per-company multi-tenant**, isolated end to end:

- **Login** establishes an active company. Non-super users **must** pick a company
  (`AuthService.login` throws `COMPANY_CONTEXT_REQUIRED` otherwise); super users without a
  company land in `ADMIN_MODE`.
- The **JWT** carries a `companyId` claim and a `mode` (`ADMIN_MODE` | `OPERATIONAL_MODE`).
- `TenantContext` + `TenantFilter` pin every request to one company; `ADMIN_MODE` is blocked
  from all operational endpoints (`COMPANY_CONTEXT_REQUIRED`).
- `PermissionAspect` (`@RequiresPermission`) checks `hasPermission(user, companyId, authEnv, type, action)`
  against the token's company.
- All document endpoints are nested under `/api/companies/{companyId}/...` (ETA Invoice,
  ETA Receipt, ZATCA Standard, ZATCA Simplified, plus items/customers/config/submission/artifact).
- Repositories filter by `company_id` **and** `authority_environment_id`.
- The frontend builds every document URL from `SessionContext.activeCompanyId` and shows a
  company selector on the login screen.

This is codified in `specs/002-platform-foundation-tenancy` (FR-001 data isolation; US2
multi-company switching) and reinforced by `specs/012` FR-010/FR-011a/FR-013 (per-company
dashboard and submission-log scoping).

The product owner has decided this model is wrong for the intended workflow: users think in
terms of **authority + environment**, not company, and need to see **all** documents in a
chosen authority+environment regardless of which company owns them or whether that company is
registered on the Companies screen.

## Decision

Adopt a **company-less read scope** keyed on authority+environment, while keeping documents
**owned** by a company and keeping **writes** company-permissioned.

| ID | Decision |
|----|----------|
| **D1** | The login screen collects **email, password, authority, environment** only. No company selection. |
| **D2** | **Reads** (list, detail, dashboard, submission log) are scoped to the active **authority + environment** and span **all companies** in that context. |
| **D3** | **Writes** (create/edit/submit/cancel/retry/delete) remain **company-scoped**. The owning company is chosen in the **create form**; edit/submit/etc. follow the document's own company. |
| **D4** | The per-type **VIEW** permission gate is **removed** for document read screens. Any authenticated user in the authority+environment can view. |
| **D5** | **Write** permissions (CREATE/EDIT/DELETE/SUBMIT/CANCEL/RETRY) are **preserved**, evaluated against the **selected/owning company** rather than a login-time company. |

### Mechanism (summary; full plan in the handoff)

- Add `TenantContext.Mode.AUTHORITY_SCOPED` (company-less, cross-company read). `OPERATIONAL_MODE`
  is retained for company-scoped writes; `ADMIN_MODE` for `/api/admin`.
- New company-less read endpoints `GET /api/{authority}/{type}[/{docId}]` filtered by
  `authority_environment_id` only, **without** `@RequiresPermission`. `TenantFilter` allows
  `AUTHORITY_SCOPED` to reach them.
- Writes keep `/api/companies/{companyId}/...`; permission is evaluated against the **path**
  company so a company-less session can still write where permitted.
- `SessionContextAssembler` returns `activeCompanyId = null` plus a `writableCompanies` list for
  the create-form picker.

## Consequences

### Positive
- Matches the user's mental model: authority+environment is the only scope to choose.
- No company-switching friction; ingested documents are visible without first registering or
  selecting their company.
- Dashboard and submission log give a true cross-company view per authority+environment.

### Negative / risks
- **Loss of per-company read isolation.** Every authenticated user in an authority+environment
  can read **every** company's documents, including amounts, parties, and submission details.
  This is the core trade-off and must be acceptable for the deployment's data sensitivity.
- **Write-path refactor risk.** Services currently read the owning company from `TenantContext`,
  which is now `null` for read-scoped sessions. Each write path must switch to the explicit
  selected company or risk a null `company_id` on create. (Grep every `*Service` for
  `TenantContext.getCompanyId()` and triage read vs. write.)
- **Detail responses must expose `companyId`** so edit/submit can target the right company path.
- **Dead/!partial plumbing**: `switch-company`, parts of `ADMIN_MODE`, and the operational-mode
  guard may become partly redundant; clean up to avoid confusion.
- **Test churn**: `TenantIsolationIT` and related tests encode the isolation guarantee being
  removed and must be rewritten deliberately, not just made to pass.

### Mitigations
- Keep an **audit-log entry for cross-company reads** (at least at list/detail granularity) so
  visibility is traceable even though it is no longer restricted.
- Preserve **authority+environment isolation** strictly — it is now the only hard boundary;
  cross-context leakage (012 FR-013) remains forbidden.
- Land spec edits (`spec-edits-companyless-login.md`) in the **same PR** as the code so the
  documented contract never lags the implementation.

## Supersedes / amends

- `002` **FR-001** — read portion ("users can only access data for companies they are assigned
  to") no longer holds for operational document reads; ownership and write restriction remain.
- `002` **US2 / US3** — company-switching and per-company environment gating for reads.
- `002` **FR-005, FR-016** — environment selection at login; no company switcher.
- `012` **FR-010 / FR-011a / FR-013** — per-company read scoping of the dashboard and submission log.

## Out of scope (unchanged)

- Per-company isolation of **administrative/config** data (company/branch/authority config),
  items, and customers.
- `/api/admin/**` and `ADMIN_MODE`.
- ERP ingestion gateway (`/api/integration/v1/...`).

## Status / next steps

1. Approve this ADR (flip status to **Accepted**).
2. Apply `spec-edits-companyless-login.md` to 002 + 012.
3. Implement per `glm5-handoff-companyless-login.md` (suggested order in its §10).
4. Rewrite isolation tests; add cross-company read + permissioned-write tests.

---

*Recorded from a read-only analysis on 2026-06-02. No spec or source files were modified by this ADR.*
