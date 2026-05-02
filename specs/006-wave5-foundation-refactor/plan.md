# Implementation Plan: Wave 5 — Foundation Refactoring (Auth, Session, Global Entities & RBAC)

**Branch**: `006-wave5-foundation-refactor` | **Date**: 2026-05-02 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/006-wave5-foundation-refactor/spec.md`

## Summary

Wave 5 replaces the platform's current 3-LOV login + per-context tenancy with a 2-LOV (Authority + Environment) login flow, post-login company selection, and an Admin Mode / Operational Mode separation. It introduces a fresh global-vs-operational two-tier entity model: **Companies, Branches, Users** become global (no `authority_environment_id`), while everything else is keyed by `(company_id, authority_environment_id)`. A new 3-table RBAC model (`transaction_roles`, `transaction_role_permissions`, `user_company_transaction_roles`) replaces all prior role wiring; permissions are loaded per-request via a session-context API and never embedded in the JWT.

This is a **fresh-database refactor**: deployment drops every Wave 0–4 operational table and rebuilds the schema starting at Flyway V37. No data migration is required because the platform has not yet been released to live customers (per FR-048, FR-049, and Constitution v2.0.0).

Five clarifications recorded in `spec.md` shape the implementation: (1) revoked access only takes effect at next login, (2) ~8h single-token sessions with no refresh endpoint, (3) no password complexity / lockout in Wave 5, (4) tax-number uniqueness scoped per `authority_environment_id`, (5) backend-enforced "at least one active Super User" invariant.

## Technical Context

**Language/Version**: Java 17 (backend), TypeScript 5.x (frontend, Angular 19)
**Primary Dependencies**:
- Backend: Spring Boot 3.4.4 (web, data-jpa, security, validation), Spring Data JPA + Flyway (PostgreSQL 16), Lombok, Jackson, BouncyCastle (already present from earlier waves), JJWT (existing JWT stack from Wave 1).
- Frontend: Angular 19, Angular Material, RxJS, the existing `auth.interceptor`, `auth.service`, and `SessionContextService` skeleton.
- No new third-party libraries are introduced by Wave 5.
**Storage**: PostgreSQL 16 (single instance via Docker Compose). Flyway migrations V37–V43.
**Testing**:
- Backend: JUnit 5 + Spring Boot Test, Mockito; integration tests use Testcontainers PostgreSQL (existing pattern); golden-file tests not required for Wave 5 (no payload generation introduced here).
- Frontend: Jest (existing config) for components/services.
**Target Platform**: On-prem Linux server (Docker Compose), browser (Chromium-based for UAT). Constitution Principle XIX.
**Project Type**: Multi-module Maven backend + Angular frontend (web application). Existing layout: `platform-api`, `platform-core`, `platform-security`, `platform-eta`, `platform-zatca`, `platform-jobs`, `platform-pdf`, `frontend/`.
**Performance Goals**:
- Login (each step) under 500ms p95 in nominal conditions.
- `GET /api/session/context` under 300ms p95 for users with up to 50 assigned companies.
- Company-card dashboard initial load under 1s p95 with up to 50 cards.
- (Constitution Principle XXV: document list responses use compound `(company_id, authority_environment_id)` indexes — Wave 5 establishes the global tables; the operational compound indexes are added in Wave 6+.)
**Constraints**:
- Token TTL fixed at ~8h (per spec Clarification 2 and FR-023a). No refresh endpoint.
- Permissions MUST NOT be embedded in JWT (FR-022, Constitution XV.8).
- All operational APIs reject `mode = ADMIN_MODE` with `COMPANY_CONTEXT_REQUIRED` (FR-030, Constitution VII.4).
- `at-least-one-active-Super-User` invariant enforced in admin layer (FR-036a).
- Plain-text storage for any secrets (Constitution XVIII) — applies to user `password_hash` storage, but password hashing itself uses BCrypt (existing).
**Scale/Scope**:
- Initial deployments: 1 Super User, 5–20 regular users, 5–50 companies, 1–5 branches per company, ~10 modules per session-context payload.
- 49 functional requirements across 8 groups; 5 user stories (2× P1, 2× P2, 1× P3); 10 success criteria.
- 7 Flyway migrations (V37–V43), ~12 new backend domain entities/services, ~8 new REST endpoints, ~6 Angular screens (login, dashboard shell, admin: companies/branches/users/assignments/list+forms).

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

Wave 5 is the constitution-aligning refactor itself, so most principles map directly to in-scope work. Each principle below is evaluated for whether the plan respects it.

### Pre-Phase-0 Evaluation

| # | Principle | Status | Notes |
|---|-----------|--------|-------|
| I | Compliance-First | N/A | No authority document generation in Wave 5 |
| II | Multi-Tenant Isolation | ✅ Pass | Plan honors Global vs Operational tiers; isolation key `(company_id, authority_environment_id)` is established as a foundation for Wave 6+ |
| III | Authority Environment Architecture | ✅ Pass | V37 seeds 5-row `authority_environments`; JWT carries `authority_environment_id`; mid-session switching forbidden (logout required) |
| IV | Branch + Environment Segregation | ✅ Pass | Branches modeled as global under company; per-environment certificate configs are deferred to Wave 6 (not violated by Wave 5) |
| V | Stateless Service | ✅ Pass | TenantContext via ThreadLocal per request; no in-memory tenant state |
| VI | Server-Side Cryptography | ✅ Pass | Wave 5 introduces no client-side crypto; password hashing server-side with BCrypt (existing) |
| VII | Admin Mode / Operational Mode Separation | ✅ Pass | First-class in this wave (FR-019, FR-020, FR-030, FR-045) |
| VIII | Transaction Module Architecture | ✅ Pass | Sidebar driven by session-context permissions per module per authority (FR-026, FR-043) |
| IX | Immutable Audit | ⏭ Deferred to Wave 7 | The `audit_logs` table is dropped by V37 (per implementation-plan §5.1) and recreated by V51 in Wave 7. Wave 5 introduces no audit functionality; Constitution IX applies to Wave 7+. Decision logged in spec.md Clarifications (C1) and Assumptions. |
| X | Deterministic Document Lifecycle | N/A | No document lifecycle in Wave 5 |
| XI | Physical Document Table Strategy | N/A | No document tables in Wave 5 |
| XII | ZATCA Hash Chain Integrity | N/A | No ZATCA chain work in Wave 5 |
| XIII | Validation Layering | ✅ Pass | Wave 5 validations live in domain services (login, admin), with thin Angular reactive-form guidance |
| XIV | Angular Engineering | ✅ Pass | Reactive forms for login + admin; header chips; permission-driven sidebar; permission directive (FR-027, FR-041, FR-042, FR-043) |
| XV | Spring Boot Engineering | ✅ Pass (with deferred sub-clause) | Layered controllers→services→repos; Flyway V37+; TenantContext fields per spec; session-context API authoritative. **XV.7 sub-clause** (every operational repository query MUST filter by `(company_id, authority_environment_id)`) is a **Wave 6 entry-criterion** — Wave 5 has no operational repositories; the enforcement scaffold (base class / aspect) ships with the first operational repos in Wave 6. Tracked in spec.md Clarifications (U1) and FR-029. |
| XVI | Authority Adapter | N/A | No authority engine changes in Wave 5 |
| XVII | RBAC and Access Control | ✅ Pass | 3-table model implemented exactly per Constitution XVII.2; Super User flag bypasses assignment requirement; default role permission sets per XVII.8 |
| XVIII | Plain-Text Certificate Storage Policy | ✅ Pass | Wave 5 introduces no certificates (Wave 6); user passwords still hashed with BCrypt (not in scope of XVIII) |
| XIX | Secure On-Prem Deployment | ✅ Pass | No deployment-topology changes |
| XX | Reliability and Recovery | ✅ Pass | No long-running jobs in Wave 5 |
| XXI | Document Preservation | N/A | Not applicable to Wave 5 |
| XXII | Signed License Enforcement (Phase 2) | ✅ Pass | "Create Company" remains always-enabled in Phase 1 (FR-047 + Wave 5 implementation-plan note) |
| XXIII | Change Control | ✅ Pass | Plan + spec + tasks chain provides traceability |
| XXIV | Testing | ✅ Pass | Cross-context isolation tests added (SC-003, see quickstart.md scenarios); auth-flow integration tests; admin-API permission tests |
| XXV | Performance | ✅ Pass | Performance Goals section above sets targets; Wave 6+ adds the operational compound indexes called out in XXV.2 |
| XXVI | Source of Truth | ✅ Pass | spec.md → plan.md → tasks.md chain maintained |

**Gate Result**: ✅ PASS — no violations to justify.

### Post-Phase-1 Re-Evaluation

Re-evaluation after `data-model.md` and `contracts/` were generated:
- All entities introduced in `data-model.md` map cleanly to the Global vs Operational tier model (Constitution II.2). No operational entity is missing `authority_environment_id`; no global entity inadvertently carries one.
- `user_company_transaction_roles` matches Constitution XVII.2 verbatim; `transaction_role_permissions` matches the action set in XVII.6 / XVII.7.
- The session-context contract returns the permissions object the Angular shell needs to satisfy XIV.6 (action-button gating); the same authority-aware module key set required by XIV.4 is preserved.
- The `at-least-one-active-Super-User` invariant (FR-036a) is enforced in `AdminUserService` before persistence (no race risk in single-DB Phase 1).
- No new principle violations detected. **Gate Result**: ✅ PASS.

## Project Structure

### Documentation (this feature)

```text
specs/006-wave5-foundation-refactor/
├── plan.md                                # This file (/speckit.plan output)
├── research.md                            # Phase 0 output
├── data-model.md                          # Phase 1 output (entities + relationships)
├── quickstart.md                          # Phase 1 output (smoke-test runbook)
├── contracts/
│   ├── auth-api.openapi.yaml              # /api/auth/* + /api/session/context
│   ├── admin-api.openapi.yaml             # /api/admin/*
│   └── error-codes.md                     # Catalogue of error codes used by both
├── checklists/
│   └── requirements.md                    # Already created during /speckit.specify
└── tasks.md                               # Will be created by /speckit.tasks
```

### Source Code (repository root)

Existing multi-module Maven layout — Wave 5 modifies the modules below. No new modules introduced.

```text
backend/  (multi-module Maven; rooted at repo root, not under /backend)
├── platform-core/
│   └── src/main/resources/db/migration/
│       ├── V37__wave5_prelude_drop_and_authority_environments.sql
│       ├── V38__create_companies.sql
│       ├── V39__create_branches.sql
│       ├── V40__create_users.sql
│       ├── V41__create_transaction_roles.sql
│       ├── V42__create_transaction_role_permissions.sql
│       └── V43__create_user_company_transaction_roles.sql
│
├── platform-security/
│   └── src/main/java/com/einvoice/security/
│       ├── tenant/
│       │   ├── TenantContext.java                    # REWRITE per spec §5.2
│       │   ├── TenantFilter.java                     # UPDATE: read auth+env+mode from JWT
│       │   └── LovContextResponseFilter.java         # DELETE (superseded)
│       ├── jwt/
│       │   ├── JwtTokenProvider.java                 # UPDATE: emit new claim set
│       │   └── JwtProperties.java                    # UPDATE: TTL → 8h
│       ├── permission/
│       │   ├── PermissionAspect.java                 # UPDATE: check via session-context
│       │   ├── PermissionService.java                # REWRITE: new RBAC source
│       │   ├── PermissionCache.java                  # KEEP (per-request)
│       │   ├── LovContextMapper.java                 # DELETE
│       │   └── LovContextMappingProviderImpl.java    # DELETE
│       ├── rbac/
│       │   └── RolePermissions.java                  # REWRITE: new 8-action model
│       └── SecurityConfig.java                       # UPDATE: route admin endpoints
│
├── platform-api/
│   └── src/main/java/com/einvoice/api/
│       ├── auth/
│       │   ├── AuthController.java                   # /api/auth/{environments|companies|login|logout}
│       │   ├── AuthService.java
│       │   └── dto/                                  # LoginRequest, LoginResponse, etc.
│       ├── session/
│       │   ├── SessionContextController.java         # GET /api/session/context
│       │   ├── SessionContextAssembler.java          # Builds permissions from RBAC tables
│       │   └── dto/SessionContextResponse.java
│       └── admin/
│           ├── AdminCompanyController.java
│           ├── AdminBranchController.java
│           ├── AdminUserController.java
│           ├── AdminAssignmentController.java
│           ├── service/{AdminCompanyService, AdminBranchService, AdminUserService, AdminAssignmentService}.java
│           └── dto/...
│
├── platform-core/
│   └── src/main/java/com/einvoice/core/
│       ├── domain/
│       │   ├── company/{Company, CompanyRepository}.java
│       │   ├── branch/{Branch, BranchRepository}.java
│       │   ├── user/{User, UserRepository}.java
│       │   └── rbac/
│       │       ├── AuthorityEnvironment.java
│       │       ├── TransactionRole.java
│       │       ├── TransactionRolePermission.java
│       │       └── UserCompanyTransactionRole.java + repos
│       └── audit/                                    # existing — extended for admin actions
│
└── (platform-eta, platform-zatca, platform-jobs, platform-pdf untouched in Wave 5)

