---
description: "Task list for Wave 5 — Foundation Refactoring: Authority+Environment Login, Admin/Operational Modes, Global Entities & RBAC"
---

# Tasks: Wave 5 — Foundation Refactoring

**Input**: Design documents from `/specs/006-wave5-foundation-refactor/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/, quickstart.md

**Tests**: REQUIRED. Constitution Principle XXIV mandates integration tests for tenant + authority-environment isolation, contract tests for auth/admin endpoints, and permission-parity tests (SC-004). Test tasks are interleaved per story.

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story. All file paths are repo-relative from `D:\Cou\Spring Course In 28 Minutes\Projects\einvoice-platform\`.

## Path Conventions (Wave 5 actual layout — multi-module Maven + Angular)

- Backend modules at repo root: `platform-core/`, `platform-security/`, `platform-api/` (others untouched).
- Frontend at `frontend/src/app/`.
- Flyway migrations at `platform-core/src/main/resources/db/migration/`.
- Test sources at `<module>/src/test/java/...` (backend) and `frontend/src/app/.../*.spec.ts` (frontend).

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Module-level wiring needed before Wave 5 implementation begins. Branch `006-wave5-foundation-refactor` already exists.

- [ ] T001 Confirm working tree clean and on branch `006-wave5-foundation-refactor`; verify `mvn -q -DskipTests verify` and `cd frontend && npm ci && npm run build` succeed against current `005-pre-final-phase-review` baseline (captures pre-refactor green state for rollback comparison).
- [ ] T002 [P] Add `JWT_TTL_SECONDS=28800`, `BOOTSTRAP_SUPERUSER_EMAIL`, `BOOTSTRAP_SUPERUSER_PASSWORD_HASH` to `docker-compose.yml` backend service env block and to `Docs/configuration-reference.md` (Decision 1 + Decision 10 in research.md).
- [ ] T003 [P] Update `frontend/angular.json` and `frontend/src/environments/environment.ts` if needed to ensure `apiBaseUrl` covers `/api/auth`, `/api/admin`, `/api/session` paths (no change expected — verify only).

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Schema, domain entities, security plumbing, and removal of superseded Wave 0–4 code. **No user story work may begin until this phase is complete.**

### 2A — Flyway migrations (V37–V43, sequential by version number)

- [ ] T004 Create `platform-core/src/main/resources/db/migration/V37__wave5_prelude_drop_and_authority_environments.sql` per implementation-plan §5.1 V37. Drops every Wave 0–4 operational table (CASCADE) and creates `authority_environments` with the 5 seed rows (Constitution III.2 table).
- [ ] T005 Create `platform-core/src/main/resources/db/migration/V38__create_companies.sql` per implementation-plan §5.1 V38. Includes `idx_companies_tax` and `idx_companies_active`. **No** unique constraint on `tax_number` (FR-007 + Decision 6).
- [ ] T006 Create `platform-core/src/main/resources/db/migration/V39__create_branches.sql` per implementation-plan §5.1 V39 with constraint `uq_branch_code (company_id, branch_code)` and `idx_branches_company`.
- [ ] T007 Create `platform-core/src/main/resources/db/migration/V40__create_users.sql` per implementation-plan §5.1 V40. Unique on `email`. `is_super_user` and `is_active` default values per spec.
- [ ] T008 Create `platform-core/src/main/resources/db/migration/V41__create_transaction_roles.sql` per implementation-plan §5.1 V41. Includes the 22-row INSERT covering ETA + ZATCA × all transaction types × roles (data-model.md §5).
- [ ] T009 Create `platform-core/src/main/resources/db/migration/V42__create_transaction_role_permissions.sql` per implementation-plan §5.1 V42. Implement the COMPANY_ADMIN/ACCOUNTANT/VIEWER permission seed using `INSERT … SELECT FROM transaction_roles JOIN VALUES …` so every role gets the correct action set per Constitution XVII.8.
- [ ] T010 Create `platform-core/src/main/resources/db/migration/V43__create_user_company_transaction_roles.sql` per implementation-plan §5.1 V43 with both indexes and the unique constraint.
- [ ] T011 Create `platform-core/src/main/resources/db/migration/R__bootstrap_super_user.sql` (repeatable). Inserts a single bootstrap Super User using `BOOTSTRAP_SUPERUSER_EMAIL` / `BOOTSTRAP_SUPERUSER_PASSWORD_HASH` env vars via Flyway placeholders; uses `INSERT … ON CONFLICT (email) DO NOTHING`. Logs a warning when env vars are missing (Decision 10).

### 2B — Delete superseded Wave 0–4 Java code

- [ ] T012 [P] Delete `platform-security/src/main/java/com/einvoice/security/permission/LovContextMapper.java` (superseded by `authority_environments` model — Decision 5).
- [ ] T013 [P] Delete `platform-security/src/main/java/com/einvoice/security/permission/LovContextMappingProviderImpl.java` and any companion interface in the same package no longer referenced.
- [ ] T014 [P] Delete `platform-security/src/main/java/com/einvoice/security/tenant/LovContextResponseFilter.java`.
- [ ] T015 Run a repo-wide grep for `LovContext`, `lov_context`, `lovContextId` and remove or rewrite remaining references in `platform-api`, `platform-eta`, `platform-zatca`, `platform-jobs` (confirms Decision 5; these modules stay untouched in Wave 5 but must compile).

### 2C — Catalogue / Global / Operational entities + repositories (data-model.md §1–7)

- [ ] T016 [P] Create JPA entity `AuthorityEnvironment` in `platform-core/src/main/java/com/einvoice/core/domain/rbac/AuthorityEnvironment.java` (immutable; `id` SMALLINT in PostgreSQL mapped to Java `Short` at the entity layer). Note: `Short` is the canonical Java type for `authorityEnvironmentId` across the entire stack (entity, TenantContext, JWT claim, DTOs, OpenAPI `integer`); see T022 for the matching TenantContext field type.
- [ ] T017 [P] Create `AuthorityEnvironmentRepository` (Spring Data) in same package with finder `findByAuthorityAndEnvironmentAndIsActiveTrue` + `findByIsActiveTrueOrderById`.
- [ ] T018 [P] Create JPA entity `Company` + `CompanyRepository` in `platform-core/src/main/java/com/einvoice/core/domain/company/`. Repository methods: `findByIsActiveTrue`, `findByTaxNumber`.
- [ ] T019 [P] Create JPA entity `Branch` + `BranchRepository` in `platform-core/src/main/java/com/einvoice/core/domain/branch/`. Repository methods: `findByCompanyIdAndIsActiveTrue`, `findByCompanyIdAndBranchCode`.
- [ ] T020 [P] Create JPA entity `User` + `UserRepository` in `platform-core/src/main/java/com/einvoice/core/domain/user/`. Repository methods: `findByEmail`, `findByIsSuperUserTrueAndIsActiveTrue` (used by FR-036a check), `countByIsSuperUserTrueAndIsActiveTrueAndIdNot(UUID)`.
- [ ] T021 [P] Create entities `TransactionRole`, `TransactionRolePermission`, `UserCompanyTransactionRole` plus their repositories under `platform-core/src/main/java/com/einvoice/core/domain/rbac/`. Required finders: `TransactionRoleRepository.findByAuthorityAndTransactionTypeAndRoleCode`, `TransactionRolePermissionRepository.findByRoleId`, `UserCompanyTransactionRoleRepository.findByUserIdAndAuthorityEnvironmentIdAndIsActiveTrue`, `findByCompanyIdAndAuthorityEnvironmentIdAndIsActiveTrue`, `findByUserIdAndCompanyIdAndAuthorityEnvironmentIdAndTransactionType`.

### 2D — Security infrastructure rewrite

- [ ] T022 Rewrite `platform-security/src/main/java/com/einvoice/security/tenant/TenantContext.java` per spec §5.2: fields `userId: UUID`, `companyId: UUID` (nullable), `authorityEnvironmentId: Short` (NOT `Integer` — match the entity-layer type from T016), `authority: String`, `environment: String`, `mode` (enum `AdminMode|OperationalMode`), `isSuperUser: boolean`. Provide `current()` accessor backed by `ThreadLocal`. Remove all permission-set fields (Constitution XV.6, FR-022). The `Short` type flows through to `JwtTokenProvider` claim-builder (T023) and to `AuthorityEnvironmentRepository` lookups (T017).
- [ ] T023 Rewrite `platform-security/src/main/java/com/einvoice/security/jwt/JwtTokenProvider.java` to emit the claim set per Decision 1 (`sub`, `email`, `isSuperUser`, `authority`, `environment`, `authorityEnvironmentId`, `companyId`, `mode`) and **never** include a `permissions` claim (FR-022). Add `JwtTokenProvider.parseToTenantContext(String token): TenantContext`.
- [ ] T024 Update `platform-security/src/main/java/com/einvoice/security/jwt/JwtProperties.java` to read `ttlSeconds` (default 28800) from `JWT_TTL_SECONDS` env var. Bounds: accept values in the inclusive range `[60, 86400]` — i.e., reject when `ttlSeconds < 60` or `ttlSeconds > 86400` at startup with a clear configuration error. Document the bounds in JavaDoc on the field. (Aligns with quickstart.md Phase H step 33 which uses `JWT_TTL_SECONDS=90` for the expiry test.)
- [ ] T025 Update `platform-security/src/main/java/com/einvoice/security/jwt/JwtAuthenticationFilter.java` to construct `TenantContext` from the parsed JWT and set the thread-local; clear in `finally`.
- [ ] T026 Update `platform-security/src/main/java/com/einvoice/security/tenant/TenantFilter.java` to handle `mode == ADMIN_MODE`: if request path starts with `/api/admin/**`, allow; if `/api/auth/**` or `/api/session/context`, allow; for everything else, short-circuit with `403 COMPANY_CONTEXT_REQUIRED` (FR-030, INV-04).
- [ ] T027 Rewrite `platform-security/src/main/java/com/einvoice/security/permission/PermissionService.java` per Decision 2. Methods: `Set<String> permissionsFor(UUID userId, UUID companyId, short authEnvId, String transactionType)` and `boolean hasPermission(UUID userId, UUID companyId, short authEnvId, String transactionType, String permissionCode)`. Joins `UserCompanyTransactionRole` × `TransactionRole` × `TransactionRolePermission`. Super User short-circuit returns full set.
- [ ] T028 Rewrite `platform-security/src/main/java/com/einvoice/security/permission/PermissionAspect.java` per Decision 9. Reads `TenantContext`, calls `PermissionService`, throws `AccessDeniedException` (mapped to 403 FORBIDDEN) when missing. Bypasses for Super User in OPERATIONAL_MODE.
- [ ] T029 Update `platform-security/src/main/java/com/einvoice/security/permission/PermissionCache.java` to be request-scoped (use `@RequestScope`) — clears automatically per request (Decision 2).
- [ ] T030 Rewrite `platform-security/src/main/java/com/einvoice/security/rbac/RolePermissions.java` to expose constants for the 8 document permissions and 5 master-data permissions (Constitution XVII.6/XVII.7). Remove all old role enum values. Reference: data-model.md §5–6.
- [ ] T031 Update `platform-security/src/main/java/com/einvoice/security/SecurityConfig.java`: route `/api/auth/environments`, `/api/auth/companies`, `/api/auth/login` as **public**; `/api/auth/logout`, `/api/session/context`, `/api/admin/**` as authenticated; `/api/admin/**` additionally guarded by `hasAuthority('SUPER_USER')` mapped from JWT claim `isSuperUser` (FR-036, INV-08).

### 2E — Cross-cutting error handling + audit helper

- [ ] T032 [P] Create `platform-api/src/main/java/com/einvoice/api/error/ErrorResponse.java` (DTO matching `contracts/error-codes.md`) and `GlobalExceptionHandler.java` (`@ControllerAdvice`) mapping every code in the catalogue to its HTTP status. Include explicit handlers for `BadCredentialsException`, `LastSuperUserProtectedException`, `TaxNumberDuplicateException`, `AssignmentExistsException`, `BranchCodeDuplicateException`.
- [ ] T033 [P] Create domain exceptions in `platform-core/src/main/java/com/einvoice/core/error/`: `BadCredentialsException`, `InvalidAuthorityEnvironmentException`, `CompanyContextRequiredException`, `UnauthorizedContextException`, `InactiveCompanyException`, `LastSuperUserProtectedException`, `EmailAlreadyExistsException`, `TaxNumberDuplicateException`, `InvalidRoleForAuthorityException`, `AssignmentExistsException`, `BranchCodeDuplicateException`. Each carries the stable `code` constant.
- [ ] T034 ⏭ **DEFERRED to Wave 7** — Per spec.md Clarification C1, all audit work is deferred to Wave 7 because V37 drops the `audit_logs` table and V51 (Wave 7) recreates it. No `AdminAuditService` is created in Wave 5; admin mutations and login events do NOT produce audit-log rows in Wave 5. The Wave 7 implementation will re-derive the audit surface against the freshly-created table. **Action for Wave 5**: do not create the file; remove any local stubs if drafted.

**Checkpoint**: ✅ Foundational complete. The platform compiles, migrates cleanly on a fresh DB, and exposes `TenantContext`, `PermissionService`, `JwtTokenProvider`, and the error catalogue. User story implementation can now begin.

---

## Phase 3: User Story 1 — Operational user logs in to a specific authority+environment+company (Priority: P1) 🎯 MVP

**Goal**: A regular user can log in via the new 4-step cascading flow, lands in Operational Mode with the correct sidebar/buttons, and operational data is scoped strictly to `(company_id, authority_environment_id)`.

**Independent Test**: Seed a User + one `UserCompanyTransactionRole` (e.g., ETA INVOICE ACCOUNTANT for ABC Co under ETA PREPROD) via SQL fixture, walk the login flow, verify sidebar shows only Invoices/Customers/Items, that buttons match permissions, and that `GET /api/session/context` returns the matching permissions object. Quickstart Phase C.

### Backend — Auth flow

- [ ] T035 [US1] Create DTOs in `platform-api/src/main/java/com/einvoice/api/auth/dto/`: `EnvironmentsRequest`, `EnvironmentsResponse`, `CompaniesRequest`, `CompaniesResponse`, `LoginRequest`, `LoginResponse`. Field shapes per `contracts/auth-api.openapi.yaml`.
- [ ] T036 [US1] Create `AuthService` in `platform-api/src/main/java/com/einvoice/api/auth/AuthService.java` with methods: `listEnvironments(authority)`, `listCompanies(authority, environment, email)`, `login(LoginRequest)`. Implements the validation sequence per spec §5.3 steps 1–7 and INV-01..INV-03, INV-09, INV-10.
- [ ] T037 [US1] Create `AuthController` in `platform-api/src/main/java/com/einvoice/api/auth/AuthController.java` exposing `POST /api/auth/environments`, `POST /api/auth/companies`, `POST /api/auth/login`, `POST /api/auth/logout`. `logout` simply returns 204 (Decision 1). Annotate with `@RestController` and use `@Validated`.
- [ ] T038 ⏭ **DEFERRED to Wave 7** [US1] — Login-event auditing depends on T034's `AdminAuditService` and the `audit_logs` table, both deferred to Wave 7 (spec.md Clarification C1). `AuthService.login` in Wave 5 produces no audit row on success or failure. **Action for Wave 5**: implement `AuthService.login` per T036 only; do not call any audit API.

### Backend — Session context

- [ ] T039 [US1] Create `SessionContextResponse` DTO + nested types in `platform-api/src/main/java/com/einvoice/api/session/dto/` per `contracts/auth-api.openapi.yaml` schema.
- [ ] T040 [US1] Create `SessionContextAssembler` in `platform-api/src/main/java/com/einvoice/api/session/SessionContextAssembler.java`. Logic per data-model.md §9 "Population rules": regular user → assigned companies only; Super User Operational → all active companies with full permissions; Super User Admin → empty list. Module-key set chosen from authority (ETA → invoice/receipt/customers/items/configuration; ZATCA → standard/simplified/customers/items/configuration; FR-026).
- [ ] T041 [US1] Create `SessionContextController` in `platform-api/src/main/java/com/einvoice/api/session/SessionContextController.java` exposing `GET /api/session/context`. Reads `TenantContext.current()`, delegates to assembler.

### Frontend — Login screen + auth wiring

- [ ] T042 [US1] Update `frontend/src/app/auth/auth.service.ts` with new methods: `listEnvironments(authority)`, `listCompanies(authority, environment, email)`, `login(LoginRequest)`, `logout()`. Use Angular `HttpClient`. Persist token in `localStorage` under existing key.
- [ ] T043 [US1] Update `frontend/src/app/shared/interceptors/auth.interceptor.ts` to: attach `Authorization: Bearer <token>` to all non-public requests; on 401 response, clear token and redirect to `/login` (handles token expiry per Edge Cases — Decision 1; FR-023a).
- [ ] T044 [US1] Rewrite `frontend/src/app/auth/auth.component.{ts,html,scss}` as a 4-step cascading reactive form: (1) email+password, (2) authority dropdown ETA/ZATCA, (3) environment dropdown loaded via `auth.service.listEnvironments()`, (4) company dropdown loaded via `auth.service.listCompanies()`. Login button disabled until all required fields filled (FR-038). Header reads "Global E-Invoicing Gateway" (per implementation-plan §5.6).
- [ ] T045 [US1] Display error states from backend codes (`UNAUTHORIZED_CONTEXT`, `COMPANY_CONTEXT_REQUIRED`, `BAD_CREDENTIALS`) in the auth component as user-readable messages (FR-039).

### Frontend — Session context, sidebar, header, permission directive

- [ ] T046 [US1] Rewrite `frontend/src/app/shared/services/session-context.service.ts` to call `GET /api/session/context` after successful login (and on app-init when a token is present), store the `SessionContextResponse` in a `BehaviorSubject`, and expose `currentContext$`, `isSuperUser$`, `mode$`, `companies$`.
- [ ] T047 [US1] Create `frontend/src/app/shared/services/permission.service.ts` exposing `hasPermission(companyId: string, moduleKey: string, action: string): boolean` reading from `SessionContextService`. Returns `true` unconditionally when `isSuperUser && mode === 'OPERATIONAL_MODE'`.
- [ ] T048 [US1] Create `frontend/src/app/shared/directives/has-permission.directive.ts` implementing `*appHasPermission="['module','ACTION']"` per Decision 8. Removes host element from DOM when permission absent.
- [ ] T049 [US1] Update `frontend/src/app/layout/header/header.component.{ts,html,scss}`: render three header chips per FR-042 (revised) — (1) Authority, (2) Environment, (3) Context chip computed as: `"Admin Mode"` when `mode === 'ADMIN_MODE'`; the single `companyNameEn` (with Arabic fallback per locale) when `mode === 'OPERATIONAL_MODE' && companies.length === 1`; the literal `"<N> companies"` when `mode === 'OPERATIONAL_MODE' && companies.length >= 2`. The chip is read-only — do NOT render a dropdown or any "switch company" affordance (Constitution VIII.5–VIII.6: document lists span all assigned companies via in-page Company column). Read from `SessionContextService`.
- [ ] T050 [US1] Create `frontend/src/app/layout/sidebar/sidebar.component.{ts,html,scss}` driven by `SessionContextService`. **In Operational Mode, an item is shown when ANY accessible company in `companies[]` has `module.visible === true` for that module** (i.e., the union of permitted modules across all accessible companies; FR-043 + Constitution VIII.5). There is no "active company" concept in this component — per FR-042 (revised) and Constitution VIII.5–VIII.6, the user works across all assigned companies simultaneously and document list screens carry an in-page Company column. Items remain hidden when no accessible company has visibility. (Admin sidebar variant is added in T070.)

### Frontend — Dashboard skeleton

- [ ] T051 [US1] Create `frontend/src/app/dashboard/dashboard.component.{ts,html,scss}` rendering one card per company from `SessionContextService.companies$`. Each card shows `companyNameEn`, `companyNameAr`, `taxNumber`, and active state. **Per FR-046 (revised) U5**: active companies render with the default card border and no chip; **inactive companies render with a visually muted border (e.g., reduced opacity / Material `disabled` palette) and an explicit "Inactive" chip in the card header.** KPIs deferred to Wave 9.
- [ ] T052 [US1] Update `frontend/src/app/app.routes.ts` to: `/login` → AuthComponent (public), `/dashboard` → DashboardComponent (protected by an `authGuard` that requires a valid token), `/admin/**` → routes added in US2.

### Tests — User Story 1

- [ ] T053 [P] [US1] Contract test for `POST /api/auth/login` in `platform-api/src/test/java/com/einvoice/api/auth/AuthLoginContractTest.java`. Validates request/response shapes against `contracts/auth-api.openapi.yaml`. Assert all error codes from `error-codes.md` returned for the listed conditions.
- [ ] T054 [P] [US1] Contract test for `POST /api/auth/environments` and `POST /api/auth/companies` in `platform-api/src/test/java/com/einvoice/api/auth/AuthDiscoveryContractTest.java`.
- [ ] T055 [P] [US1] Contract test for `GET /api/session/context` in `platform-api/src/test/java/com/einvoice/api/session/SessionContextContractTest.java`. Asserts module-key set differs between ETA and ZATCA sessions (FR-026).
- [ ] T056 [P] [US1] Integration test `RegularUserLoginIT` in `platform-api/src/test/java/com/einvoice/api/auth/RegularUserLoginIT.java` covering acceptance scenarios 1–5 of US1 **scoped to Wave 5 endpoints** (`/api/auth/*`, `/api/session/context`): successful login → JWT issued, session-context payload correct (single-company → company name in chip; multi-company → N-company chip semantics per FR-042); missing `companyId` → `COMPANY_CONTEXT_REQUIRED`; no assignments → `UNAUTHORIZED_CONTEXT`; session-context for env=A excludes companies whose only assignment is in env=B (acceptance #5, scoped to session-context payload — operational-document-endpoint cross-tenant tests deferred to Wave 7+).
- [ ] T057 [P] [US1] Integration test `TenantIsolationIT` in `platform-api/src/test/java/com/einvoice/api/security/TenantIsolationIT.java` covering Constitution XXIV.5–6 and SC-003 **scoped to Wave 5 surfaces**: seed two assignments for the same user under `authority_environment_id = 2` (ETA PREPROD) and `authority_environment_id = 5` (ZATCA SANDBOX). Log in for env=2, assert `GET /api/session/context` returns only the company assigned in env=2 and never the env=5 company. Log in for env=5, assert the inverse. Also verify a Super User in `authority_environment_id = 2` Operational Mode sees env=2 companies only (not env=5). (Operational document-endpoint cross-tenant isolation tests are added in Wave 7+ alongside the endpoints they exercise.)
- [ ] T058 [P] [US1] Frontend spec `frontend/src/app/auth/auth.component.spec.ts` covering the cascading dropdown logic, Login-button-disabled-until-complete (FR-038), and error display (FR-039).
- [ ] T059 [P] [US1] Frontend spec `frontend/src/app/shared/directives/has-permission.directive.spec.ts` asserting the directive removes the host element when permission absent and renders when present (Decision 8).
- [ ] T060 [US1] End-to-end smoke per `quickstart.md` Phases A + C (manual run; record evidence in PR description). Confirms SC-002 and SC-007.

**Checkpoint**: ✅ User Story 1 complete. A regular user with seeded assignments can log in, see correct sidebar, and operate scoped to their company+environment.

---

## Phase 4: User Story 2 — Super User administers the platform in Admin Mode (Priority: P1)

**Goal**: A Super User can log in to Admin Mode (no company), use the admin sidebar, and create companies/branches/users/assignments via REST + UI. Operational endpoints are blocked in Admin Mode.

**Independent Test**: Quickstart Phase B — onboard a brand-new company end-to-end in under 5 minutes (SC-001).

### Backend — Admin services

- [ ] T061 [US2] Create `AdminCompanyService` in `platform-api/src/main/java/com/einvoice/api/admin/service/AdminCompanyService.java`. Methods: `create(CompanyCreateRequest)`, `update(id, CompanyUpdateRequest)`, `deactivate(id)`, `list(includeInactive)`. (Audit-log wiring is **deferred to Wave 7** per spec.md Clarification C1 — no `AdminAuditService` calls in Wave 5.)
- [ ] T062 [US2] Create `AdminBranchService` in `platform-api/src/main/java/com/einvoice/api/admin/service/AdminBranchService.java`. Methods: `create(companyId, BranchCreateRequest)`, `update(id, BranchUpdateRequest)`, `listForCompany(companyId)`. Throws `BranchCodeDuplicateException` when `(companyId, branchCode)` collides. (Audit-log wiring deferred to Wave 7 per spec.md Clarification C1.)
- [ ] T063 [US2] Create `AdminUserService` in `platform-api/src/main/java/com/einvoice/api/admin/service/AdminUserService.java`. Methods: `create(UserCreateRequest)` (BCrypt-hash password), `update(id, UserUpdateRequest)` (re-hash if password non-null), `activate(id)`, `deactivate(id)`, `list(includeInactive)`. **Implements INV-07 / FR-036a (revised Q4)**: any mutation method that could affect the at-least-one-active-Super-User invariant (i.e., `update` when `isSuperUser` flips false on a previously-Super user OR `isActive` flips false on a Super user, and `deactivate` on a Super user) MUST execute inside a single transaction that (1) calls `pg_advisory_xact_lock(SUPER_USER_INVARIANT_KEY)` where `SUPER_USER_INVARIANT_KEY` is a fixed bigint constant defined in the service; (2) `SELECT … FOR UPDATE` on the target user row; (3) `SELECT COUNT(*) FROM users WHERE is_super_user=true AND is_active=true AND id != :targetId` and throw `LastSuperUserProtectedException` if zero; (4) apply the mutation. The advisory lock is auto-released on transaction commit/rollback. (Audit-log wiring deferred to Wave 7 per spec.md Clarification C1.)
- [ ] T063a [P] [US2] Add `@NotBlank` validation to the `password` field on `UserCreateRequest` and `UserUpdateRequest` DTOs (T069) **and** on `LoginRequest` DTO (T035), so empty strings, whitespace-only strings, and nulls are rejected at the controller boundary with `400 VALIDATION_ERROR` before reaching service code (FR-006a refinement A1). Add unit/contract test asserting the rejection for each of: `""`, `"   "`, missing field — for both admin user-create and login.
- [ ] T064 [US2] Create `AdminAssignmentService` in `platform-api/src/main/java/com/einvoice/api/admin/service/AdminAssignmentService.java`. Methods: `create(userId, AssignmentCreateRequest)`, `delete(userId, assignmentId)`, `listForUser(userId)`. Validations:
  - `(authority-derived-from-authorityEnvironmentId, transactionType, roleCode)` resolves to a `TransactionRole` row → else `InvalidRoleForAuthorityException` (INV / FR-008).
  - Assignment dedupe → `AssignmentExistsException` on unique-constraint violation (data-model §7).
  - Tax-number uniqueness probe per Decision 6 + INV-06: count active companies (excluding the new one) that already have at least one `UserCompanyTransactionRole` row for the same `authorityEnvironmentId` and share `taxNumber` → throw `TaxNumberDuplicateException` if any.
  - (Audit-log wiring on create + delete deferred to Wave 7 per spec.md Clarification C1.)

### Backend — Admin controllers

- [ ] T065 [US2] Create `AdminCompanyController` in `platform-api/src/main/java/com/einvoice/api/admin/AdminCompanyController.java` exposing `GET/POST /api/admin/companies`, `PUT /api/admin/companies/{id}`, `PUT /api/admin/companies/{id}/deactivate`. Routes guarded by `SecurityConfig` Super-User authority (T031).
- [ ] T066 [US2] Create `AdminBranchController` in `platform-api/src/main/java/com/einvoice/api/admin/AdminBranchController.java` exposing `GET/POST /api/admin/companies/{id}/branches`, `PUT /api/admin/branches/{id}`.
- [ ] T067 [US2] Create `AdminUserController` in `platform-api/src/main/java/com/einvoice/api/admin/AdminUserController.java` exposing `GET/POST /api/admin/users`, `PUT /api/admin/users/{id}`, `PUT /api/admin/users/{id}/activate`, `PUT /api/admin/users/{id}/deactivate`.
- [ ] T068 [US2] Create `AdminAssignmentController` in `platform-api/src/main/java/com/einvoice/api/admin/AdminAssignmentController.java` exposing `GET/POST /api/admin/users/{id}/assignments`, `DELETE /api/admin/users/{id}/assignments/{assignmentId}`.
- [ ] T069 [US2] Create DTOs `CompanyCreateRequest`, `CompanyUpdateRequest`, `BranchCreateRequest`, `BranchUpdateRequest`, `UserCreateRequest`, `UserUpdateRequest`, `AssignmentCreateRequest`, plus their response DTOs in `platform-api/src/main/java/com/einvoice/api/admin/dto/` matching `contracts/admin-api.openapi.yaml` schemas.

### Frontend — Admin shell + screens

- [ ] T070 [US2] Update `frontend/src/app/layout/sidebar/sidebar.component.ts` to render the **Admin Mode** sidebar variant (Dashboard, Companies, Branches, Users, Assignments, Logs) when `mode === 'ADMIN_MODE'` and the **Operational Mode** sidebar otherwise (FR-045). Add the "Admin" entry exclusively for Super Users in either mode (FR-044).
- [ ] T071 [US2] Add Admin-Mode banner to the dashboard layout (renders only when `mode === 'ADMIN_MODE'`): "Admin Mode — operational features require logout and re-login with a company selected." (FR-045).
- [ ] T072 [US2] Update `frontend/src/app/dashboard/dashboard.component.ts` to support Super-User Operational-Mode behavior: shows all companies in the active env (not just assignments) with Edit/Manage actions and a "Create Company" CTA (FR-047). Driven from `SessionContextService` (which already returns the right list per data-model §9 rules).
- [ ] T073 [US2] Rewrite `frontend/src/app/shared/services/admin.service.ts` with methods covering every endpoint in `contracts/admin-api.openapi.yaml`. Strongly-typed DTOs.
- [ ] T074 [P] [US2] Create `frontend/src/app/admin/companies/company-list.component.{ts,html}` and `company-form.component.{ts,html}` (reactive form). List table with active-state filter; form posts to `admin.service.createCompany` / `updateCompany`. **Per spec.md Q2 / FR-004 (revised)**: the form MUST omit any logo-upload control and MUST NOT include a `logoPath` field; logo handling is deferred to a future wave when a consumer ships. Form fields are: `nameEn`, `nameAr`, `taxNumber`, `crNumber` (optional). Active-state toggle handled via the deactivate endpoint, not the form.
- [ ] T075 [P] [US2] Create `frontend/src/app/admin/branches/branch-list.component.{ts,html}` and `branch-form.component.{ts,html}` scoped under a company. Surface `BRANCH_CODE_DUPLICATE_IN_COMPANY` errors inline.
- [ ] T076 [P] [US2] Create `frontend/src/app/admin/users/user-list.component.{ts,html}` and `user-form.component.{ts,html}`. Form includes `isSuperUser` checkbox (visible to current Super User). Surface `LAST_SUPER_USER_PROTECTED` and `EMAIL_ALREADY_EXISTS` errors inline.
- [ ] T077 [P] [US2] Create `frontend/src/app/admin/assignments/assignment-list.component.{ts,html}` and `assignment-form.component.{ts,html}` (cascading: company → authorityEnvironment → transactionType → roleCode). Surface `TAX_NUMBER_DUPLICATE_IN_CONTEXT`, `INVALID_ROLE_FOR_AUTHORITY`, `ASSIGNMENT_EXISTS` errors inline.
- [ ] T078 [US2] Add `/admin/companies`, `/admin/branches`, `/admin/users`, `/admin/assignments` routes to `frontend/src/app/app.routes.ts` guarded by an `adminGuard` that requires `isSuperUser === true`.

### Tests — User Story 2

- [ ] T079 [P] [US2] Contract test for the company endpoints in `platform-api/src/test/java/com/einvoice/api/admin/AdminCompanyContractTest.java`.
- [ ] T080 [P] [US2] Contract test for branch endpoints in `platform-api/src/test/java/com/einvoice/api/admin/AdminBranchContractTest.java`.
- [ ] T081 [P] [US2] Contract test for user endpoints in `platform-api/src/test/java/com/einvoice/api/admin/AdminUserContractTest.java`. Covers `LAST_SUPER_USER_PROTECTED` + `EMAIL_ALREADY_EXISTS`.
- [ ] T082 [P] [US2] Contract test for assignment endpoints in `platform-api/src/test/java/com/einvoice/api/admin/AdminAssignmentContractTest.java`. Covers `INVALID_ROLE_FOR_AUTHORITY`, `ASSIGNMENT_EXISTS`, `TAX_NUMBER_DUPLICATE_IN_CONTEXT`.
- [ ] T083 [P] [US2] Integration test `SuperUserAdminFlowIT` in `platform-api/src/test/java/com/einvoice/api/admin/SuperUserAdminFlowIT.java` covering US2 acceptance scenarios 1–5 (Admin Mode entry → operational APIs blocked → company+branch+user+assignment creation → next-login pickup).
- [ ] T084 [P] [US2] Integration test `LastSuperUserInvariantIT` in `platform-api/src/test/java/com/einvoice/api/admin/LastSuperUserInvariantIT.java` covering quickstart Phase G steps 30–32 (sequential cases: lone-SU self-deactivation rejected; second SU created → first can now deactivate; second SU then alone → cannot deactivate). Asserts `409 LAST_SUPER_USER_PROTECTED` and unchanged DB state in each case.
- [ ] T084a [P] [US2] Concurrency integration test `LastSuperUserConcurrencyIT` in `platform-api/src/test/java/com/einvoice/api/admin/LastSuperUserConcurrencyIT.java` covering FR-036a (Q4 refinement). Two concurrent test cases: (a) **same-target race** — two threads simultaneously call `PUT /api/admin/users/{lastSuId}/deactivate`; assert exactly one HTTP 204 and exactly one HTTP 409 `LAST_SUPER_USER_PROTECTED` were observed across the two responses, and the user's final state matches whichever response was 204 (the test MUST NOT assert which thread succeeds — outcome is non-deterministic by design). (b) **cross-target race** — seed exactly two active Super Users; two threads simultaneously deactivate each other; assert that exactly one HTTP 204 and exactly one HTTP 409 were observed across the two responses, and that the post-state has exactly one active Super User remaining (again, the identity of the surviving SU is non-deterministic and MUST NOT be asserted). Both cases prove the `pg_advisory_xact_lock` + `FOR UPDATE` + count-recheck design from T063 closes the race correctly without overconstraining the test.
- [ ] T085 [US2] Frontend spec `frontend/src/app/admin/users/user-form.component.spec.ts` asserting that `LAST_SUPER_USER_PROTECTED` and `EMAIL_ALREADY_EXISTS` server-side codes render as inline errors.

**Checkpoint**: ✅ User Story 2 complete. The platform is fully self-administrable; Super Users can onboard everything end-to-end.

---

## Phase 5: User Story 3 — Cascading login dropdowns guide the user through valid choices (Priority: P2)

**Goal**: The login screen's dropdowns reflect exactly what the user is entitled to, including the Super-User "Continue without company" entry.

**Independent Test**: Quickstart Phase C steps 13–14 (regular user) and Phase B steps 4 (Super User). SC-007.

> Most of the cascading-dropdown plumbing is built in T044/T046 under US1. US3 adds the explicit Super-User "Admin Mode" entry, dropdown-empty handling, and dedicated UX tests.

- [ ] T086 [US3] Update `frontend/src/app/auth/auth.component.ts` to detect Super-User state from the `POST /api/auth/companies` response (`isSuperUser: true`) and inject a synthetic top-of-list entry "Continue without company (Admin Mode)" with `companyId = null`. Selecting this entry sets the form's `companyId` control to `null` so the LoginRequest carries the correct payload (FR-040).
- [ ] T087 [US3] Add empty-state UX in the Environment dropdown: when the response has zero environments for the chosen authority (Edge Case "Login with no available environments"), display "No environments configured for this authority" and keep Login disabled (FR-038 + Edge Case).
- [ ] T088 [US3] Update `frontend/src/app/auth/auth.component.html` to display dropdown-loading spinners on Environment and Company selectors while the discovery calls are in flight (UX polish).
- [ ] T089 [P] [US3] Frontend spec `frontend/src/app/auth/auth.component.spec.ts` (extending the spec from T058) covering: ETA selection shows ETA-only environments (US3 acceptance #1); regular user sees only assigned companies (US3 acceptance #2 + SC-007); Super User sees all + "Continue without company" entry at top (US3 acceptance #3); generic error message returned for invalid credentials regardless of which field was wrong (US3 acceptance #5 + INV-10).
- [ ] T090 [US3] End-to-end manual smoke per `quickstart.md` Phase B step 4 + Phase C steps 12–14. Verify SC-007.

**Checkpoint**: ✅ User Story 3 complete. Login UX is unambiguous and self-guiding.

---

## Phase 6: User Story 4 — Sidebar and dynamic title adapt to authority and mode (Priority: P2)

**Goal**: ETA vs ZATCA sessions render the correct module names and page title; chips and admin-only entry are correct in both modes.

**Independent Test**: Quickstart Phase C step 16 + a parallel ZATCA login that asserts "Standard / Simplified" sidebar items.

- [ ] T091 [US4] Update `frontend/src/app/layout/header/header.component.ts` to compute the dynamic page title from `loginContext.authority`: ETA → "ETA Platform"; ZATCA → "ZATCA Platform" (FR-041 + acceptance scenarios 1–2). Bind to `<title>` via `Title` service.
- [ ] T092 [US4] Update `frontend/src/app/layout/sidebar/sidebar.component.ts` to map the authority-specific module key set into UI labels: ETA → Invoices, Receipts, Customers, Items, Configuration; ZATCA → Standard, Simplified, Customers, Items, Configuration. Plus Dashboard (always) and Logs (always for any authenticated user); Admin (Super User only — FR-044). Each item hidden when `module.visible === false` (FR-043).
- [ ] T093 [US4] Update `frontend/src/app/layout/header/header.component.html` to ensure the third chip implements the FR-042 (revised) decision: `"Admin Mode"` in Admin Mode; the single company name in Operational Mode when `companies.length === 1`; `"<N> companies"` when `companies.length >= 2`. (This task is the user-story-4 acceptance gate for the chip behavior already implemented in T049; keep the two consistent — any change here must mirror in T049.)
- [ ] T094 [P] [US4] Frontend spec `frontend/src/app/layout/sidebar/sidebar.component.spec.ts` parameterised over ETA/ZATCA fixtures asserting the correct module label set per authority (US4 acceptance scenarios 1–2).
- [ ] T095 [P] [US4] Frontend spec `frontend/src/app/layout/header/header.component.spec.ts` asserting chips render correctly for Admin Mode vs Operational Mode and that the title updates on login.

**Checkpoint**: ✅ User Story 4 complete. Authority-aware shell is correct on both sides.

---

## Phase 7: User Story 5 — Permissions remain consistent between UI and backend (Priority: P3)

**Goal**: Audit-level guarantee that hidden buttons in the UI correspond to backend rejection and visible buttons correspond to backend acceptance (SC-004).

**Independent Test**: Quickstart Phases D + E + F. The dedicated parametrised test below makes SC-004 a regression test.

- [ ] T096 [US5] Create `platform-api/src/test/java/com/einvoice/api/security/PermissionParityIT.java`. For each `(authority, transactionType, roleCode)` combination from `transaction_roles`, set up a user with exactly that one assignment and verify: (a) `GET /api/session/context` returns `permissions.<action> = true` iff `transaction_role_permissions` lists that action for the role; (b) calling each guarded endpoint with that assignment returns 200/204 when the action is granted and 403 when not. Iterate over all 8 document actions and 5 master-data actions.
- [ ] T097 [US5] Create `frontend/src/app/admin/permission-parity.spec.ts` (a small page-driver test) that, given a synthetic SessionContextResponse with hand-crafted permissions, asserts the `*appHasPermission` directive shows/hides every action button on the Invoices and Customers list pages exactly as expected. Pairs with T096 to deliver UI/backend parity coverage end-to-end (SC-004).
- [ ] T098 [US5] Add `platform-api/src/test/java/com/einvoice/api/security/AdminModeRejectionIT.java` covering quickstart Phase D step 23: Super User in Admin Mode → operational endpoint → `403 COMPANY_CONTEXT_REQUIRED` (SC-006).
- [ ] T099 [US5] Add `platform-api/src/test/java/com/einvoice/api/security/JwtClaimsContractIT.java` asserting INV-09: issued JWT carries no `permissions`, no `roles`, no `assignments` claim (only the claim set listed in Decision 1).
- [ ] T100 [US5] Add `platform-api/src/test/java/com/einvoice/api/security/CrossCompanyRejectionIT.java` asserting INV-05 + FR-031 **scoped to Wave 5 admin endpoints**: a regular-user JWT (which never has access to `/api/admin/**` per FR-036) is rejected with `403 FORBIDDEN` when calling any admin endpoint with an arbitrary path-param `companyId`; a Super-User JWT has access to admin endpoints regardless of which `companyId` appears in the path (Super User bypass per Constitution XVII.3). The full FR-031 path-param-vs-JWT `companyId` guard test on non-admin endpoints is **deferred to Wave 6** when the first such endpoints ship (per spec.md Clarification U2 / FR-031 wording).
- [ ] T100a [P] [US5] Add `platform-api/src/test/java/com/einvoice/api/security/NextLoginPermissionRefreshIT.java` covering SC-008 and US5 Acceptance #3 (no stale-permission window on next login): (1) seed a regular user with an existing assignment, log them in, capture token T1 and the `GET /api/session/context` payload P1; (2) as a Super User, grant the user a NEW assignment via `POST /api/admin/users/{id}/assignments`; (3) call `GET /api/session/context` again with the still-valid T1 — assert the response equals P1 (no mid-session refresh, per FR-031a); (4) re-login as the regular user → capture token T2; (5) call `GET /api/session/context` with T2 — assert the new assignment IS reflected in `companies[]` / `modules`. Closes the SC-008 coverage gap (analysis U3).
- [ ] T101 [US5] Append a "Wave 5 Permissions Parity" subsection to the existing `Docs/configuration-reference.md` (do **not** create a new docs file — see analysis I5) referencing T096/T097 and listing the COMPANY_ADMIN/ACCOUNTANT/VIEWER default permission sets so reviewers can audit by inspection (SC-004 evidence). If `Docs/configuration-reference.md` does not exist, append to `Docs/deployment-guide.md` instead.

**Checkpoint**: ✅ User Story 5 complete. UI/backend permission parity is machine-verified and documented.

---

## Phase 8: Polish & Cross-Cutting Concerns

**Purpose**: Performance evidence, audit verification, deployment doc updates, and final regression sign-off. **Run only after all earlier phases are green.**

- [ ] T102 [P] Run quickstart.md Phase A (clean migrations) and append the Flyway migration log excerpt to the PR description as evidence (FR-049 / SC-N/A).
- [ ] T103 [P] Run quickstart.md Phase I (performance smoke). Capture p95 numbers for `/api/auth/companies`, `/api/session/context`, dashboard initial render against the targets in plan.md → Performance Goals. Attach to PR (Decision 4).
- [ ] T104 ⏭ **DEFERRED to Wave 7** — Audit trail check depends on the `audit_logs` table and `AdminAuditService`, both deferred to Wave 7 (spec.md Clarification C1). Wave 5 produces no audit rows; quickstart.md Phase J is dropped from the Wave 5 sign-off run.
- [ ] T105 [P] Update `Docs/deployment-guide.md` with Wave 5 notes: env vars `JWT_TTL_SECONDS`, `BOOTSTRAP_SUPERUSER_EMAIL`, `BOOTSTRAP_SUPERUSER_PASSWORD_HASH`; the fresh-DB requirement; the new endpoint surface; the `LAST_SUPER_USER_PROTECTED` recovery procedure (`UPDATE users SET is_super_user=true, is_active=true WHERE …`).
- [ ] T106 [P] Update `Docs/configuration-reference.md` with the new error-code catalogue (cross-link `specs/006-wave5-foundation-refactor/contracts/error-codes.md`).
- [ ] T107 Sweep `frontend/src/app/` for any leftover references to the old LOV-based session model (e.g., `lovContext`, `LovContextService`); remove or rewrite. Mirrors T015 on the backend. **Also verify** (per spec.md Q1 / English-only-UI decision): no Angular i18n / `@angular/localize` config, no locale routing, no `dir="rtl"` styling, no Arabic UI strings have been introduced; all Wave-5-modified UI labels, button text, validation/error messages, tooltips, and the Admin-Mode banner are English literals. Bilingual *data* (e.g., `nameAr` + `nameEn` rendered side-by-side on dashboard cards) is fine and expected — only UI chrome is constrained.
- [ ] T108 Run `mvn -q clean verify` (full backend test suite) and `cd frontend && npm run lint && npm test -- --watch=false` (frontend). Both must pass green.
- [ ] T109 Execute the entirety of `quickstart.md` Phases A–J end-to-end and record the result in the PR description. This is the sign-off gate per Constitution XXIV + XXVI.

**Checkpoint**: ✅ Wave 5 ready for review and merge.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — can start immediately.
- **Foundational (Phase 2)**: Depends on Setup. **BLOCKS all user stories.** Within Phase 2: 2A (migrations) → 2C (entities + repos that depend on the schema). 2B (deletions) and 2D/2E may run in parallel with 2A once the relevant types exist.
- **User Stories (Phase 3+)**: All depend on Phase 2 completion.
  - **US1** is the MVP increment.
  - **US2** is independently implementable but in practice consumed by quickstart Phase B which seeds the data US1's quickstart Phase C consumes — the team typically runs US1 + US2 in parallel after Phase 2 if staffed.
  - **US3** depends on US1 (it polishes the login screen US1 builds).
  - **US4** depends on US1 (it polishes the shell US1 builds).
  - **US5** depends on US1 + US2 (parity tests need both surfaces).
- **Polish (Phase 8)**: Depends on US1 + US2 + US5. (US3 and US4 are not strictly required for polish to start, but the quickstart full run in T109 needs them green.)

### Within Each User Story

- Backend services before backend controllers.
- Backend before frontend (frontend consumes the contracts).
- DTOs/contracts can be parallelised with services.
- Tests can be drafted in parallel with implementation, but the integration tests in particular need the controllers wired to pass.

### Parallel Opportunities

- **Foundational 2C** (T016–T021): all entity+repo tasks in parallel — different files.
- **Foundational 2B** (T012–T014): all three deletions in parallel — different files.
- **US1 tests** T053–T059: all in parallel — different test files.
- **US2 admin screens** T074–T077: four screens in parallel — disjoint folders.
- **US2 contract tests** T079–T082: four contract tests in parallel.
- **US5 tests** T096, T098, T099, T100: in parallel.
- **Polish phase**: T102–T106 in parallel.

---

## Parallel Example: User Story 1

```bash
# After Phase 2 completes, run all US1 tests in parallel:
Task: "T053 [US1] Contract test for POST /api/auth/login in platform-api/src/test/java/.../AuthLoginContractTest.java"
Task: "T054 [US1] Contract test for environments + companies endpoints"
Task: "T055 [US1] Contract test for GET /api/session/context"
Task: "T056 [US1] Integration test RegularUserLoginIT"
Task: "T057 [US1] Integration test TenantIsolationIT"
Task: "T058 [US1] Frontend spec auth.component.spec.ts"
Task: "T059 [US1] Frontend spec has-permission.directive.spec.ts"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1 (Setup) and Phase 2 (Foundational) — single sequential walk through migrations + entity + security plumbing.
2. Complete Phase 3 (US1) — login + session-context + operational shell.
3. **Stop and validate**: Run `quickstart.md` Phase A + Phase C. With the bootstrap Super User from R-migration and a SQL-seeded `(user, assignment)`, US1 is fully demonstrable.
4. Demo and decide: ship US1 as Wave 5α, or proceed to Phase 4 (US2) before tagging.

### Incremental Delivery

1. Foundation ready (Phase 1+2).
2. US1 + bootstrap Super User → operational user can log in. **MVP.**
3. US2 → Super Users self-administer. **Wave 5 fully usable.**
4. US3 + US4 → Login UX + shell polish. **Wave 5 RC.**
5. US5 + Polish → Permission parity audit + sign-off. **Wave 5 GA.**

### Parallel Team Strategy

With three developers post-Phase-2:

- Dev A: US1 (auth + session context + shell + login screen).
- Dev B: US2 (admin services + admin screens). Coordinates on shared `admin.service.ts` only at integration time.
- Dev C: US3 + US4 + US5 (UI polish + parity tests). Begins US5 tests in dry-run while US1/US2 stabilise.

Polish phase (T102–T109) is shared at the end.

---

## Notes

- **[P]** = different files, no dependencies on other in-flight tasks in the same phase.
- **[Story]** = the user story this task belongs to (US1–US5). Setup, Foundational, and Polish tasks have no story label.
- All file paths above are repo-relative from `D:\Cou\Spring Course In 28 Minutes\Projects\einvoice-platform\`.
- Each user story's checkpoint should result in a green build (`mvn -q verify` + `npm test`) and a green quickstart phase.
- Constitution Principle XXIV.5–6 isolation tests (T057, T100) are gating — do not merge with these red.
- Commit at every checkpoint and after each foundational entity/repo (T016–T021) to keep PR diffs reviewable.
