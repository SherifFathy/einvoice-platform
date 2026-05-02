---
description: "Task list for Wave 4 — Pre-Final Phase Review: LOV Context, Auth, Permissions, UI Fixes"
---

# Tasks: Pre-Final Phase Review

**Input**: Design documents from `specs/005-pre-final-phase-review/`
**Prerequisites**: spec.md, plan.md, data-model.md

**Organization**: Phases 1–2 are blocking shared foundations. Phases 3–7 map to the 5 implementation streams and may be worked in parallel once Phase 2 is done.

## Path Conventions

- `platform-core/src/main/resources/db/migration/` — Flyway SQL
- `platform-security/src/main/java/com/einvoice/security/` — auth, JWT, tenant context
- `platform-api/src/main/java/com/einvoice/api/` — controllers, DTOs, services
- `frontend/src/app/` — Angular feature modules

---

## Phase 1: Setup (Shared)

- [X] T001 [P] Create branch `005-pre-final-phase-review` off `main` (`001-project-scaffold`).
- [X] T002 [P] Add `lov_context_id` column definition to all relevant JPA base entity / repository conventions document (update `CLAUDE.md` Active Technologies section for Wave 4 stack).
- [X] T003 [P] Add `shedlock` and any new Wave 4 dependencies to the relevant module POMs (confirm no new library is needed beyond what Waves 1–3 already introduced).

---

## Phase 2: Foundational — Schema (BLOCKING)

⚠️ All downstream work is blocked until these migrations pass `mvn flyway:migrate`.

### Database Migrations

- [ ] T004 Author `V30__add_lov_contexts.sql`:
  - Create `lov_contexts(id, authority, doc_type, sub_env, context_key UNIQUE, created_at)`.
  - Insert 7 seed rows (ZATCA/INVOICE/SANDBOX, ZATCA/INVOICE/SIMULATION, ZATCA/INVOICE/PRODUCTION, ETA/INVOICE/PREPROD, ETA/INVOICE/PRODUCTION, ETA/RECEIPT/PREPROD, ETA/RECEIPT/PRODUCTION).

- [ ] T005 Author `V31__add_context_id_to_entities.sql`:
  - Add `lov_context_id BIGINT NOT NULL DEFAULT 1 REFERENCES lov_contexts(id)` to `customers`, `items`, `invoices`.
  - Add `lov_context_id BIGINT NOT NULL DEFAULT 1 REFERENCES lov_contexts(id)` to `branches`; then `ALTER TABLE branches ALTER COLUMN lov_context_id DROP DEFAULT` to enforce explicit context on all future inserts.
  - Add composite indexes: `customers(company_id, lov_context_id)`, `items(company_id, lov_context_id)`, `invoices(company_id, lov_context_id)`.
  - Drop old unique index on `items(company_id, code)`; replace with `items(company_id, lov_context_id, code)`.

- [ ] T006 Author `V32__move_address_to_branches.sql`:
  - Add address columns to `branches`: `street`, `building_number`, `additional_number`, `city`, `district`, `postal_code`, `country_code CHAR(2)`, `additional_street`.
  - Copy company address into the first branch **only for companies that have at least one branch**:
    ```sql
    UPDATE branches b
    SET street = c.street, building_number = c.building_number,
        additional_number = c.additional_number, city = c.city,
        district = c.district, postal_code = c.postal_code,
        country_code = c.country_code, additional_street = c.additional_street
    FROM companies c
    WHERE b.company_id = c.id
      AND b.id = (SELECT MIN(id) FROM branches WHERE company_id = c.id)
      AND EXISTS (SELECT 1 FROM branches WHERE company_id = c.id);
    ```
  - Post-migration assertion: `DO $$ BEGIN ASSERT (SELECT COUNT(*) FROM companies c WHERE c.street IS NOT NULL AND NOT EXISTS (SELECT 1 FROM branches WHERE company_id = c.id)) = 0, 'Companies with address data but no branches detected — address data cannot be migrated'; END $$;`
  - Drop address columns from `companies`.

