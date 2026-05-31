# Phase 0 — Research: Wave 5 Foundation Refactoring

**Branch**: `006-wave5-foundation-refactor` | **Date**: 2026-05-02

## Purpose

This document resolves all NEEDS CLARIFICATION items from `plan.md` (none remained after `/speckit.clarify`) and consolidates the load-bearing technical decisions, their rationale, and rejected alternatives. The two items the clarification report flagged as **Outstanding / Deferred** — performance SCs and admin-action audit scope — are decided here.

---

## Decision 1 — Token format & lifetime mechanism

**Decision**: Continue using the existing JJWT-based **HS256-signed JWT** issued at `POST /api/auth/login` with a single ~8-hour TTL and **no refresh endpoint**. Logout is server-acknowledged but stateless (token discarded by client; expired tokens are rejected naturally).

**Rationale**:
- Spec Clarification 2 (Q2) explicitly chose long-lived single-token over refresh-pair patterns.
- The existing JJWT stack from Wave 1 already produces and validates HS256 tokens; no need to introduce a new library.
- A token blocklist would be required to add force-logout — explicitly out of scope (FR-031a).

**Alternatives considered**:
- Refresh-token pair (~1h access + ~8h refresh sliding): rejected by user in Q2 (Option C).
- Server-side session store (Redis or DB-backed): rejected — adds infrastructure for no Wave 5 benefit; reconsider in a future auth-hardening wave alongside MFA/SSO.
- Asymmetric (RS256) JWTs: not needed — single backend issues and validates; symmetric secret in `application.yml` is sufficient and already in use.

**Token claim shape**: `sub` = userId (UUID string); custom claims = `email`, `isSuperUser`, `authority`, `environment`, `authorityEnvironmentId`, `companyId` (nullable), `mode` (`ADMIN_MODE` | `OPERATIONAL_MODE`). **No permissions claim** (FR-022, Constitution XV.8).

---

## Decision 2 — Permission resolution strategy

**Decision**: Permissions are resolved **per request** by `PermissionService` joining the user's session-token claims (companyId, authorityEnvironmentId) against `user_company_transaction_roles` + `transaction_role_permissions`. A **per-request** cache (`PermissionCache`, scoped to the request thread) prevents repeated joins within a single request. **No cross-request cache** is introduced in Wave 5.

**Rationale**:
- Spec Clarification 1 (Q1) chose Option A (next-login pickup, existing sessions valid until expiry); a long cross-request cache would extend exposure beyond what was chosen, and a short cache adds complexity for marginal benefit at expected scale (≤50 users).
- Constitution XV.8 mandates `/api/session/context` as the authoritative source for the UI; the same join logic populates that response, ensuring UI/backend parity (FR-027 / FR-028 / SC-004).
- Per-request caching eliminates N+1 risk inside `@RequiresPermission` AOP without introducing cache-invalidation concerns.

**Alternatives considered**:
- Embed permissions in JWT: forbidden by FR-022 and Constitution XV.8 — rejected.
- Caffeine cross-request cache with short TTL (~30s): rejected; insufficient benefit at Wave 5 scale, and conflicts with Q1's "next login" semantics if permissions are later re-loaded mid-token.
- Pre-compute all permissions on login and stash in a server-side session: rejected — adds session store; provides no measurable speedup for ≤50 users.

---

## Decision 3 — Admin-action audit scope (SUPERSEDED — deferred to Wave 7)

> **2026-05-02 update (analysis refinement)**: This decision is **withdrawn for Wave 5**. The `audit_logs` table is dropped by V37 and not recreated until Wave 7's V51, so any audit recording in Wave 5 has no storage to land in. All audit work — admin-mutation rows, login success/failure rows, audit-trail verification, the audit table below — is moved to Wave 7 and will be re-derived at that point against the freshly-created `audit_logs` schema. The text below is retained for historical reference only and MUST NOT be implemented in Wave 5.
>
> Wave-5-era impact: there is no audit trail for admin actions or login attempts during the Wave 5 window. This is an accepted risk per spec.md Assumptions.

### Original (now-superseded) Decision 3 — Admin-action audit scope

**Decision**: Every successful **state-changing** call to `/api/admin/**` produces one row in the existing `audit_logs` table. Capture set:

| Action | `action` value | `entity_type` | `entity_id` | `payload_before` | `payload_after` |
|--------|----------------|---------------|-------------|------------------|-----------------|
| Create company | `ADMIN_COMPANY_CREATE` | `company` | new id | `null` | full DTO |
| Update company | `ADMIN_COMPANY_UPDATE` | `company` | id | prior DTO | new DTO |
| Deactivate company | `ADMIN_COMPANY_DEACTIVATE` | `company` | id | `{is_active:true}` | `{is_active:false}` |
| Create branch | `ADMIN_BRANCH_CREATE` | `branch` | new id | `null` | full DTO |
| Update branch | `ADMIN_BRANCH_UPDATE` | `branch` | id | prior DTO | new DTO |
| Create user | `ADMIN_USER_CREATE` | `user` | new id | `null` | DTO **without `password_hash`** |
| Update user | `ADMIN_USER_UPDATE` | `user` | id | prior DTO (no hash) | new DTO (no hash) |
| Activate / deactivate user | `ADMIN_USER_ACTIVATE` / `ADMIN_USER_DEACTIVATE` | `user` | id | `{is_active:!new}` | `{is_active:new}` |
| Grant assignment | `ADMIN_ASSIGNMENT_CREATE` | `user_company_transaction_role` | new id | `null` | full DTO |
| Revoke assignment | `ADMIN_ASSIGNMENT_DELETE` | `user_company_transaction_role` | id | full DTO | `null` |
| **Login success** | `AUTH_LOGIN_SUCCESS` | `user` | id | `null` | `{authority, environment, companyId, mode}` |
| **Login failure** | `AUTH_LOGIN_FAILURE` | `user` (or `null` if email unknown) | matched user id (or `null`) | `null` | `{authority, environment, errorCode}` |

Read endpoints (`GET /api/admin/**`) are **not** audited. Failed authorization (`403`) is **not** audited (no useful security signal at Wave 5 — existing JWT filter logs it).

