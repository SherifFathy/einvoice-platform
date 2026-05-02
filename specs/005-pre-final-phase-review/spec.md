# Feature Specification: Pre-Final Phase Review — Corrections & Enhancements

**Feature Branch**: `005-pre-final-phase-review`
**Created**: 2026-04-20
**Status**: Draft
**Input**: User requirements document "Project Requirements – Phase Review Before Final Phase" covering login LOV selection, data isolation, address restructuring, company/branch management, bug fixes, user management, roles & permissions, bulk upload templates, and super user role.

---

## Background

Before the final deployment phase, the platform requires corrections to authentication flow, data architecture, access control, and master data management. These changes affect the login screen, tenancy context model, company/branch structure, user/roles system, and bulk upload features.

---

## Clarifications

### Session 2026-04-20

- Q: What is the "Document Type" LOV name as shown in the UI? → A: Name TBD — use "Document Type" as placeholder for now
- Q: Is data isolation by LOV combination additive (new data goes to new context) or does it require migration of existing data? → A: New architecture; existing dev data may be wiped (not a production migration concern for this phase)
- Q: For Sub-Environment — does it depend on LOV #1 (ZATCA vs ETA)? → A: Yes. ZATCA sub-environments: Sandbox, Simulation, Production. ETA sub-environments: Preprod, Production.
- Q: Does "Receipt" document type apply to both ZATCA and ETA, or only one? → A: Applies to ETA only; ZATCA uses Invoice only. Sub-environment list adapts to the authority selected.
- Q: Are the 14 permissions a replacement for the existing ROLE-based system, or do they layer on top? → A: Layer on top — roles remain, permissions are additive fine-grained grants per company per user.
- Q: For bulk upload: is this in addition to the existing import feature in Wave 1, or does it replace it? → A: In addition — the existing import stays; this adds a template download + upload UI action specifically labeled as "Bulk Upload".
- Q: Super User — is it a role in `user_company_roles` or a flag on the `users` table? → A: New dedicated role `SUPER_USER` added to the role enum, superseding all others.

---

## Scope

### 1 — Login Screen Overhaul

#### 1.1 Side Menu Visibility
- The left navigation sidebar must be hidden completely on the `/login` route.
- It appears only after successful authentication and JWT issuance.
- Angular route-level guard determines sidebar visibility based on auth state.

#### 1.2 Three Mandatory LOVs at Login
Before the login button becomes enabled, the user must select all three:

| # | LOV | Options |
|---|-----|---------|
| 1 | Authority (Environment) | ZATCA, ETA |
| 2 | Document Type | Invoice, Receipt (Receipt shown only when Authority = ETA) |
| 3 | Sub-Environment | ZATCA: Sandbox, Simulation, Production · ETA: Preprod, Production |

Rules:
- Sub-Environment options change dynamically based on Authority selection.
- Document Type "Receipt" is hidden when Authority = ZATCA.
- Login button is disabled until all 3 are selected.
- On successful login, the 3 selections are embedded in the JWT as `active_authority`, `active_doc_type`, `active_sub_env`.

**Canonical naming (single source of truth):**

| LOV Value (display) | `lov_contexts.authority` | `lov_contexts.sub_env` | Maps to `authority_configs.environment` |
|---------------------|--------------------------|------------------------|----------------------------------------|
| ZATCA / Sandbox | ZATCA | SANDBOX | ZATCA_SANDBOX |
| ZATCA / Simulation | ZATCA | SIMULATION | ZATCA_SIMULATION |
| ZATCA / Production | ZATCA | PRODUCTION | ZATCA_PRODUCTION |
| ETA / Preprod | ETA | PREPROD | ETA_PREPRODUCTION |
| ETA / Production | ETA | PRODUCTION | ETA_PRODUCTION |

The user-facing display label for "Simulation" is **"Simulation"** (not "Test" as in the original requirement — "Test" referred to ZATCA's simulation/sandbox environment and is replaced by the ZATCA authority's own terminology).

---

### 2 — Data Isolation Per LOV Context

Every **transactional** entity scoped to a company must also be scoped to a **LOV context key**: `(authority, document_type, sub_environment)`.

Affected entities:
- `invoices`
- `customers`
- `items`
- `branches`

**Explicitly excluded from LOV context isolation:**
- `companies` — Companies are platform-global entities. A company exists once across all LOV contexts; users access it regardless of their active context. The company list is filtered only by `user_company_roles`, not by LOV context.
- `submission_attempts`, `invoice_artifacts`, `jobs`, `job_items`, `eta_item_codes` — These entities derive their context from their parent `invoice`, which already carries `lov_context_id`. Querying via invoice FK is sufficient; adding a redundant `lov_context_id` to these tables is unnecessary.

A new `lov_contexts` table stores each unique combination. The four transactional entities above gain a `lov_context_id FK` column.

A user sees only data that matches their currently active LOV context (set at login).

---

### 3 — Address at Branch Level

Address fields are removed from the `companies` table and added to the `branches` table.

**Per-authority field requiredness:**

