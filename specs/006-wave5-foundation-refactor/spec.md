# Feature Specification: Wave 5 — Foundation Refactoring: Authority+Environment Login, Admin/Operational Modes, Global Entities & RBAC

**Feature Branch**: `006-wave5-foundation-refactor`
**Created**: 2026-05-02
**Status**: Draft
**Input**: User description: "Docs/implementation-plan.md - Create a specification for Wave 5 only"

## Overview

Wave 5 replaces the platform's current login, session, tenancy, and permission model with a new foundation built around two top-level Lists of Values (LOV): **Authority** (ETA or ZATCA) and **Environment** (e.g., Production, Pre-Production, Sandbox, Simulation). Companies, branches, and users become **global** entities that are no longer tied to a single authority context, and access is granted explicitly per (user × company × authority+environment × transaction type) through a new role/permission model.

This wave also introduces two distinct user **modes** — **Admin Mode** (Super Users administering the platform) and **Operational Mode** (everyday document work) — and a session context API that drives the entire navigation and action-button experience for the operational user.

This is a fresh-database refactor: all prior Wave 0–4 operational data is dropped during deployment. No data migration is required because the platform has not yet been released to live customers.

## Clarifications

### Session 2026-05-02

- Q: When a Super User revokes a user's access mid-session (deactivates the user, deactivates an assignment, or deactivates the company), what happens to the user's existing sessions? → A: Existing sessions remain valid until token expiry; revocation only takes effect on the user's next login.
- Q: What is the session token lifetime, and is there a refresh mechanism? → A: Single long-lived access token (~8 hours), no refresh endpoint; users re-login when the token expires.
- Q: What password complexity rules and brute-force protections apply in Wave 5? → A: None beyond "non-empty"; no minimum length, no character-class rules, no failed-attempt lockout. (Deferred to a future platform-wide auth-hardening wave.)
- Q: What is the scope of company tax-number uniqueness? → A: Unique per `authority_environment_id` — the same tax number may appear once per (authority, environment) pair, allowing the same legal entity to register independently with ETA Production, ETA Pre-Production, ZATCA Production, etc.
- Q: How does the system prevent the platform from being left with zero Super Users? → A: Backend enforces an "at least one active Super User must always exist" invariant; any user-update, user-deactivate, or Super-User-flag-removal action that would result in zero active Super Users is rejected with a clear error.

### Session 2026-05-02 — Analysis Refinement

The cross-artifact analysis identified one CRITICAL, four HIGH, five MEDIUM, and five LOW findings. The decisions below resolve the items that affect spec scope, requirement wording, success criteria, or user-visible behavior. Implementation-only items (type alignment, validator wiring, doc placement, JWT TTL bounds) are tracked in plan.md, tasks.md, research.md, and quickstart.md but not restated here.

- **C1 (CRITICAL — audit_logs not present in Wave 5)**: All audit logging is deferred to Wave 7. The `audit_logs` table is dropped by V37 and not recreated until Wave 7's V51, so Wave 5 introduces no audit functionality whatsoever — no admin-action audit, no login-success/failure audit, no audit-trail verification gate. Constitution Principle IX is satisfied by the foundation Wave 5 ships (immutable-log model survives in tests/IX-tracker), and Wave 7 lights up the actual table and recording. Captured in the new Out-of-Scope bullet under Assumptions.
- **I1 (HIGH — operational-endpoint isolation tests)**: Wave 5 isolation testing is scoped to `/api/session/context` and `/api/admin/**` only. The operational-endpoint cross-tenant isolation testing originally implied by US1 Acceptance #5 is deferred to Wave 7+, when document endpoints (`/api/companies/{id}/eta/...`, `/api/companies/{id}/zatca/...`) actually exist. US1 Independent Test prose and Acceptance #5 updated accordingly.
- **U1 (HIGH — FR-029 enforcement)**: FR-029 reworded to make explicit that Wave 5 establishes the TenantContext and per-request permission machinery; the operational-repository scoping enforcement scaffold (base class / aspect / interceptor) is a **Wave 6 deliverable**, where the first operational repositories are introduced.
- **U2 (HIGH — FR-031 enforcement)**: FR-031 reworded to mark the path-param-vs-JWT `companyId` guard as a **Wave 6 deliverable**, since Wave 5 has no non-admin endpoints carrying a path-param `companyId`. Wave 5 enforces only FR-030 (Admin-Mode rejection of operational requests).
- **I2 (HIGH — multi-company "Company" chip)**: Pinned. There is **no "active company" concept** in Operational Mode. Per Constitution VIII.5–VIII.6, document lists span all assigned companies with a Company column. The third header chip therefore shows: in Admin Mode the literal text **"Admin Mode"**; in Operational Mode either the **single company name** when exactly one company is in scope, or **"\<N> companies"** (where N is the number of accessible companies in `companies[]` from session context) when more than one. FR-042 and User Story 1 Acceptance Scenario 1 updated.
- **A1 (MEDIUM — password non-empty enforcement)**: FR-006a strengthened to require explicit `@NotBlank` validation on `password` in admin and login DTOs (rejecting empty AND whitespace-only strings) with a contract assertion that empty/whitespace passwords return `400 VALIDATION_ERROR`.
- **A2 (LOW — SC-002 wording)**: Reworded to "two credential fields plus three context selections plus one Login click" to make the count unambiguous.
- **U5 (LOW — dashboard active-state visual)**: FR-046 extended with one sentence pinning the visual treatment for inactive companies (muted card border + "Inactive" chip).
- **D1 (LOW — credentials-error duplication)**: The "inactive user account" Edge Case bullet is removed; the rule lives in FR-024 + `contracts/error-codes.md`. Edge Cases keep only behaviors that are not already an FR.

