# Implementation Plan — Pre-Final Phase Review

**Phase**: 4 (Wave 4)
**Branch**: `005-pre-final-phase-review`
**Date**: 2026-04-20

---

## Delivery Strategy

The 10 requirements cluster into 5 implementation streams. Streams 1–2 are blocking (schema + security must come first). Streams 3–5 are parallelizable after the foundation lands.

| Stream | Items | Blocking? |
|--------|-------|-----------|
| S1 — Schema & LOV Context | §2 data model, §3 address move, Flyway V30–V34 | Yes — all others depend on this |
| S2 — Auth & Security | §1 login LOVs, JWT changes, §8 permissions model | Yes — UI and API depend on new JWT |
| S3 — Company/Branch/User UI | §4, §6, §7 | After S1+S2 |
| S4 — Permissions & Super User | §8, §10 | After S1+S2 |
| S5 — Bug Fixes + Bulk Templates | §5, §9 | After S1 (context FK must exist) |

---

## Stream S1 — Schema & LOV Context (Flyway Migrations)

1. V30: Create `lov_contexts` table; seed 7 valid combination rows.
2. V31: Add `lov_context_id BIGINT NOT NULL DEFAULT 1 REFERENCES lov_contexts(id)` to `customers`, `items`, `invoices`, and `branches`.
   - After backfill, drop the default on `branches` so all new branch inserts must supply a context explicitly.
   - Default value 1 = `ZATCA-INVOICE-SANDBOX` for existing dev rows.
3. V32: Remove address columns from `companies`; add address columns to `branches`.
   - Migrate existing company address data into the first branch of each company.
4. V33: Create `user_context_permissions` table.
5. V34: Add `SUPER_USER` to role enum; add `is_super_user BOOLEAN DEFAULT FALSE` to `users`.

**TenantContext update**: Extend Spring `TenantContext` ThreadLocal to carry `lovContextId` alongside `companyId`. All `@TenantScoped` base repositories gain an additional `AND lov_context_id = :lovContextId` filter (with bypass for Super User).

---

## Stream S2 — Authentication & Security

### `LovContextMapper` (platform-security)
- Static utility: `toAuthorityEnvironment(String authority, String subEnv): String`
- Returns the `authority_configs.environment` ENUM string for a given `(authority, sub_env)` pair.
- Uses the canonical mapping from data-model.md (e.g. ZATCA + SIMULATION → ZATCA_SIMULATION).
- Throws `IllegalArgumentException` for unknown combinations.
- Used by `AuthService.login(...)` and by `SubmissionOrchestrator` when resolving the active authority config.

### Login endpoint changes (`POST /api/auth/login`)
- Request body gains 3 new required fields: `authority`, `docType`, `subEnvironment`.
- Validate the combination against `lov_contexts` table; reject with 400 if invalid.
- Validate `subEnvironment` is legal for the given `authority` using `LovContextMapper`.
- On success: resolve `lov_context_id`; embed `active_authority`, `active_doc_type`, `active_sub_env`, `lov_context_id` in JWT.
- Embed `permissions[]` (list of granted permission keys for the active company+context).

### Angular login form
- Add 3 Angular Material `mat-select` dropdowns before the email/password fields.
- Dropdown 1 (Authority): static [ZATCA, ETA].
- Dropdown 2 (Document Type): reactive — shows [Invoice] for ZATCA, [Invoice, Receipt] for ETA.
- Dropdown 3 (Sub-Environment): reactive — shows authority-appropriate options.
- Login button bound to `[disabled]="!allLovsSelected || loginForm.invalid"`.
- Hide sidebar `<mat-sidenav>` when `!authService.isLoggedIn()`.

### JWT + interceptor
- HTTP interceptor attaches `X-Lov-Context: {lovContextId}` header on every API call.
- Backend `TenantFilter` reads this header alongside JWT; validates it matches JWT claim.

---

## Stream S3 — Company / Branch / User UI

### Company list (Angular `ConfigModule`)
- Add **Edit** button per row → navigates to `/config/companies/:id/edit`.
- Add **Branches** button per row → navigates to `/config/companies/:id/branches`.

### Branch list screen (`/config/companies/:id/branches`)
- New route + component.
- Lists branches with Edit button per row.
- "New Branch" FAB creates new branch under the company.
- Branch form includes all 8 address fields (moved from company form).
- Company form: remove address field group.