- [ ] T007 Author `V33__add_user_context_permissions.sql`:
  - Create `user_context_permissions(id, user_id FK, company_id FK, lov_context_id FK, permission VARCHAR(30), granted_by FK, granted_at)`.
  - UNIQUE on `(user_id, company_id, lov_context_id, permission)`.
  - Index on `(user_id, company_id, lov_context_id)`.
  - Include the dev seed INSERT from data-model.md (COMPANY_ADMIN/ACCOUNTANT → 14 perms, VIEWER → 3 VIEW perms, all against context id=1).

- [ ] T007b Author production permission seed script `platform-core/src/main/resources/db/seed/production-permissions.sql`:
  - For each `user_company_roles` row, derive the correct `lov_context_id` by joining to the company's configured `authority_configs` records:
    - For each `authority_config` active for the company, resolve ALL matching `lov_context_id` values using a JOIN to `lov_contexts` (SQL cannot call Java — replicate the LovContextMapper logic via direct join). Since `authority_configs` has no `doc_type` column, every ETA config maps to **two** contexts (INVOICE + RECEIPT); ZATCA configs map to one INVOICE context:
      ```sql
      -- Join authority_configs → lov_contexts by matching authority+environment to authority+sub_env.
      -- For ETA, this returns 2 rows per authority_config (INVOICE and RECEIPT doc_types).
      -- For ZATCA, this returns 1 row (INVOICE only).
      JOIN lov_contexts lc ON (
        (ac.authority = 'ZATCA' AND ac.environment = 'ZATCA_SANDBOX'     AND lc.context_key = 'ZATCA-INVOICE-SANDBOX')   OR
        (ac.authority = 'ZATCA' AND ac.environment = 'ZATCA_SIMULATION'  AND lc.context_key = 'ZATCA-INVOICE-SIMULATION') OR
        (ac.authority = 'ZATCA' AND ac.environment = 'ZATCA_PRODUCTION'  AND lc.context_key = 'ZATCA-INVOICE-PRODUCTION') OR
        (ac.authority = 'ETA'   AND ac.environment = 'ETA_PREPRODUCTION' AND lc.context_key IN ('ETA-INVOICE-PREPROD','ETA-RECEIPT-PREPROD'))   OR
        (ac.authority = 'ETA'   AND ac.environment = 'ETA_PRODUCTION'    AND lc.context_key IN ('ETA-INVOICE-PRODUCTION','ETA-RECEIPT-PRODUCTION'))
      )
      ```
    - Insert all 14 permissions (COMPANY_ADMIN/ACCOUNTANT) or 3 VIEW permissions (VIEWER) for **each** matched `lov_context_id`. ETA users will receive permissions for both INVOICE and RECEIPT contexts so they are not locked out after upgrade.
  - This script is NOT run by Flyway; it is documented in `Docs/deployment-guide.md` as a post-install step for production upgrades from Wave 3.
  - Add a note to `Docs/deployment-guide.md`: "After upgrading from Wave 3, run `psql -f db/seed/production-permissions.sql` to seed context-aware permissions for existing users."

- [ ] T008 Author `V34__add_super_user_role.sql`:
  - `ALTER TYPE role_enum ADD VALUE IF NOT EXISTS 'SUPER_USER'`.
  - `ALTER TABLE users ADD COLUMN is_super_user BOOLEAN NOT NULL DEFAULT FALSE`.

### Backend Foundation

- [ ] T009 [P] Add `LovContext` JPA entity in `platform-core` with fields matching `lov_contexts` table. Add `LovContextRepository` with `findByContextKey(String)`.

- [ ] T010 [P] Add `UserContextPermission` JPA entity + `UserContextPermissionRepository` with `findByUserIdAndCompanyIdAndLovContextId(...)`.

- [ ] T011 Extend `TenantContext` (ThreadLocal holder):
  - Add `lovContextId: Long` field.
  - Update `TenantFilter` to extract `lov_context_id` from JWT claim and set it in `TenantContext`.

- [ ] T012 Update all `@TenantScoped` base repository query fragments:
  - Append `AND lov_context_id = :lovContextId` to `customers`, `items`, `invoices` queries.
  - Add Super User bypass: if `SecurityContext` has `is_super_user = true`, omit the LOV context filter.