### Session 2026-05-02 — Post-Refinement Clarifications

- Q: What language(s) and layout direction does the Wave 5 UI render in (login, dashboard, admin screens, sidebar, header chips, validation messages, button labels)? → A: **English-only UI in Wave 5**; bilingual *data* (Arabic + English names of Companies, Branches, Authority Environments, Transaction Roles) is stored and displayed where the data is shown (e.g., dashboard cards show both names; Context chip shows the English company name with Arabic fallback when English is missing), but all UI chrome — labels, buttons, validation/error messages, tooltips, the Admin-Mode banner — renders in English only with LTR layout. Arabic UI text and RTL layout are deferred to a future i18n wave; adding them later does not require changes to stored bilingual data.
- Q: Is company logo upload in scope for Wave 5, and if so, by what mechanism? → A: **Out of scope for Wave 5.** The `logo_path` column remains in the `companies` schema for forward compatibility, but the admin Company create/edit form does NOT expose a logo field, no upload endpoint is added, and `logo_path` stays NULL for every Wave 5 row. Logo upload (mechanism, format/size limits, storage location) will be added in the wave that introduces the first consumer of the logo (document PDFs / branded headers — Wave 6/7).
- Q: Where does the Angular client store the issued session token? → A: **`localStorage`** in Wave 5 (matches the existing pattern from earlier waves used by `auth.service.ts` and `auth.interceptor.ts`). The XSS exposure this creates — combined with FR-031a's "no force-logout" and the ~8h TTL — is an explicitly accepted risk for Wave 5 and is deferred to the same future auth-hardening wave that introduces password complexity, account lockout, login rate limiting, and (likely) migration to an `HttpOnly` `Secure` `SameSite=Strict` cookie with CSRF protection. No mid-wave migration of token storage will occur.
- Q: How does the system protect the "≥1 active Super User" invariant (FR-036a) against concurrent demotion/deactivation races? → A: **PostgreSQL transaction-level advisory lock** on a fixed application-wide key (`pg_advisory_xact_lock(SUPER_USER_INVARIANT_KEY)`) acquired inside `AdminUserService` before any mutation that could affect the invariant, **combined with** `SELECT … FOR UPDATE` on the target user row and a `COUNT(*)` of remaining active Super Users — all in the same transaction. The advisory lock serializes both same-target and cross-target races (e.g., two Super Users attempting to demote each other simultaneously); the row lock protects against same-row contention; the count is re-read against the locked state. (Supersedes the `READ COMMITTED`-only reasoning previously sketched in research.md Decision 7, which did not actually close the cross-target race.)

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Operational user logs in to a specific authority+environment+company (Priority: P1)

A regular user (e.g., an accountant at "ABC Company") needs to log in, choose which tax authority and which authority environment they want to work in, then choose which of their assigned companies to operate on. Once inside, the navigation and action buttons they see reflect exactly what they are allowed to do for that company in that authority+environment.

**Why this priority**: This is the primary entry point for every non-administrator user of the platform. Without a working multi-step login and permission-driven shell, operational users cannot do anything else. Every other Wave 5 capability (master data, document creation in later waves, etc.) depends on this.

**Independent Test**: Can be fully tested by seeding a user with one or more assignments (e.g., Accountant on ETA INVOICE for Company X under ETA Pre-Production), logging in via the new flow, and confirming (a) the sidebar shows only the modules permitted by those assignments; (b) action-button visibility matches the per-module permissions in `GET /api/session/context`; (c) the session-context payload returned for ETA Pre-Production excludes any companies/modules visible only under a different authority+environment. (Cross-tenant isolation testing on operational document endpoints is deferred to Wave 7+ when those endpoints exist.)

**Acceptance Scenarios**:

