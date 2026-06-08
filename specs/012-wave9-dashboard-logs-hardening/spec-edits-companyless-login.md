# Draft Spec Edits — Company-less Login & Cross-Company Visibility

**Companion to:** `glm5-handoff-companyless-login.md`
**Branch:** `012-wave9-dashboard-logs-hardening`
**Status:** PROPOSED — not yet applied to spec files. Apply alongside (or just before) the implementation.
**Date:** 2026-06-02

These are the surgical edits required so the specs stop contradicting the new direction
(decisions D1–D5 in the handoff). Each block gives the **exact current text** and the
**proposed replacement**. Line numbers are as of 2026-06-02 and may drift — match on text.

> **Governing principle for all edits below:** separate **read scope** from **write scope / data ownership**.
> - **Reads** (lists, detail, dashboard, logs) are scoped to the active **authority + environment** and span **all companies** — visible to any authenticated user.
> - **Writes** and **data ownership** remain **per company**: every document still belongs to one company, and create/edit/submit/cancel/retry/delete still require that company's permission.

---

## A. `specs/002-platform-foundation-tenancy/spec.md`

### A1 — FR-001 (data isolation) — line ~168

**Before**
```
- **FR-001**: System MUST support multi-tenant data isolation — each company's data is completely separated, and users can only access data for companies they are assigned to
```

**After**
```
- **FR-001**: System MUST preserve per-company data **ownership** — every document, customer, item, and config record belongs to exactly one company. **Write** operations (create/edit/submit/cancel/retry/delete) and master-data management MUST be restricted to companies the user is assigned to with the relevant permission. **Read** access to operational documents (lists, detail, dashboard, logs) is NOT restricted per-company: any authenticated user MAY view all documents within their active authority+environment (see 012 FR-010). Administrative configuration data remains per-company isolated.
```

### A2 — User Story 2 title + narrative — lines ~28-30

**Before**
```
### User Story 2 - User Authentication and Multi-Company Switching (Priority: P1)

A user with credentials in the system logs in with their email and password. The system issues a secure session. If the user belongs to multiple companies, they can switch between companies using a dropdown in the header without logging out. After switching, they see only the selected company's data and their role adjusts accordingly.
```

**After**
```
### User Story 2 - User Authentication and Authority/Environment Selection (Priority: P1)

A user with credentials logs in with their email and password and chooses an **authority and environment** (no company selection). The system issues a secure session scoped to that authority+environment. The user can then view all documents that exist in that authority+environment across every company. When creating a document, the user selects the owning company from the companies they are permitted to write to.
```

### A3 — User Story 2 acceptance scenarios — lines ~38-39

**Before**
```
1. **Given** a user with valid credentials, **When** they submit email and password, **Then** they are authenticated and see their default company's dashboard
2. **Given** an authenticated user belonging to two companies, **When** they select the second company from the header dropdown, **Then** the interface refreshes to show only the second company's data, and their role reflects their assignment in that company
```

**After**
```
1. **Given** a user with valid credentials, **When** they submit email, password, authority, and environment, **Then** they are authenticated and see the dashboard for that authority+environment spanning all companies
2. **Given** an authenticated user, **When** they open any document list, **Then** they see documents from every company in the active authority+environment (no per-company filtering required), with an optional company filter available
```

### A4 — User Story 3 narrative — line ~47

**Before**
```
After logging in (and optionally switching companies), the user selects an active environment (e.g., ZATCA Sandbox, ETA Pre-Production). The environment selector appears as a picker after login or after company switch, and a persistent badge in the header shows which environment is active. All subsequent operations are scoped to the selected environment. Users can only select environments they have been granted permission for within their current company role.
```

**After**
```
The user selects an authority and environment (e.g., ZATCA Sandbox, ETA Pre-Production) as part of login; there is no company selection step. A persistent badge in the header shows the active authority+environment. All subsequent operations are scoped to that authority+environment. Read access spans all companies; write operations target a company the user is permitted to write to.
```

### A5 — FR-005 (environment selection) — line ~172

**Before**
```
- **FR-005**: System MUST support environment selection (ZATCA Sandbox, ZATCA Simulation, ZATCA Production, ETA Pre-Production, ETA Production) scoped per user per company role
```