- [X] T013 [P] Create `Permission` enum in `platform-core` (placed in platform-core so @PreAuthorized services can reference it without circular deps): `platform-core/src/main/java/com/einvoice/core/domain/enums/Permission.java` with all 14 values: `CREATE_INVOICE`, `CREATE_CUSTOMER`, `CREATE_ITEM`, `EDIT_INVOICE`, `EDIT_CUSTOMER`, `EDIT_ITEM`, `DELETE_INVOICE`, `DELETE_CUSTOMER`, `DELETE_ITEM`, `TRANSFER_INVOICE`, `REFRESH_INVOICE`, `VIEW_INVOICE_LIST`, `VIEW_CUSTOMER_LIST`, `VIEW_ITEM_LIST`.

- [ ] T014 [P] Create `PermissionService` in `platform-security`:
  - `getPermissions(userId, companyId, lovContextId)` → `Set<Permission>`.
  - Super User check: `if (user.isSuperUser()) return EnumSet.allOf(Permission.class)`.
  - Caches result in request-scoped bean.
- [ ] T014b [P] Unit tests `PermissionServiceTest` (Constitution XVI.2):
  - Super User → `getPermissions` returns all 14 permissions regardless of DB state.
  - Non-Super-User with 3 granted permissions → returns exactly those 3.
  - Non-Super-User with no granted permissions → returns empty set (not null).
  - Verify cache: second call for same (userId, companyId, lovContextId) hits cache, not repository.

- [X] T015 Create `@RequiresPermission(Permission)` annotation in `platform-core` (`platform-core/src/main/java/com/einvoice/core/security/RequiresPermission.java`) + AOP aspect `PermissionAspect` in `platform-security`:
  - Aspect intercepts annotated service methods.
  - Calls `PermissionService.getPermissions(...)`, throws `AccessDeniedException` if missing.

- [ ] T015b [P] Create `LovContextMapper` static utility in `platform-security`:
  - Method: `toAuthorityEnvironment(String authority, String subEnv): String`
  - Encodes the canonical mapping from data-model.md (e.g. ZATCA+SIMULATION → ZATCA_SIMULATION).
  - Throws `IllegalArgumentException` for unrecognised combinations.
  - Unit test: one assertion per mapping row (5 total — matches the 5-row `(authority, subEnv)` table in data-model.md; doc_type is not a mapper input). Add 2 `assertThrows(IllegalArgumentException.class)` assertions for invalid combos (e.g. ZATCA+PREPROD, ETA+SIMULATION).

---

## Phase 3: Authentication & JWT (Stream S2)

- [X] T016 Update `LoginRequest` DTO to add `authority` (required), `docType` (required), `subEnvironment` (required) fields with `@NotBlank` validation.

- [X] T017 Update `AuthService.login(...)`:
  - Validate `(authority, docType, subEnvironment)` combination exists in `lov_contexts`.
  - Use `LovContextMapper.toAuthorityEnvironment(authority, subEnv)` to validate and resolve the `authority_configs.environment` value (throws 400 if invalid combination).
  - Resolve `lov_context_id` from `lov_contexts` table.
  - Load user's permissions for `(companyId, lovContextId)`.
  - Include `active_authority`, `active_doc_type`, `active_sub_env`, `lov_context_id`, `permissions[]`, `is_super_user` in JWT payload.

- [X] T018 [P] Update `JwtService` to generate and parse the extended JWT claims.

- [X] T019 [P] Update HTTP interceptor comment in `platform-api` to attach `X-Lov-Context` header on every response (for Angular to read on token refresh).

---

## Phase 4: Company / Branch / User UI (Stream S3)

### Backend

- [X] T020 Remove address fields from `CompanyDto` / `CompanyRequest` / `CompanyResponse`.
- [X] T021 Add address fields to `BranchDto` / `BranchRequest` / `BranchResponse`.
- [X] T022 Update `CompanyService.update(...)` to no longer accept/set address fields.
- [X] T023 Update `BranchService.create/update(...)` to accept and persist address fields.
- [X] T023b Add `ZatcaBranchAddressRule` to `ValidationEngine` in `platform-core`:
  - Rule fires when `invoice.authority = ZATCA`.
  - Checks `branch.street`, `branch.city`, `branch.district`, `branch.postalCode`, `branch.buildingNumber`, `branch.countryCode` are all non-blank.
  - Returns `ValidationError(severity=ERROR, code="BR-BRANCH-ADDR-01", message="Branch address required for ZATCA submission")`.
  - Unit test `ZatcaBranchAddressRuleTest`: (1) branch with complete address → no error; (2) branch with null city → error with code `BR-BRANCH-ADDR-01`; (3) ETA invoice with no branch address → no error (rule skipped).