1. **Given** a regular user with active assignments on ETA Pre-Production for two companies, **When** the user enters valid credentials and selects Authority=ETA, Environment=Pre-Production, **Then** the system grants access in Operational Mode, displays "ETA Platform" as the workspace title, shows header chips for Authority, Environment, and `"<N> companies"` (N = 2 in this case), and the sidebar lists the union of modules permitted across those two companies under ETA Pre-Production.
2. **Given** a regular user who is logged in, **When** the session loads its permission context, **Then** every action button (Create, Edit, Delete, Submit, etc.) is shown only if the user's role for that module on at least one accessible company includes that permission, and hidden otherwise.
3. **Given** a regular user with no assignments for the chosen authority+environment, **When** they attempt to log in for that context, **Then** the system rejects login with an `UNAUTHORIZED_CONTEXT` error and an explanatory message — no session token is issued.
4. **Given** a regular user, **When** they attempt to log in without selecting a company (and they are not a Super User), **Then** the system rejects login with a `COMPANY_CONTEXT_REQUIRED` error.
5. **Given** a logged-in regular user with assignments only under ETA Pre-Production, **When** their session-context payload is loaded, **Then** `companies[]` contains exactly the companies they are assigned to in that authority+environment and never any company assigned only under a different `authority_environment_id`. (Tenant scoping of operational document endpoints is exercised in Wave 7+ when those endpoints ship.)

---

### User Story 2 - Super User administers the platform in Admin Mode (Priority: P1)

A Super User (a platform-level administrator, not tied to any single company) needs to create and maintain companies, branches, users, and access assignments. They log in without selecting a company so the platform places them into Admin Mode where operational document features are intentionally unavailable, and where administration screens — Companies, Branches, Users, Assignments — become available.

**Why this priority**: Without a Super User able to create companies and grant assignments, no regular user can be onboarded; therefore no operational story can be exercised end-to-end in production. This is co-priority with Story 1.

**Independent Test**: Can be fully tested by logging in as a Super User without selecting a company, confirming Admin Mode is entered, creating a new company, adding a branch, creating a regular user, granting that user an assignment, then logging out and verifying the regular user can subsequently log in (Story 1) for that company.

**Acceptance Scenarios**:

1. **Given** a Super User, **When** they log in choosing Authority=ETA, Environment=Pre-Production, and the "Continue without company (Admin Mode)" option, **Then** the system enters Admin Mode, displays an Admin-Mode banner explaining operational features require re-login with a company, and the sidebar shows only the administration items (Dashboard, Companies, Branches, Users, Assignments, Logs).
2. **Given** a Super User in Admin Mode, **When** they invoke any non-admin/non-auth/non-session endpoint — i.e., any path the `TenantFilter` classifies as operational (in Wave 5 this is exercised against a stub probe URL such as `GET /api/companies/{id}/anything`; the example invoice/receipt endpoints implied here ship in Wave 7+ and the same rule will apply to them) — **Then** the system rejects the request with a `COMPANY_CONTEXT_REQUIRED` error.
3. **Given** a Super User, **When** they create a new company, add a branch under it, create a new regular user, and grant that user an assignment for a specific authority+environment+company+transaction type+role, **Then** all four records are persisted and the assignment is immediately effective for that user's next login.
4. **Given** a Super User who selects a company at login (instead of "no company"), **When** login completes, **Then** the system enters Operational Mode and the user can perform operational work on that company in addition to seeing administration capabilities.
5. **Given** a Super User in Operational Mode, **When** they access the dashboard, **Then** they see all companies in the active authority+environment (not just assigned ones) with Edit/Manage actions and a "Create Company" entry point.

---

### User Story 3 - Cascading login dropdowns guide the user through valid choices (Priority: P2)

The login screen should make valid context selection self-evident: the user enters credentials, then chooses Authority, then sees only Environments valid for that Authority, then sees only Companies they are allowed to access in that authority+environment.

**Why this priority**: While the underlying enforcement is in P1, this story is the user-facing guardrail that prevents wasted attempts and confusing errors. It can be delivered after P1 enforcement exists, but is required for a usable experience at launch.

**Independent Test**: Can be tested by walking through the login screen as both a regular user (sees only assigned companies) and a Super User (sees all companies plus an explicit "no company" option), and confirming dropdown contents match what the user is entitled to.

**Acceptance Scenarios**:

1. **Given** a user has entered valid credentials and selected Authority=ZATCA, **When** the Environment dropdown opens, **Then** it shows exactly the environments configured for ZATCA (Production, Simulation, Sandbox) and no ETA environments.
2. **Given** a regular user has chosen an Authority and Environment, **When** the Company dropdown loads, **Then** it lists only companies for which the user has at least one active assignment in that authority+environment, and no others.
3. **Given** a Super User has chosen an Authority and Environment, **When** the Company dropdown loads, **Then** it lists all active companies for that authority+environment and additionally shows a "Continue without company (Admin Mode)" entry at the top.
4. **Given** any user, **When** required login fields are not all filled, **Then** the Login button remains disabled.
5. **Given** any user, **When** their credentials are invalid or their authority/environment selection is malformed, **Then** the system returns a clear error without exposing whether the email exists.