| Field | ZATCA Required | ETA Required | Notes |
|-------|---------------|-------------|-------|
| `street` | Yes | Yes | |
| `building_number` | Yes | No | ZATCA BR-KSA-09 |
| `additional_number` | No | No | Optional both |
| `city` | Yes | Yes | |
| `district` | Yes | No | ZATCA BR-KSA-09 |
| `postal_code` | Yes | No | ZATCA BR-KSA-09 |
| `country_code` | Yes | Yes | 2-letter ISO |
| `additional_street` | No | No | Optional both |

ZATCA validation of branch address is enforced by `ZatcaBranchAddressRule` in `ValidationEngine` (see §8 / tasks T023b). ETA fields are validated by `EtaInvoiceSerializer` at serialization time.

Company record retains: name fields, VAT number, CR number, logo path, is_active, timestamps.

---

### 4 — Company & Branch Management UI

#### 4.1 Company List Screen
- Each company row has an **Edit** button that opens the company edit form.
- Each company row has a **Branches** navigation button/link that navigates to the branch list filtered to that company.

#### 4.2 Branch List Screen (per company)
- Shows all branches for the selected company.
- Each branch row has an **Edit** button.
- A **New Branch** button creates a new branch under that company.
- Branch edit form includes the address fields (moved from company in §3).

---

### 5 — Bug Fixes

| # | Description |
|---|-------------|
| BF-01 | Fix: Unable to create a Customer (creation API or form broken) |
| BF-02 | Fix: Unable to create an Item (creation API or form broken) |

Both fixes require root-cause investigation before patching. Likely causes: validation constraint mismatch, missing required fields in request DTO, or FK constraint failure on `lov_context_id` once §2 is applied.

---

### 6 — User Management Screen

A new "Users" screen accessible to Super User only.

Fields per user:
- Name (required)
- Email (required, unique across system)
- Password (used for login, hashed BCrypt on save, never returned in API)

Actions:
- Create new user
- Edit existing user (name, email, reset password)
- Deactivate / Reactivate user

**Audit logging (mandatory — Constitution VI.2):** Every user management action MUST create an immutable `audit_log` entry. Actions to audit: user created, user name/email updated, password reset, user activated, user deactivated, user deleted, user assigned to company, user removed from company, role changed, permission granted, permission revoked, Super User promotion.

API endpoints:
- `GET /api/admin/users` — list all users (Super User only)
- `POST /api/admin/users` — create user
- `PUT /api/admin/users/{id}` — update name / email
- `PUT /api/admin/users/{id}/password` — set new password
- `PUT /api/admin/users/{id}/activate` / `deactivate`
- `DELETE /api/admin/users/{id}` — soft delete

---

### 7 — User Assignment Moved to User Level

Remove the "Assign Users" action from the Company screen.

Instead, from the User Management screen (§6), a Super User can:
- Assign the user to one or more companies (creates `user_company_roles` records).
- Set the role for that user in each company.
- Remove the user from a company.

---

### 8 — Roles & Permissions System

#### 8.1 LOV-Based Access Scoping
All permissions a user holds are evaluated against their active LOV context at login. A user with access to Company A under `ZATCA / Invoice / Sandbox` does **not** automatically have access to Company A under `ETA / Invoice / Preprod`.

A new `user_context_permissions` table captures per-user, per-company, per-LOV-context permission grants.

#### 8.2 Company Access Control
From the User Management screen, a Super User assigns a user access to specific companies. This is the replacement for the removed "Assign Users" at company level.

#### 8.3 Role Assignment per Company
Roles are assigned at the company level per user (existing `user_company_roles` table remains). Roles:
`SUPER_USER`, `COMPANY_ADMIN`, `ACCOUNTANT`, `VIEWER`

#### 8.4 Fine-Grained Permissions
14 permissions, each individually grantable/revocable per user per company per LOV context:

| # | Permission Key |
|---|---------------|
| 1 | `CREATE_INVOICE` |
| 2 | `CREATE_CUSTOMER` |
| 3 | `CREATE_ITEM` |
| 4 | `EDIT_INVOICE` |
| 5 | `EDIT_CUSTOMER` |
| 6 | `EDIT_ITEM` |
| 7 | `DELETE_INVOICE` |
| 8 | `DELETE_CUSTOMER` |
| 9 | `DELETE_ITEM` |
| 10 | `TRANSFER_INVOICE` |
| 11 | `REFRESH_INVOICE` |
| 12 | `VIEW_INVOICE_LIST` |
| 13 | `VIEW_CUSTOMER_LIST` |
| 14 | `VIEW_ITEM_LIST` |

SUPER_USER implicitly holds all 14 permissions across all contexts.

---

### 9 — Bulk Data Upload Templates

#### 9.1 Items Screen
- **Download Template** button: downloads `items_template.xlsx` with column headers matching item fields.
- **Upload Template** button: file picker accepts `.xlsx`; validates structure matches the download template; bulk creates/updates items in the active LOV context.

#### 9.2 Customers Screen
- **Download Template** button: downloads `customers_template.xlsx`.
- **Upload Template** button: same validation and bulk upsert behavior.

Template column definitions are specified in `data-model.md`.