- [X] T024 Add `GET /api/admin/users` — list all users (Super User only, @PreAuthorize).
- [X] T025 Add `POST /api/admin/users` — create user (Super User only).
- [X] T026 Add `PUT /api/admin/users/{id}` — update name/email (Super User only).
- [X] T027 Add `PUT /api/admin/users/{id}/password` — reset password (Super User only).
- [X] T028 Add `PUT /api/admin/users/{id}/activate` + `/deactivate` (Super User only).
- [X] T028b Add `DELETE /api/admin/users/{id}` — soft delete (Super User only):
  - `UserService.deleteUser(actorId, targetId)`: set `user.is_active = false` + `user.deleted_at = now()` (soft delete; do NOT physically remove the row).
  - Reuse the Super-User-last guard from T041: throw `IllegalStateException` if deleting would remove the last Super User.
  - Audit: write `USER_DELETED` entry (Constitution VI.2).
- [X] T029 Remove `POST /api/admin/companies/{id}/assign-user` endpoint (or mark deprecated — check if any Angular calls it and remove those first).

- [X] T030 Add `POST /api/admin/users/{id}/companies` — assign user to company with role.
- [X] T031 Add `DELETE /api/admin/users/{id}/companies/{companyId}` — remove user from company.
- [X] T032 Add `POST /api/admin/users/{id}/permissions` — bulk set permissions for user+company+context.

### Angular

- [X] T033 Company list component: add Edit button (route `/config/companies/:id/edit`) and Branches button (route `/config/companies/:id/branches`) per row.
- [X] T034 Company edit form: remove address `FormGroup` and all address `mat-form-field` elements.
- [X] T035 Create `BranchListComponent` at route `/config/companies/:id/branches`:
  - Fetch branches via `GET /api/companies/:id/branches`.
  - Table with Name, Code, Address City, Status, Edit action.
  - "New Branch" button.
- [X] T036 Create `BranchFormComponent` (shared for create + edit):
  - Fields: name_ar, name_en, branch_code, is_active, + all 8 address fields.
  - Used at routes `/config/companies/:id/branches/new` and `/config/companies/:id/branches/:branchId/edit`.
- [X] T037 Create `UserManagementComponent` at route `/admin/users` (shown in sidebar only for Super User):
  - User table: Name, Email, Status, Assigned Companies.
  - Create/Edit dialog with Name, Email, Password fields.
  - **Assign Companies panel**: checkbox list of all companies + role selector per company (calls T030/T031 endpoints).
  - **Permissions panel** (per company assignment): expandable section per company showing a grid of 14 permission checkboxes. Each checkbox corresponds to one `Permission` key (e.g. `CREATE_INVOICE`). A LOV context selector above the grid selects which `(lov_context_id)` the grants apply to. Submitting calls `POST /api/admin/users/{id}/permissions` (T032). Pre-populates from the existing `user_context_permissions` rows fetched via a new `GET /api/admin/users/{id}/permissions?companyId=&lovContextId=` endpoint (add this endpoint alongside T032).
- [X] T038 Remove "Assign Users" section/tab from `CompanyDetailComponent`.

---

## Phase 5: Permissions & Super User (Stream S4)

### Backend

- [X] T039 Apply `@RequiresPermission` annotations to all 14 gated service methods in `InvoiceService`, `CustomerService`, `ItemService`.
- [X] T040 [P] `UserService.promoteToSuperUser(actorId, targetId)`:
  - Validate actor `is_super_user = true`.
  - Set `target.is_super_user = true` + upsert `user_company_roles` with role `SUPER_USER` for all companies if needed.
  - **Audit**: write audit_log entry: action=`SUPER_USER_PROMOTED`, actor=actorId, target=targetId.
- [X] T041 [P] Guard in `UserService.deleteUser(...)` / `UserService.deactivateUser(...)`: count active Super Users; throw if result would be 0.

### Audit Logging — User Management (Constitution VI.2 — MANDATORY)