---

### User Story 4 - Sidebar and dynamic title adapt to authority and mode (Priority: P2)

The platform serves both ETA and ZATCA users from the same shell, but each authority has different transaction-type vocabulary (ETA: Invoices/Receipts; ZATCA: Standard/Simplified). The sidebar, page title, and header chips must reflect the active authority, environment, mode, and company.

**Why this priority**: Required for clarity at launch — without authority-aware labels and a permission-driven sidebar, users will see misleading menu items for the wrong authority, or items they have no permission to use.

**Independent Test**: Can be tested by logging in once as an ETA user and once as a ZATCA user with the same set of permission categories and confirming the sidebar items, page title, and header chips differ correctly.

**Acceptance Scenarios**:

1. **Given** a user logs in to ETA Pre-Production, **When** the shell renders, **Then** the title reads "ETA Platform" and the sidebar contains Dashboard, Invoices, Receipts, Customers, Items, Configuration, and Logs (each shown only when the user has at least VIEW on that module).
2. **Given** a user logs in to ZATCA Sandbox, **When** the shell renders, **Then** the title reads "ZATCA Platform" and the sidebar contains Dashboard, Standard, Simplified, Customers, Items, Configuration, and Logs (each shown only when the user has at least VIEW on that module).
3. **Given** a Super User in any session, **When** the shell renders, **Then** an additional "Admin" sidebar entry appears regardless of authority.
4. **Given** any logged-in user, **When** the shell renders, **Then** header chips display the active Authority, the active Environment, and a third Context chip whose value is "Admin Mode" in Admin Mode, the single accessible company's name when `companies.length === 1` in Operational Mode, or `"<N> companies"` (where N = `companies.length`) when `companies.length >= 2` in Operational Mode (per FR-042).

---

### User Story 5 - Permissions remain consistent between UI and backend (Priority: P3)

The set of permissions a user sees in the UI (controlling button visibility and sidebar items) must exactly match what the backend will allow when the user clicks one of those buttons. Hiding a button must always coincide with backend rejection of that action; showing a button must always coincide with backend acceptance.

**Why this priority**: Critical for audit, security review, and user trust — but as long as P1 enforcement is in place, a temporary UI/backend drift is unsafe rather than non-functional. Listed as P3 because validation is the deliverable; the underlying enforcement was built in P1/P2.

**Independent Test**: Can be tested by enumerating each module and each permission, scripting a user with that permission only, and verifying both that the corresponding action button is visible/usable and that all other action calls for the same module are rejected with an authorization error.

**Acceptance Scenarios**:

1. **Given** a user with VIEW-only permission on a module, **When** they navigate to the module, **Then** they see lists/details but no Create/Edit/Delete/Submit buttons, and any direct attempt at those actions via the backend is rejected.
2. **Given** a user with no permissions for a module, **When** the sidebar renders, **Then** the module entry is absent and any direct backend call to that module's endpoints is rejected.
3. **Given** an administrator changes a user's assignment, **When** the user logs in next, **Then** the new permission set is reflected on the very first session-context load — there is no stale-permission window driven by token contents.

---

### Edge Cases

- **Inactive company**: A company that exists but is deactivated does not appear in the Company dropdown and cannot be selected at login, even by a Super User.
- **Inactive assignment**: An assignment with active flag false does not contribute to the user's permitted-companies list and does not grant any module visibility.
- **Authority+Environment combination disabled**: An (authority, environment) catalogue entry with active flag false is omitted from the Environment dropdown for all users.
- **User has assignments in multiple authorities**: The user must explicitly choose Authority and Environment per session; switching context requires logout and re-login (no in-app context switching in this wave).
- **Super User without explicit assignments**: A Super User in Admin Mode does not require any rows in the assignment table; their Super-User flag is sufficient. In Operational Mode, the Super User implicitly has full access to the chosen company.
- **Last Super User self-deactivation attempt**: A Super User who is the only active Super User on the platform attempting to deactivate themselves, remove their own Super-User flag, or perform any update that would result in zero active Super Users is rejected with a `LAST_SUPER_USER_PROTECTED` error; recovery requires either creating another Super User first, or (in catastrophic loss) a direct database intervention.
- **Concurrent logins from multiple browsers**: Each session carries its own context and is independent; updates by an administrator only take effect for sessions created after the change.
- **Permissions update mid-session**: Until the user re-loads the session context (typically on next login), they continue to see the cached permission set; this is acceptable because all final enforcement is server-side per request.
- **Access revoked mid-session**: When an administrator deactivates a user, deactivates an assignment, or deactivates a company while the affected user has an active session, the existing session remains valid until token expiry (~8 hours from issuance). Revocation takes effect on the user's next login. Administrators needing immediate cut-off must wait for token expiry (no force-logout or refresh mechanism in Wave 5).
- **Token expiry mid-task**: When the ~8-hour token expires while a user is in the middle of work, the next request returns an authentication error and the user is redirected to the login screen; no automatic refresh is performed.
- **Cross-tenant access attempt via crafted request**: An operational request whose payload references a company the active session was not issued for must be rejected with an authorization error, not silently scoped down.
- **Login with no available environments for chosen authority**: The Environment dropdown is empty; the Login button stays disabled and a clear message explains no environments are configured.

