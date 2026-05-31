# Quickstart — Wave 4: Pre-Final Phase Review

## What changed from Wave 3

1. Login now requires 3 LOV dropdowns before authentication.
2. All tenant-scoped data (customers, items, invoices, branches) carries a `lov_context_id` FK.
3. Company address fields moved to Branch.
4. New User Management screen (Super User only) replaces company-level user assignment.
5. 14 fine-grained permissions replace coarse role-only access control.
6. Super User role added with system-wide bypass.
7. Bulk upload template actions added to Items and Customers screens.

## Where to start

1. Run migrations: `mvn flyway:migrate` — creates V30–V34. Verify with `psql -c "SELECT * FROM lov_contexts"` (should show 7 rows).
2. Restart backend: `TenantFilter` now sets `lovContextId` from JWT. Without a valid JWT containing `lov_context_id`, all tenant-scoped queries will throw.
3. Test login: call `POST /api/auth/login` with `{"email":"...","password":"...","authority":"ZATCA","docType":"INVOICE","subEnvironment":"SANDBOX"}`. Confirm JWT contains `lov_context_id`, `permissions[]`, `is_super_user`.
4. Angular: run `ng serve`. Login page should show 3 dropdowns. Sidebar should be absent until login.

## Dev seed data (for local testing)

After migrations, insert a test Super User:
```sql
INSERT INTO users (name, email, password_hash, is_active, is_super_user)
VALUES ('Admin', 'admin@dev.local', '<bcrypt-of-admin123>', true, true);
```

## Key files to know

| File | Purpose |
|------|---------|
| `platform-security/.../TenantContext.java` | Now carries `lovContextId` |
| `platform-security/.../TenantFilter.java` | Reads `lov_context_id` from JWT |
| `platform-security/.../PermissionService.java` | Returns permission set for current user+company+context |
| `platform-security/.../Permission.java` | All 14 permission keys as enum |
| `platform-api/.../AuthController.java` | Extended login endpoint |
| `frontend/src/app/auth/login/` | 3-LOV login form |
| `frontend/src/app/admin/users/` | New user management screen |
| `frontend/src/app/shared/directives/has-permission.directive.ts` | `*appHasPermission` directive |