### User Management screen (Super User only)
- New route: `/admin/users`.
- Listed in sidebar only when `user.isSuperUser`.
- Table: Name, Email, Status, Assigned Companies, Actions (Edit / Deactivate).
- Create/Edit dialog: Name, Email, Password (masked).
- Assign Companies panel: checkbox list of all companies with role selector per company.

### Remove "Assign Users" from Company screen
- Remove the existing user-assignment section/tab from the company detail page.

---

## Stream S4 — Permissions & Super User

### Backend: PermissionService
- `PermissionService.getPermissions(userId, companyId, lovContextId)` → `Set<Permission>`.
- Cached per request in ThreadLocal (cleared after each request).
- Super User check: `if (user.isSuperUser()) return ALL_PERMISSIONS`.

### `@RequiresPermission` annotation
- Custom Spring Security annotation: `@RequiresPermission(Permission.CREATE_INVOICE)`.
- AOP aspect checks `PermissionService` and throws `AccessDeniedException` if not granted.
- Apply to all 14 permission-gated service methods.

### Super User enforcement
- `UserService.promoteToSuperUser(actorId, targetUserId)` — validates actor is Super User.
- `UserService.deleteUser(actorId, targetUserId)` — prevents deletion of last Super User.

### Angular: permission-driven UI
- `AuthService.hasPermission(key: string): boolean` reads `permissions[]` from JWT.
- Structural directive `*appHasPermission="'CREATE_INVOICE'"` hides elements.
- Apply to: New Invoice button, New Customer button, New Item button, Edit/Delete actions.

---

## Stream S5 — Bug Fixes + Bulk Upload Templates

### BF-01: Customer creation fix
- Reproduce the error (likely: missing `lov_context_id` in DTO after S1, or constraint failure).
- Fix DTO, service, and repository to include `lov_context_id` from `TenantContext`.

### BF-02: Item creation fix
- Same investigation pattern as BF-01.

### Bulk Upload — Items Screen
- Backend: `GET /api/items/template` (already exists from Wave 1 — verify headers match data-model.md).
- Backend: `POST /api/items/bulk-upload` — validates column headers, processes rows, returns `BulkUploadResult`.
- Angular: "Download Template" button calls `GET /api/items/template`.
- Angular: "Upload Template" button opens file picker, POSTs to `/api/items/bulk-upload`, shows result dialog.

**`ItemService.createBatch(List<ItemRequest> rows, Long companyId, Long lovContextId): BulkUploadResult`**
- Iterates rows; for each row: validates required fields, checks uniqueness of `code` within `(companyId, lovContextId)`.
- **Upsert semantics**: if a row's `code` matches an existing item in the same `(companyId, lovContextId)`, **update** that item; otherwise **create** a new one.
- Collects row-level errors; continues processing valid rows regardless of failures (best-effort).
- Returns `BulkUploadResult { int processed, int failed, List<RowError> errors }` where `RowError = { int row, String field, String message }`.
- Entire operation is wrapped in a single transaction; if the header validation fails, no rows are inserted.

**`CustomerService.createBatch(List<CustomerRequest> rows, Long companyId, Long lovContextId): BulkUploadResult`**
- Same contract. Upsert key: VAT number for B2B, or email for B2C.

### Bulk Upload — Customers Screen
- Same pattern: `GET /api/customers/template` + `POST /api/customers/bulk-upload`.
- Angular: same two buttons.

---

## Exit Criteria

- Login screen: sidebar hidden; 3 LOVs required; Sub-Environment adapts to Authority.
- Data isolation: customer created in context A not visible in context B (integration test).
- Address: company form has no address fields; branch form has all address fields.
- Company list: Edit and Branches buttons functional.
- Branch screen: can create, view, edit branches per company.
- User management: Super User can CRUD users and assign roles.
- Permissions: user with `CREATE_INVOICE` revoked cannot create invoice (403 + hidden button).
- Super User: only Super User can promote others; last Super User cannot be deleted.
- BF-01: customer creation succeeds end-to-end.
- BF-02: item creation succeeds end-to-end.
- Bulk upload: valid template uploads succeed; structure mismatch rejected before row processing.