## Requirements *(mandatory)*

### Functional Requirements

#### Authority and Environment model

- **FR-001**: The system MUST maintain a fixed catalogue of supported (authority, environment) pairs that includes ETA Production, ETA Pre-Production, ZATCA Production, ZATCA Simulation, and ZATCA Sandbox, each marked active or inactive.
- **FR-002**: The system MUST treat the (authority, environment) pair as a top-level isolation boundary: every operational record MUST be tied to exactly one such pair and MUST never be visible across pairs.
- **FR-003**: The system MUST present a human-readable label for each (authority, environment) pair in dropdowns and header chips (e.g., "ETA Pre-Production").

#### Global entities

- **FR-004**: The system MUST manage Companies as global entities not tied to any specific authority+environment, including bilingual names, tax number, optional commercial-registration number, and active flag. The schema MUST also include an optional `logo_path` field for forward compatibility, but Wave 5 MUST NOT expose any UI control or API endpoint to populate it; `logo_path` remains NULL for all Wave 5 rows. Logo upload mechanism, format/size limits, and storage location are deferred to the wave that introduces the first consumer of the logo (document PDFs / branded headers).
- **FR-005**: The system MUST manage Branches as global entities under a Company, including bilingual names, optional branch code (unique per company), full postal address fields, optional ETA taxpayer-activity code, and active flag.
- **FR-006**: The system MUST manage Users as global entities with a unique email, hashed password, Super-User flag, and active flag.
- **FR-006a**: Wave 5 MUST NOT impose password complexity rules beyond rejecting empty or whitespace-only values; minimum-length, character-class, and history checks are out of scope and deferred to a future auth-hardening wave. The "non-empty" gate MUST be enforced explicitly at the API DTO boundary (e.g., `@NotBlank` on `password` in admin user-create / user-update DTOs and on `password` in the login DTO) so that empty strings, strings consisting solely of whitespace, and `null` all return `400 VALIDATION_ERROR` before any BCrypt hashing or authentication attempt is made.
- **FR-006b**: Wave 5 MUST NOT implement account lockout, failed-attempt throttling, or rate limiting on the login endpoint; all such protections are deferred to a future auth-hardening wave.
- **FR-007**: The system MUST NOT enforce database-level uniqueness of company tax number; uniqueness MUST be validated at application level scoped by `authority_environment_id`. The same tax number MAY appear at most once per (authority, environment) pair (e.g., once for ETA Production AND once for ZATCA Production AND once for ETA Pre-Production is permitted), but a second active company with the same tax number under the same `authority_environment_id` MUST be rejected at create or update time with a clear validation error.
- **FR-007a**: Tax-number uniqueness validation in FR-007 MUST consider only active companies; deactivated companies do not block reuse of the same tax number under the same `authority_environment_id`.

#### Role-based access control

- **FR-008**: The system MUST define a fixed catalogue of (authority, transaction type, role code) entries covering: ETA Invoice/Receipt/Customers/Items/Config; ZATCA Standard/Simplified/Customers/Items/Config — for the role codes Company Admin, Accountant, and Viewer (Viewer applies only where read-only access is meaningful).
- **FR-009**: The system MUST associate each role with a fixed set of permissions, drawn from: VIEW, CREATE, EDIT, DELETE, CANCEL, TRANSFER, REFRESH, SUBMIT (for document modules) or VIEW, CREATE, EDIT, DELETE, REFRESH (for master-data and configuration modules).
- **FR-010**: The system MUST grant permissions to a user via assignments of the form (user, company, authority+environment, transaction type, role code), each independently activatable.
- **FR-011**: The system MUST treat Super User as a flag on the user record that grants full permissions in Operational Mode without requiring assignment rows, and grants administrative capabilities in Admin Mode.
- **FR-012**: The system MUST persist who granted each assignment and when, for audit purposes.

#### Login flow