**After**
```
- **FR-005**: System MUST support authority+environment selection at login (ZATCA Sandbox/Simulation/Production, ETA Pre-Production/Production). The session is scoped to the selected authority+environment. Write permissions remain defined per user per company; environment access is no longer gated per-company for read access.
```

### A6 — FR-016 (screens list) — line ~183

**Before**
```
- **FR-016**: System MUST provide screens for: login, company switching, environment selection, Super Admin dashboard, company/branch configuration, user management, customer management, item management, and draft invoice creation. UI is English-only for MVP; Arabic name fields are stored but not rendered in a localized layout
```

**After**
```
- **FR-016**: System MUST provide screens for: login (with authority+environment selection), Super Admin dashboard, company/branch configuration, user management, customer management, item management, and draft invoice creation (with owning-company selection). The header no longer provides a company switcher for read scope. UI is English-only for MVP; Arabic name fields are stored but not rendered in a localized layout
```

---

## B. `specs/002-platform-foundation-tenancy/contracts/auth-api.md`

### B1 — `POST /api/auth/login` request — lines ~7-13

**Before**
```json
{
  "email": "user@example.com",
  "password": "string"
}
```

**After**
```json
{
  "email": "user@example.com",
  "password": "string",
  "authority": "ZATCA",
  "environment": "SANDBOX"
}
```

> Note: the current implementation's `LoginRequest` already carries `authority`/`environment`
> (and an optional `companyId`). This contract simply catches up: `authority`+`environment`
> become required; `companyId` is omitted at login (it is selected later for writes).

### B2 — `POST /api/auth/login` response `user` block — lines ~22-33

**Before**
```json
  "user": {
    "id": 1,
    "name": "John Doe",
    "email": "user@example.com",
    "activeCompanyId": 1,
    "role": "COMPANY_ADMIN",
    "permittedEnvironments": ["ZATCA_SANDBOX", "ZATCA_SIMULATION"],
    "availableCompanies": [
      { "id": 1, "name": "Saudi Trading Co." },
      { "id": 2, "name": "Egypt Services LLC" }
    ]
  }
```

**After**
```json
  "user": {
    "id": 1,
    "name": "John Doe",
    "email": "user@example.com",
    "activeAuthority": "ZATCA",
    "activeEnvironment": "SANDBOX",
    "activeCompanyId": null,
    "writableCompanies": [
      { "id": 1, "name": "Saudi Trading Co." },
      { "id": 2, "name": "Egypt Services LLC" }
    ]
  }
```

> `activeCompanyId` is `null` for the read-scoped session. `writableCompanies` lists the
> companies the user may select as the owning company when **creating** a document
> (the only place a company is chosen now). Reads ignore it.

### B3 — `POST /api/auth/switch-company` — lines ~43-56

**Before**
```
## POST /api/auth/switch-company

Switch active company context. Returns new JWT.

**Request**:
\`\`\`json
{
  "companyId": 2
}
\`\`\`

**Response 200**: Same shape as login response with updated company context.

**Response 403**: User has no role in the target company.
```

**After** (mark deprecated — keep the heading so historical links resolve)
```
## POST /api/auth/switch-company  — DEPRECATED

Removed by the company-less login redesign (012 / FR-010). Read scope is no longer
per-company, so there is no active company to switch. Document creation selects the
owning company in the create form instead. This endpoint SHOULD return 410 Gone (or be
removed) once the frontend no longer calls it.
```

### B4 — `POST /api/auth/select-environment` — lines ~60-78

Add a note that environment is now chosen at login:

**Append after the existing section**
```
> Note (012 redesign): environment is selected as part of `POST /api/auth/login`.
> A standalone post-login environment switch, if retained, re-issues the session token
> for the new authority+environment and does not involve a company.
```

---

## C. `specs/012-wave9-dashboard-logs-hardening/spec.md`

> User Story 1 (P1) is currently premised on *operators seeing only their assigned companies*.
> The redesign removes that premise. Two options: (1) **rewrite US1** to the cross-company
> model (recommended), or (2) keep US1 as historical and add a superseding clarification.
> Edits below take option (1) at the requirement level and fix the contradicting scenarios/assumptions.

### C1 — User Story 1 acceptance scenario #5 — line ~38