**Rationale**:
- Constitution IX.2 enumerates what must be auditable: "credential updates, user access changes" — this set covers all of those.
- Capturing both `payload_before` and `payload_after` enables forensic reconstruction without a full event-sourcing system.
- Excluding `password_hash` from audit payloads prevents secret leakage into log dumps (Constitution VI.3).
- Login success/failure is recorded for compliance (correlates with Q3's deferred lockout work — gives visibility now even though enforcement comes later).

**Alternatives considered**:
- Audit every API call (read + write): rejected — noisy; reads are scoped by tenant context already (Constitution II) and add no audit value.
- Audit only writes; skip login: rejected — login telemetry is the only signal we have for the brute-force exposure accepted in Q3.
- Use a separate `admin_audit_logs` table: rejected — `audit_logs` already exists with the right shape (`action`, `entity_type`, `entity_id`, JSONB payloads); adding a parallel table fragments the audit trail.

---

## Decision 4 — Performance success criteria (was Outstanding)

**Decision**: Add the following targets to the plan's **Performance Goals** (already done) and validate them in the Wave 5 `quickstart.md` smoke run:

| Endpoint / scenario | p95 target | Conditions |
|---|---|---|
| `POST /api/auth/environments` | < 200 ms | Warm cache; 1 user concurrent |
| `POST /api/auth/companies` | < 300 ms | User with up to 50 assigned companies |
| `POST /api/auth/login` | < 500 ms | BCrypt cost factor 10 (existing default) |
| `GET /api/session/context` | < 300 ms | User with up to 50 assigned companies |
| Dashboard initial render (Angular) | < 1 s | 50 company cards |
| Admin list endpoints (companies/users/assignments) | < 500 ms | 200 rows |

**Rationale**:
- Wave 5 has no document throughput component; targets focus on perceived UX latency on the critical login + first-screen path.
- BCrypt-bound login latency (~250–400ms at cost factor 10) is the dominant term; bumping the cost factor is deferred to the auth-hardening wave (Q3 deferral).
- These thresholds are intentionally generous and selected so the existing single-PostgreSQL Docker setup meets them without tuning. They serve as regression triggers, not optimization targets.

**Alternatives considered**:
- Tighter targets (e.g., login < 200ms): rejected — would require BCrypt cost reduction, weakening posture without a measured user benefit.
- No formal targets in Wave 5: rejected — a measurable target is needed to satisfy the Outstanding clarification flag and to give the test plan a concrete check.

---

## Decision 5 — Dropping `lov_contexts` and superseding Wave 0–4 RBAC code

**Decision**: V37 drops every Wave 0–4 operational table per the implementation plan, **including** `lov_contexts`, `user_environment_permissions`, `user_context_permissions`, and `user_company_roles`. The corresponding Java classes (`LovContextMapper`, `LovContextMappingProviderImpl`, `LovContextResponseFilter`) are **deleted** rather than retained as no-ops. `RolePermissions` and `PermissionService` are **rewritten** rather than extended.

**Rationale**:
- The platform has no live customer data (per spec Assumptions and FR-048).
- Constitution v2.0.0 supersedes the old 3-LOV / `lov_contexts` model; keeping shadow code invites accidental re-introduction (and CLAUDE.md guidance prohibits backwards-compat shims).
- Deletion makes intent obvious in code review and removes search hits for terms (`lovContext`) that no longer exist conceptually.

**Alternatives considered**:
- Keep `lov_contexts` table empty as a placeholder: rejected — V37 explicitly drops it (implementation plan §5.1).
- Mark old classes `@Deprecated` and remove in next wave: rejected — half-finished migrations violate CLAUDE.md guidance ("No half-finished implementations").

---

## Decision 6 — Tax-number uniqueness enforcement point

**Decision**: Per Q4, uniqueness is scoped per `authority_environment_id`. Since `companies` has no `authority_environment_id` column (it is a global entity per Constitution II.2), uniqueness is checked **at the moment a company is associated with an authority+environment** — practically, when:
1. A user assignment is created via `POST /api/admin/users/{id}/assignments` for `(companyId, authorityEnvironmentId)`, OR
2. A certificate config is created for that pair (deferred to Wave 6).

In both cases, the service computes the set of *active* companies that already have at least one row binding them to the same `authority_environment_id` and rejects if any of them shares the new company's tax number.

**Rationale**:
- This is the only consistent interpretation given that `companies.tax_number` is a global field and `authority_environment_id` lives on `user_company_transaction_roles` (and later `*_configs`).
- It allows the same legal entity to be onboarded against multiple (authority, environment) pairs but prevents two distinct companies from claiming the same tax number within one pair — exactly what Q4 selected.

**Alternatives considered**:
- Add an `authority_environment_id` array/junction column to `companies`: rejected — companies are global by Constitution II.2; junction-table approach would re-derive `user_company_transaction_roles` semantics.
- Validate at company create only (not at assignment): rejected — at create time the company is not yet bound to any (authority, environment); validation has no scope to operate against.

---

## Decision 7 — Last-Super-User enforcement layer

**Decision**: Enforce the FR-036a invariant ("at least one active Super User must always exist") inside `AdminUserService` immediately before persistence. The check runs in the same transaction as the mutation:

```text
begin tx
  if mutation would set is_super_user=false OR is_active=false on a user where is_super_user=true:
    if SELECT COUNT(*) FROM users WHERE is_super_user=true AND is_active=true AND id != :targetId  == 0:
      throw LastSuperUserProtectedException → HTTP 409 LAST_SUPER_USER_PROTECTED
  apply mutation
commit
```

**Updated 2026-05-02 (post-refinement clarification Q4)**: The original "READ COMMITTED is sufficient" rationale was incorrect — under READ COMMITTED two concurrent demotions targeting *different* Super Users can each read `count = 2`, both pass the check, and both commit, leaving zero. The pinned design is now:

```text
begin tx
  -- (1) Application-wide gate: serializes ANY two SU-affecting mutations.
  perform pg_advisory_xact_lock(SUPER_USER_INVARIANT_KEY);    -- fixed bigint
  -- (2) Row-lock the target so same-target races also serialize cleanly.
  select 1 from users where id = :targetId for update;
  -- (3) Re-read count against the now-locked state.
  if mutation would set is_super_user=false OR is_active=false on a user
     where is_super_user=true (post-mutation):
    if SELECT COUNT(*) FROM users
       WHERE is_super_user=true AND is_active=true AND id != :targetId == 0:
      throw LastSuperUserProtectedException → HTTP 409 LAST_SUPER_USER_PROTECTED
  apply mutation
commit  -- advisory lock released automatically
```

`SUPER_USER_INVARIANT_KEY` is a fixed `bigint` constant (e.g., a stable hash of the literal string `"super_user_invariant"`) defined alongside the service.

**Rationale**:
- Advisory locks scoped to the transaction (`pg_advisory_xact_lock`, NOT `pg_advisory_lock`) auto-release on commit/rollback — no session-leak risk.
- The lock is application-defined and does not block any unrelated workload (no contention with normal user CRUD reading/writing the `users` table).
- Serializes both same-target races and cross-target races (two Super Users demoting each other), closing the gap the original Decision 7 left open.
- Doing this in the service layer (not via DB constraint) gives a clean error code/message; a DB CHECK across rows is awkward in PostgreSQL and harder to maintain.

**Alternatives considered**:
- DB-level trigger: rejected — error reporting via SQLSTATE is harder to map to a stable HTTP error code; less testable.
- UI-only block: rejected by Q5 (Option D explicitly excluded by user choosing B).

---

## Decision 8 — Frontend permission directive contract

**Decision**: Introduce `*appHasPermission="['module', 'action']"` (e.g., `*appHasPermission="['invoice','CREATE']"`) backed by `PermissionService.hasPermission(module, action)` which reads the active company's permissions block from `SessionContextService`. The directive removes the host element from the DOM when the permission is absent (no `hidden`, no disabled-but-visible).

**Rationale**:
- Constitution XIV.6 mandates action-button gating from the session context; a directive consolidates the check pattern and makes audits (search-for-`*appHasPermission`) feasible.
- Removing rather than disabling avoids the common mistake of users clicking a disabled-but-displayed button and feeding a 403 back through error toasts.

**Alternatives considered**:
- Function call in template (`*ngIf="canCreate('invoice')"`): rejected — boilerplate per call site and no audit grep target.
- Decorator/route guard only (no per-element directive): rejected — buttons inside list rows can't be route-guarded; per-element gating is required by FR-043.

---

## Decision 9 — Backend permission AOP

**Decision**: Keep the existing `@RequiresPermission` annotation and `PermissionAspect` but **rewrite** the aspect to:
1. Read `TenantContext` for `companyId` and `authorityEnvironmentId`.
2. If `mode == ADMIN_MODE` and the annotation is on a non-admin endpoint → reject with `COMPANY_CONTEXT_REQUIRED`.
3. Resolve the user's permission set for `(companyId, authorityEnvironmentId, transactionType)` through `PermissionService` (uses per-request `PermissionCache`).
4. Reject with `403 FORBIDDEN` (and audit-skipped per Decision 3) when the required permission is absent.
5. Bypass all checks when `TenantContext.isSuperUser == true` AND `mode == OPERATIONAL_MODE` (per Constitution XVII.3).

**Rationale**:
- Reuses an existing annotation surface, minimizing controller-method churn.
- Keeps backend permission checks centralized at one decision point, satisfying SC-004's UI/backend parity requirement.

**Alternatives considered**:
- Spring Security method-security with SpEL: rejected — SpEL expressions encoding `(module, action, tenant)` are harder to read than a typed annotation argument and don't compose well with `TenantContext`.
- Filter-based permission check at URL level: rejected — too coarse for action-level granularity.

---

## Decision 10 — Bootstrap Super User

**Decision**: A Flyway repeatable migration (`R__bootstrap_super_user.sql`, runs idempotently after V40) inserts a single bootstrap Super User if none exists, with email and a deployment-time-provided BCrypt password hash sourced from environment variable `BOOTSTRAP_SUPERUSER_EMAIL` and `BOOTSTRAP_SUPERUSER_PASSWORD_HASH`. If both env vars are unset, the migration logs a warning and inserts no row (intended for CI / test environments where a Super User is created via test fixtures instead).

**Rationale**:
- Spec Assumptions explicitly note "Bootstrapping the very first Super User is handled by deployment seeding (out of band of the login flow)."
- A repeatable migration with idempotent insert (`ON CONFLICT DO NOTHING` keyed on email) means re-running is safe.
- The hash (not the plaintext password) is supplied externally so the password never touches application logs or `application.yml`.

**Alternatives considered**:
- Bake an admin/admin default into V40 seed data: rejected — security smell; would require Day-2 password rotation flow that doesn't yet exist.
- CLI bootstrap command: rejected — adds a new operational tool for a single use case; the env-var migration achieves the same with one-line documentation.

---

## Open items handed to `/speckit.tasks`

None — all NEEDS CLARIFICATION resolved before Phase 1.