- **FR-013**: The system MUST require credentials (email + password) plus an explicit (authority, environment, companyId) selection on every login.
- **FR-014**: The system MUST expose an API to return the valid environments for a given authority, populated from the active (authority, environment) catalogue.
- **FR-015**: The system MUST expose an API to return, for a given (authority, environment, email), the set of companies the user may select: all active companies for a Super User; only companies with active assignments for a regular user.
- **FR-016**: The system MUST validate credentials first, then validate the (authority, environment) selection, then validate the company selection — failing with a clear error code at the earliest invalid step.
- **FR-017**: The system MUST reject regular-user logins that omit company selection with `COMPANY_CONTEXT_REQUIRED`.
- **FR-018**: The system MUST reject regular-user logins where the user has no active assignment for the chosen company under the chosen authority+environment with `UNAUTHORIZED_CONTEXT`.
- **FR-019**: The system MUST allow Super-User logins without company selection; this MUST place the session into Admin Mode.
- **FR-020**: The system MUST allow Super-User logins with company selection; this MUST place the session into Operational Mode.
- **FR-021**: The system MUST issue a session token containing user identity, Super-User flag, the chosen authority and environment (including the resolved authority+environment identifier), the chosen company (nullable in Admin Mode), and the active mode.
- **FR-022**: The system MUST NOT embed the user's full permission set in the session token.
- **FR-023**: The system MUST provide a logout endpoint alongside login. No refresh endpoint is provided in Wave 5; expired tokens require the user to log in again.
- **FR-023a**: Issued session tokens MUST have a lifetime of approximately 8 hours from issuance, after which the user MUST re-authenticate. This bound also defines the maximum window during which a revoked user (per FR-031a) may continue to operate.
- **FR-024**: The system MUST return the same generic error for invalid email and invalid password (no enumeration).

#### Session context and permission enforcement

- **FR-025**: The system MUST expose a session-context endpoint that returns the active user, mode, login context (authority, environment, identifier), and a permissions structure listing each company in the active authority+environment with module-by-module visibility and per-permission flags.
- **FR-026**: The session-context permissions structure MUST use authority-appropriate module names: ETA → invoice, receipt, customers, items, configuration; ZATCA → standard, simplified, customers, items, configuration.
- **FR-027**: The frontend MUST call the session-context endpoint after login and use it as the single source of truth for sidebar visibility and action-button gating.
- **FR-028**: The backend MUST authoritatively re-check permissions on every request and MUST NOT rely on the frontend's gating.
- **FR-029**: Every operational query MUST scope by both the active company and the active authority+environment, returning only data that matches both. **Wave 5 scope**: this requirement establishes the runtime foundation only — the `TenantContext` carries the scoping values and the session-context endpoint is scoped accordingly. **Repository-level enforcement scaffold** (a base class, query-method interceptor, or equivalent that ensures every operational repository query inherits the scoping clause) is a **Wave 6 deliverable** and must be in place before the first operational repositories ship in Wave 6. Wave 5 introduces no operational repositories or operational document endpoints, so there is nothing to enforce against in this wave beyond the session-context payload assembly already described in FR-025.
- **FR-030**: The backend MUST reject any operational request submitted while the session mode is Admin Mode with `COMPANY_CONTEXT_REQUIRED`.
- **FR-031**: The backend MUST reject any request whose path or payload references a company the session is not authorized for. **Wave 5 scope**: enforcement is limited to FR-030 (Admin-Mode rejection of operational requests) plus the Super-User-only gate on `/api/admin/**` (FR-036). Wave 5 ships **no non-admin endpoints carrying a path-param `companyId`**, so the path-param/body-vs-JWT `companyId` guard required by this requirement is a **Wave 6 deliverable** that must be in place before the first operational endpoints with `/companies/{id}/...` paths ship in Wave 6.
- **FR-031a**: The backend MUST honor the session token's embedded context (user, company, authority, environment, mode, Super-User flag) for the lifetime of that token. Mid-session revocation of the user, assignment, or company MUST NOT invalidate an outstanding token; the change MUST take effect on the user's next login. No force-logout or token-blocklist mechanism is provided in Wave 5.

#### Administration APIs (Super User only)

- **FR-032**: The system MUST allow Super Users to create, update, deactivate, and list companies.
- **FR-033**: The system MUST allow Super Users to create, update, and list branches under a company.
- **FR-034**: The system MUST allow Super Users to create, update, activate, deactivate, and list users.
- **FR-035**: The system MUST allow Super Users to create, list, and delete assignments granting a user a specific role on a specific (authority+environment, company, transaction type) combination.
- **FR-036**: The system MUST reject all administration API calls for non-Super-User accounts.
- **FR-036a**: The system MUST enforce, at the administration API layer, the invariant that at least one active Super User exists at all times. Any user-update, user-deactivate, or Super-User-flag-removal request — whether targeting another user or the calling user themselves — that would result in zero active Super Users MUST be rejected with a clear error code (e.g., `LAST_SUPER_USER_PROTECTED`) and MUST leave system state unchanged. The enforcement MUST be safe under concurrent demotion attempts: a single-DB transaction that (a) acquires a transaction-level advisory lock on a fixed application-wide key, (b) acquires a row lock on the target user, and (c) recounts remaining active Super Users excluding the target before mutating, is the required structure. Any two concurrent demotion attempts — same-target or cross-target — MUST serialize through the advisory lock so that the second to acquire it observes the post-commit state of the first and rejects with `LAST_SUPER_USER_PROTECTED` if its mutation would now violate the invariant.