**Before**
```
5. **Given** a company the operator is **not** assigned to, **When** the dashboard loads, **Then** that company never appears as a card and its activity never appears in the feed.
```

**After**
```
5. **Given** any company in the active authority+environment, **When** the dashboard loads, **Then** it appears as a card and its activity appears in the feed — read visibility spans all companies in the authority+environment (012 redesign / FR-010).
```

### C2 — FR-010 (submission log scope) — line ~141  ← the headline edit

**Before**
```
- **FR-010**: The submission log MUST list submission attempts drawn from the unified submission-attempt records introduced in the prior waves, scoped to the companies the viewer can access (assigned companies for an operator; all companies for a Super User) within the active authority+environment.
```

**After**
```
- **FR-010**: The submission log MUST list submission attempts drawn from the unified submission-attempt records introduced in the prior waves, scoped to the active authority+environment and spanning **all companies** in that context. Any authenticated user MAY view the full log; there is no per-company read restriction (company-less login redesign). Per-company **write** permissions are unaffected.
```

### C3 — FR-011a (multi-company column/filter) — line ~143

**Before**
```
- **FR-011a**: When the viewer can access more than one company, each submission-log row MUST identify its owning company (Company column), and the log MUST allow filtering by company (Constitution VIII.5).
```

**After**
```
- **FR-011a**: Each submission-log row MUST identify its owning company (Company column), and the log MUST allow filtering by company. (Because read scope now always spans all companies in the authority+environment, the Company column and filter are always shown.)
```

### C4 — FR-013 (no cross-context leakage) — line ~145

**Before**
```
- **FR-013**: The submission log MUST never display attempts belonging to companies the viewer cannot access, or to other authority+environment contexts.
```

**After**
```
- **FR-013**: The submission log MUST never display attempts belonging to **other authority+environment contexts**. Within the active authority+environment, all companies' attempts are visible (company-less read scope).
```

### C5 — Assumption bullet (KPIs/feed scope) — line ~205

**Before**
```
- The recent-activity feed and the today/this-month KPIs are bounded to the active authority+environment and to the companies the viewer can access (assigned companies for operators, all companies for Super Users / Admin Mode).
```

**After**
```
- The recent-activity feed and the today/this-month KPIs are bounded to the active authority+environment and span all companies in that context (company-less read scope); they are not filtered by per-user company assignment.
```

### C6 — FR-009 (Admin Mode stats) — line ~137 (light touch)

`FR-009` still holds (system stats per authority+environment). Optionally drop the
"Admin Mode" qualifier since the cross-company view is now the default for everyone:

**Before**
```
- **FR-009**: The Admin Mode dashboard MUST present system statistics for the active authority+environment: total companies, total users, and total submissions today (where "today" is computed in **UTC**, per FR-004).
```

**After**
```
- **FR-009**: The dashboard MUST present system statistics for the active authority+environment: total companies, total users, and total submissions today (where "today" is computed in **UTC**, per FR-004). These are visible to any authenticated user (no Admin-Mode gating).
```

---

## D. New ADR note (recommended, create as a new file)

Create `specs/012-wave9-dashboard-logs-hardening/adr-001-companyless-read-scope.md` capturing:

- **Context:** platform was per-company isolated end to end (JWT companyId, `TenantFilter`, `PermissionAspect`, `/api/companies/{companyId}/...`).
- **Decision:** D1–D5 (login is authority+environment only; reads cross-company & ungated; writes stay per-company permissioned; owning company chosen in create form).
- **Consequences:** loss of per-company read isolation — every authenticated user sees every company's documents within an authority+environment. Accept the data-sensitivity trade-off; consider auditing cross-company reads.
- **Supersedes:** 002 FR-001 (read portion), 002 US2/US3 company-switch flow, 012 FR-010/FR-011a/FR-013 per-company read scoping.

---

## E. Apply order

1. Apply D (ADR) first so the rationale exists.
2. Apply A + B (002 foundation) and C (012) together with — or immediately before — the code change, so specs and code land consistently in the same PR.
3. Update any acceptance tests that assert the old per-company read isolation (see handoff §7).

---

*Prepared from a read-only analysis on 2026-06-02. No spec or source files were modified by this draft.*