Validation behavior:
- Row-by-row validation; invalid rows collected and returned in a validation report.
- Valid rows are processed regardless of invalid ones (best-effort import).
- API returns: `{ processed: N, failed: M, errors: [{row, field, message}] }`

> **Constitution XVII.3 scope note**: Constitution XVII.3 ("Bulk processing MUST use asynchronous execution and progress tracking") applies to invoice submission batch processing (Wave 3/4 bulk-ops jobs). Small-batch ETL imports via this upload feature (items and customers templates) are explicitly exempt and process synchronously, returning results inline. If an upload exceeds 500 rows, the API SHOULD return a `202 Accepted` with a job ID for async polling instead.

---

### 10 — Super User Role

#### 10.1 Capabilities
| Capability | Description |
|------------|-------------|
| Manage Users | Create, edit, deactivate user accounts |
| Assign Roles | Assign/modify roles for any user in any company |
| Create Super Users | Promote any user to `SUPER_USER` role |
| Full System Access | Access and modify all data across all companies and LOV contexts |

#### 10.2 Role Addition
- Add `SUPER_USER` to the `role` ENUM in `user_company_roles` (or as a global flag on `users` — see data-model.md for chosen approach).
- `SUPER_USER` bypasses all tenant and LOV context filters.
- Only an existing Super User can grant `SUPER_USER` to another user.
- At least one Super User must exist at all times (enforced in service layer).

---

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Login with 3 mandatory LOV selections (Priority: P1)

A finance user opens the login screen. They must select Authority, Document Type, and Sub-Environment before the login button is enabled. After login, all data they see is scoped to those 3 selections.

**Acceptance Scenarios**:
1. **Given** a user on the login screen with nothing selected, **When** they view the login button, **Then** it is disabled.
2. **Given** a user selects ZATCA as Authority, **When** they open the Sub-Environment LOV, **Then** only Sandbox, Simulation, Production are shown (not Preprod).
3. **Given** a user selects ETA as Authority, **When** they look at Document Type, **Then** both Invoice and Receipt are available; when they select ZATCA, Receipt is hidden.
4. **Given** all 3 LOVs are selected and credentials are valid, **When** the user clicks Login, **Then** they are authenticated and land on the dashboard with a visible context badge showing their selections.
5. **Given** a logged-in user, **When** they log out and log back in selecting different LOV values, **Then** the data shown is different — matching only the new context.

---

### User Story 2 — Data isolation per LOV context (Priority: P1)

A Company Admin creates a customer named "Test Corp" while logged in under ZATCA/Invoice/Sandbox. When they log out and log back in under ETA/Invoice/Preprod, "Test Corp" is not visible.

**Acceptance Scenarios**:
1. **Given** Customer "X" created under context ZATCA/Invoice/Sandbox, **When** same user logs in under ETA/Invoice/Preprod, **Then** Customer "X" is not in the list.
2. **Given** an invoice created under ZATCA/Invoice/Sandbox, **When** the user switches context to ZATCA/Invoice/Production, **Then** that invoice is not visible.
3. **Given** a Super User, **When** they access customer or invoice lists, **Then** they see all records regardless of context (with a context column visible).

---

### User Story 3 — User and role management from User screen (Priority: P1)

A Super User creates a new user, assigns them to two companies with different roles, and grants specific permissions per company per context.

**Acceptance Scenarios**:
1. **Given** a Super User on the Users screen, **When** they create a user with duplicate email, **Then** an error "Email already exists" is shown and the user is not created.
2. **Given** a new user, **When** a Super User assigns them to Company A as ACCOUNTANT, **Then** the user can log in and see Company A data only.
3. **Given** a user assigned as ACCOUNTANT with `CREATE_INVOICE` revoked, **When** they log in and go to the invoice screen, **Then** the "New Invoice" button is hidden/disabled.
4. **Given** a Company Admin, **When** they access the Users screen, **Then** they receive a 403 — only Super User can access user management.

---

### User Story 4 — Branch-level address management (Priority: P2)

A Company Admin edits a branch and sets its full address. The company edit form no longer shows address fields.

**Acceptance Scenarios**:
1. **Given** a company edit form, **When** it is rendered, **Then** no address fields are present.
2. **Given** a branch edit form, **When** it is rendered, **Then** all 8 address fields are present and required per authority rules.
3. **Given** an invoice for a ZATCA branch without a branch address, **When** validation runs, **Then** a validation error "Branch address required for ZATCA" is returned.

---

### User Story 5 — Bulk upload templates for Items and Customers (Priority: P2)

An accountant downloads the items template, fills in 50 items, uploads the file, and sees a report showing 48 successes and 2 errors with row-level detail.

**Acceptance Scenarios**:
1. **Given** an accountant on the Items screen, **When** they click "Download Template", **Then** an `.xlsx` file downloads with the correct column headers for items.
2. **Given** an uploaded items file with one row missing the required `code` field, **When** the upload is processed, **Then** that row appears in the error report with message "Item code is required", and all valid rows are imported.
3. **Given** an upload file with wrong column headers (e.g., a customers template uploaded to items), **When** processed, **Then** the entire upload is rejected with "Template structure mismatch" before any rows are inserted.