#### Login screen experience

- **FR-037**: The login screen MUST collect credentials first, then reveal Authority, Environment, and Company selectors in cascading order.
- **FR-038**: The login screen MUST disable the Login button until all required fields are populated.
- **FR-039**: The login screen MUST surface backend error codes (`UNAUTHORIZED_CONTEXT`, `COMPANY_CONTEXT_REQUIRED`, invalid credentials, no environments available) as user-readable messages.
- **FR-040**: The login screen MUST present, for Super Users, an explicit "Continue without company (Admin Mode)" option at the top of the Company selector.

#### Application shell

- **FR-041**: The application shell MUST display a dynamic page title reflecting the active authority (e.g., "ETA Platform", "ZATCA Platform").
- **FR-042**: The application shell MUST display three read-only header chips: (1) the active Authority, (2) the active Environment, and (3) a Context chip whose value depends on session mode and the number of accessible companies. Specifically, the Context chip MUST display:
  - In Admin Mode: the literal text **"Admin Mode"**.
  - In Operational Mode with exactly one accessible company in `companies[]`: the **single company's display name** (English by default, with Arabic fallback per locale preferences).
  - In Operational Mode with two or more accessible companies in `companies[]`: the literal text **"\<N> companies"** where `N` is the count of entries in `companies[]` from the session-context response.
  Operational Mode does NOT carry a "single active company" concept — per Constitution Principle VIII.5–VIII.6, document list screens span all assigned companies with a Company column, so the Context chip surfaces breadth, not selection. The chip is read-only in all cases (no dropdown, no click-to-switch).
- **FR-043**: The application shell MUST render the sidebar from the session-context permissions, hiding modules the user has no visibility for.
- **FR-044**: The application shell MUST show an "Admin" entry in the sidebar exclusively for Super Users.
- **FR-045**: The application shell MUST, in Admin Mode, replace the operational sidebar with an administration sidebar (Dashboard, Companies, Branches, Users, Assignments, Logs) and display a banner explaining operational features require re-login with a company.

#### Dashboard

- **FR-046**: In Operational Mode, the dashboard MUST present one card per company assigned to the user in the active authority+environment, showing company name, tax number, and active state. Inactive companies MUST render with a visually muted card border and an explicit "Inactive" chip in the card header so users can distinguish them at a glance from active companies; active companies render with the default card border and no chip. (Detailed KPIs and quick stats are deferred to Wave 9; Wave 5 establishes the card layout and active/inactive visual treatment only.)
- **FR-047**: In Operational Mode for a Super User, the dashboard MUST present cards for all companies in the active authority+environment, plus a "Create Company" action and Edit/Manage actions per card.

#### Migration and deployment

- **FR-048**: Deployment of Wave 5 MUST drop all prior operational tables and recreate the schema from scratch, since no data migration is required.
- **FR-049**: All Wave 5 schema migrations MUST run cleanly against a fresh database.

### Key Entities *(include if feature involves data)*

- **Authority Environment**: A fixed catalogue entry pairing a tax authority (ETA, ZATCA) with a specific environment of that authority (Production, Pre-Production, Simulation, Sandbox), carrying a small numeric identifier and a human-readable label, with an active flag. Five entries exist in Wave 5.
- **Company**: A global business entity with bilingual name, tax number, optional commercial-registration number, and active flag. Companies are not bound to any single authority or environment. (A `logo_path` field is reserved in the schema for forward compatibility but is NOT user-editable in Wave 5 — see FR-004.)
- **Branch**: A physical or logical operating location belonging to a Company, with bilingual name, optional branch code (unique within its company), postal address, optional ETA-specific activity code, and active flag.
- **User**: A person with login credentials (email and hashed password), a Super-User flag, an active flag, and display name. Not tied to any single company.
- **Transaction Role**: A fixed catalogue entry naming a role (Company Admin, Accountant, Viewer) for a specific combination of authority and transaction type (e.g., ETA INVOICE, ZATCA SIMPLIFIED, ETA CUSTOMERS).
- **Transaction Role Permission**: The set of permission codes (VIEW, CREATE, EDIT, DELETE, CANCEL, TRANSFER, REFRESH, SUBMIT for documents; VIEW, CREATE, EDIT, DELETE, REFRESH for master data) granted to each Transaction Role.
- **User Assignment**: An active or inactive grant of a Transaction Role to a specific User on a specific Company under a specific Authority Environment, capturing who granted it and when.
- **Tenant Context**: The runtime per-request structure carrying the active user, company (nullable in Admin Mode), authority, environment, authority-environment identifier, mode (Admin or Operational), and Super-User flag. Permissions are looked up live, not embedded here.
- **Session Context**: The response payload assembled from the user's assignments and consumed by the frontend to render sidebar and action-button visibility. Contains, per accessible company, a per-module visibility flag and per-permission flags.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A Super User can onboard a brand-new company end-to-end (create company, add a branch, create a user, grant an assignment) in under 5 minutes from a clean platform.
- **SC-002**: A regular user can log in and reach their first operational module in no more than two credential field entries (email + password) plus three context selections (authority, environment, company) plus one Login click — i.e., at most six total input actions on the login screen.
- **SC-003**: 100% of operational read endpoints return data scoped exclusively to the active company and authority+environment, verified by isolation tests that introduce decoy records in adjacent contexts and confirm they are never returned.
- **SC-004**: 100% of action buttons visible to a user correspond to backend permissions that accept that user's request, and 100% of hidden action buttons correspond to backend permissions that reject that user's request — no UI/backend drift.
- **SC-005**: A regular user with no assignments for a chosen authority+environment receives a clear `UNAUTHORIZED_CONTEXT` error and never receives a session token, in 100% of attempts.
- **SC-006**: A Super User in Admin Mode receives a clear `COMPANY_CONTEXT_REQUIRED` error in 100% of attempts to call any operational endpoint.
- **SC-007**: The login Company dropdown for a regular user lists exactly the set of companies for which they have at least one active assignment in the chosen authority+environment, with zero false positives or omissions.
- **SC-008**: When an administrator activates a new assignment for a user, the user sees the corresponding permissions on their very next login, with no stale-permission window.
- **SC-009**: A user switching from ETA to ZATCA (via logout and re-login) sees the correct authority-specific module names (Standard/Simplified vs Invoices/Receipts) within one shell render, with no leakage of the prior authority's labels.
- **SC-010**: A Super User in Operational Mode sees all active companies for the chosen authority+environment on the dashboard; a regular user sees only their assigned companies — verified by counting cards against expected sets.