frontend/
└── src/app/
    ├── auth/
    │   ├── auth.component.{ts,html}                  # REWRITE: 4-step cascading login
    │   ├── auth.service.ts                           # UPDATE: new login DTO + endpoints
    │   └── auth.interceptor.ts                       # UPDATE: handle 401 → redirect
    ├── shared/services/
    │   ├── session-context.service.ts                # REWRITE: GET /api/session/context
    │   ├── permission.service.ts                     # NEW: hasPermission(module, action)
    │   └── admin.service.ts                          # REWRITE for new admin APIs
    ├── shared/directives/
    │   └── has-permission.directive.ts               # NEW: *appHasPermission="'CREATE'"
    ├── layout/header/
    │   └── header.component.{ts,html,scss}           # UPDATE: chips for auth/env/mode|company
    ├── layout/sidebar/                               # NEW (split out of header)
    │   └── sidebar.component.{ts,html}               # Driven by SessionContextService
    ├── dashboard/
    │   └── dashboard.component.{ts,html}             # NEW: company cards grid
    └── admin/
        ├── companies/{list, form}.component.{ts,html}
        ├── branches/{list, form}.component.{ts,html}
        ├── users/{list, form}.component.{ts,html}
        └── assignments/{list, form}.component.{ts,html}

tests/  (existing layout retained)
├── (backend tests live alongside production sources under each platform-* module's src/test)
└── frontend Jest specs live alongside their source files
```

**Structure Decision**: Existing multi-module Maven backend (no new modules) + existing Angular workspace (one new `admin/` feature folder + minor reorganization splitting sidebar out of header). All Wave 5 backend work fits inside three modules: `platform-core` (entities + Flyway), `platform-security` (TenantContext, JWT, RBAC), and `platform-api` (REST layer). The deletions in `platform-security/permission/` (LovContextMapper, LovContextMappingProviderImpl, LovContextResponseFilter) reflect the constitutional shift from `lov_contexts` to `authority_environments` per Constitution v2.0.0.

## Complexity Tracking

> No Constitution Check violations. This section is intentionally empty for Wave 5.

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| _(none)_ | _(none)_ | _(none)_ |