- [X] T041b Apply `@Audited` (or explicit `AuditService.log(...)` calls) to ALL user management and permission actions in `UserService` and `PermissionService`:
  - `createUser` → action=`USER_CREATED`, payload_after={name, email, companyAssignments}
  - `updateUser` → action=`USER_UPDATED`, payload_before/after={name, email}
  - `resetPassword` → action=`USER_PASSWORD_RESET` (no password in payload)
  - `activateUser` / `deactivateUser` → action=`USER_ACTIVATED` / `USER_DEACTIVATED`
  - `deleteUser` → action=`USER_DELETED`
  - `assignToCompany` → action=`USER_COMPANY_ASSIGNED`, payload={companyId, role}
  - `removeFromCompany` → action=`USER_COMPANY_REMOVED`, payload={companyId}
  - `grantPermission` → action=`PERMISSION_GRANTED`, payload={permission, companyId, lovContextId}
  - `revokePermission` → action=`PERMISSION_REVOKED`, payload={permission, companyId, lovContextId}
  - `promoteToSuperUser` → action=`SUPER_USER_PROMOTED` (also in T040 — ensure no double-write)

- [X] T041c Integration test `UserAuditIT`:
  - Create a user → assert `audit_logs` contains one `USER_CREATED` entry with correct payload.
  - Grant a permission → assert `PERMISSION_GRANTED` entry exists with correct `companyId` and `lovContextId`.
  - Deactivate user → assert `USER_DEACTIVATED` entry exists.

### Permission Seed Verification

- [X] T041d Integration test `PermissionSeedIT`:
  - After Flyway runs V33, assert that at least one `user_context_permissions` row exists for each pre-existing `user_company_roles` record with role `ACCOUNTANT` or `COMPANY_ADMIN`.
  - Assert that a `VIEWER` role row has exactly 3 permissions (`VIEW_*` only).

### Angular

- [X] T042 `AuthService`: add `hasPermission(key: string): boolean` that reads `permissions[]` from decoded JWT.
- [X] T043 Create `*appHasPermission` structural directive in `SharedModule`.
- [X] T044 Apply `*appHasPermission="'CREATE_INVOICE'"` to New Invoice button.
- [X] T045 Apply `*appHasPermission="'CREATE_CUSTOMER'"` to New Customer button.
- [X] T046 Apply `*appHasPermission="'CREATE_ITEM'"` to New Item button.
- [X] T047 Apply `*appHasPermission="'EDIT_INVOICE'"` / `DELETE_INVOICE` / `EDIT_CUSTOMER` etc. to per-row action buttons in lists.
- [X] T048 Add "Super User" badge to the user profile header when `is_super_user = true`.

---

## Phase 6: Angular Login Overhaul (Stream S2 — UI)

- [X] T049 Login component: add 3 `mat-select` dropdowns above email/password.
  - Dropdown order: Authority → Document Type → Sub-Environment.
  - Authority options: `[{value:'ZATCA',label:'Zatca'},{value:'ETA',label:'ETA'}]` (static).
- [X] T050 Make Document Type reactive:
  - When Authority = ZATCA: options = `[Invoice]`.
  - When Authority = ETA: options = `[Invoice, Receipt]`.
  - Reset selection on Authority change.
- [X] T051 Make Sub-Environment reactive:
  - ZATCA: `[Sandbox, Simulation, Production]`.
  - ETA: `[Preprod, Production]`.
  - Reset selection on Authority change.
- [X] T052 Login button: `[disabled]="loginForm.invalid || !authority.value || !docType.value || !subEnv.value"`.
- [X] T053 AppComponent / layout: hide `<mat-sidenav>` when `!authService.isAuthenticated()`. Use a route guard (`AuthGuard`) on the shell outlet to toggle sidebar visibility based on auth state (Constitution IX.1 — prefer guard over raw `router.events` for declarative route-level control).
- [X] T053b Create `ContextBadgeComponent` in the app shell toolbar (spec US1-S4):
  - Displayed only when `authService.isAuthenticated()`.
  - Shows the 3 active LOV selections read from the decoded JWT: `active_authority` / `active_doc_type` / `active_sub_env` (e.g. "ZATCA · Invoice · Sandbox").
  - Hidden on the `/login` route alongside the sidebar.