## Assumptions

- This wave is delivered as a fresh-database refactor; the platform has no live customer data and no migration of prior records is required.
- Password reset and self-service registration are out of scope for Wave 5; user accounts are created and managed exclusively by Super Users.
- Single sign-on (SSO), multi-factor authentication, and external identity providers are out of scope for Wave 5; authentication is email + password only.
- Password complexity rules, account lockout, and login rate limiting are out of scope for Wave 5 and will be addressed in a future auth-hardening wave; this is an accepted risk for the current phase.
- Client-side session-token storage uses browser `localStorage` in Wave 5 (matching the existing platform pattern). This is XSS-vulnerable; combined with the no-force-logout / no-refresh decisions and the ~8h TTL, a token captured by an injected script can be replayed for the remainder of the TTL. The risk is explicitly accepted for Wave 5 and is bundled into the future auth-hardening wave that will (most likely) migrate to `HttpOnly` `Secure` `SameSite=Strict` cookies with CSRF protection.
- A user may be a Super User of the platform; this flag is set directly on the user record by another Super User during user creation/edit. Bootstrapping the very first Super User is handled by deployment seeding (out of band of the login flow).
- Within a single browser session, a user works in exactly one (authority, environment, company-or-Admin-Mode) context; switching contexts requires logout and re-login.
- Permissions are read live via the session-context endpoint after login; in-session refresh of the permission set when an administrator changes assignments is not a Wave 5 requirement (next-login pickup is acceptable).
- Detailed dashboard KPIs (counts, ZATCA certificate-expiry warnings, recent activity feed) are scoped to Wave 9; Wave 5 establishes only the company-card grid layout and Admin-Mode dashboard skeleton.
- Audit logging is **out of scope for Wave 5**. The `audit_logs` table is dropped by V37 and is recreated by Wave 7's V51 migration, so no admin-action, login, or session-event auditing is recorded in Wave 5. Constitution Principle IX continues to apply to Wave 7+ when the table returns; Wave 5 only ensures the foundation (TenantContext fields, error codes) is in place for Wave 7 to record against. Operationally this means the Wave-5-era window has no audit trail — accepted as part of the fresh-DB refactor risk.
- Session token format and cryptographic mechanisms are an implementation choice consistent with the platform's existing auth stack; the only requirement is that the token does NOT carry the user's permission set.
- Active flags on User Assignments allow temporary suspension of access without deletion; deletion remains available for permanent removal.
- The set of supported (authority, environment) pairs is fixed in Wave 5 (five entries: ETA Production, ETA Pre-Production, ZATCA Production, ZATCA Simulation, ZATCA Sandbox); adding new pairs in future waves is a configuration change, not a feature change.
- Wave 5 UI is rendered in **English only** with LTR layout. Bilingual data fields (Arabic + English names) are stored and surfaced in lists/cards/chips where the data itself appears, but UI chrome (button labels, validation messages, tooltips, banners) is English. Arabic UI and RTL layout are out of scope for Wave 5 and deferred to a future internationalization wave; the data model already supports them so no schema changes will be needed at that time.