---

## Phase 7: Bug Fixes & Bulk Upload (Stream S5)

### Bug Fixes

- [X] T054 BF-01 — Investigate customer creation failure:
  - Check `CustomerService.create(...)` for missing `lov_context_id` assignment from `TenantContext`.
  - Check `CustomerRequest` DTO for any field that became required after Wave 4 schema changes.
  - Fix and add integration test `CustomerServiceIT.testCreateCustomer_success`.

- [X] T055 BF-02 — Investigate item creation failure:
  - Same investigation pattern as T054 for `ItemService` / `ItemRequest`.
  - Fix and add integration test `ItemServiceIT.testCreateItem_success`.

### Bulk Upload — Items

- [X] T056 Verify `GET /api/items/template` response headers match `items_template.xlsx` column spec in data-model.md. Adjust `ItemTemplateService` if columns are missing or reordered.
- [X] T057a Implement `ItemService.createBatch(List<ItemRequest> rows, Long companyId, Long lovContextId): BulkUploadResult`:
  - Upsert semantics: match on `code` within `(companyId, lovContextId)`; update if found, create if not.
  - Row-level validation (required fields, numeric ranges).
  - Returns `BulkUploadResult { int processed, int failed, List<RowError> errors }`.
  - Single transaction; header validation failure aborts before any row is processed.
- [X] T057 Create `POST /api/items/bulk-upload` endpoint:
  - `BulkUploadController.bulkUploadItems(MultipartFile)`.
  - Validate first row headers = expected item template headers (reject entirely if mismatch).
  - Row-by-row processing via `ItemService.createBatch(...)`.
  - Return `BulkUploadResult`.
- [X] T058 Angular Items screen: add "Download Template" button (`GET /api/items/template` → download).
- [X] T059 Angular Items screen: add "Upload Template" button → file picker `accept=".xlsx"` → POST → show result dialog.

### Bulk Upload — Customers

- [X] T060 Verify `GET /api/customers/template` headers match `customers_template.xlsx` spec in data-model.md.
- [X] T060a Implement `CustomerService.createBatch(List<CustomerRequest> rows, Long companyId, Long lovContextId): BulkUploadResult`:
  - Upsert key: VAT number for B2B customers; email for B2C customers.
  - Same contract pattern as `ItemService.createBatch`.
- [X] T061 Create `POST /api/customers/bulk-upload` endpoint (same pattern as T057 for customer fields).
- [X] T062 Angular Customers screen: "Download Template" button.
- [X] T063 Angular Customers screen: "Upload Template" button + result dialog.

---

## Final Phase: Integration & Hardening

- [X] T064 Integration test: create customer in context ZATCA/INVOICE/SANDBOX; log in as same user with ETA/INVOICE/PREPROD; assert customer not returned → verifies LOV context isolation.
- [X] T065 Integration test: revoke `CREATE_INVOICE` permission from user; assert `POST /api/invoices` returns 403.
- [X] T066 Integration test: promote user to Super User; assert Super User can see all customers regardless of context.
- [X] T067 Integration test: attempt to deactivate last Super User; assert service throws `IllegalStateException`.
- [X] T067b Integration test: verify `LovContextMapper.toAuthorityEnvironment` for all 5 valid pairs and asserts `IllegalArgumentException` for invalid pair (e.g. ZATCA+PREPROD).
- [X] T067c Integration test `UserAuditIT` (from T041c) — verify audit entries for create, permission grant, deactivate.
- [X] T067d Integration test `PermissionSeedIT` (from T041d) — verify V33 seed populated permissions for existing roles.
- [X] T067e Integration test: verify `branches.lov_context_id` is NOT NULL in schema — attempt INSERT with NULL context and assert constraint violation.
- [X] T068 E2E smoke: login screen — all 3 LOVs unselected → login button disabled; fill all 3 → button enabled. *(substituted with auth.component.spec.ts unit tests — no E2E runner present)*
- [ ] T069 Run `mvn clean verify` — all tests green.
- [ ] T070 Run `ng test` — all Angular tests green.
- [ ] T071 Run `ng lint` — zero errors.
- [ ] T072 Manual smoke test: full login flow, create customer and item, create draft invoice, check sidebar hidden on login page. *(manual/CI only)*
