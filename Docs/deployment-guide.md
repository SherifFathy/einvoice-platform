# Deployment Guide

## Wave 5 — Fresh Database Deployment

Wave 5 (V37–V43) drops every Wave 0–4 operational table and rebuilds the schema.
There is **no data migration path** — the platform has not yet been released to
live customers (FR-048, FR-049). Deploy to a **fresh database** only.

### Required Environment Variables

| Variable | Notes |
|----------|-------|
| `BOOTSTRAP_SUPERUSER_EMAIL` | Must be set before first start. Email for the bootstrap Super User inserted by `V44__bootstrap_super_user.sql`. |
| `BOOTSTRAP_SUPERUSER_PASSWORD_HASH` | Must be set before first start. BCrypt hash. See `configuration-reference.md` for generation recipe. |
| `JWT_TTL_SECONDS` | Optional; defaults to `28800` (8 h). Accepted range: `[60, 86400]`. |
| `JWT_SECRET` | Required. Base64-encoded HMAC-SHA256 secret. |

### Deployment Steps

1. **Tear down the old database**: `docker compose down -v` (removes the PostgreSQL volume).
2. **Start PostgreSQL**: `docker compose up -d db`.
3. **Set the environment variables** listed above in the backend's environment (`.env` file, container env, or shell exports).
4. **Start the backend**: `mvn -pl platform-api -am spring-boot:run`. Flyway will apply V37–V43 automatically.
5. **Verify migrations**: backend log must contain `Successfully applied N migrations to schema "public"` with no errors.
6. **Bootstrap Super User**: the versioned migration `V44__bootstrap_super_user.sql` inserts the Super User idempotently (`ON CONFLICT (email) DO NOTHING`). If either env var is missing or set to placeholder values, it logs a warning and skips — intended for CI only.

### New Endpoint Surface

| Method | Path | Auth | Notes |
|--------|------|------|-------|
| POST | `/api/auth/environments` | Public | List active environments for an authority |
| POST | `/api/auth/companies` | Public | List companies for a user+authority+environment |
| POST | `/api/auth/login` | Public | Issue JWT |
| POST | `/api/auth/logout` | Authenticated | No-op 204 (client discards token) |
| GET | `/api/session/context` | Authenticated | Full session-context payload (permissions, modules, companies) |
| GET/POST | `/api/admin/companies` | Super User | Admin CRUD |
| PUT | `/api/admin/companies/{id}` | Super User | Update company |
| PUT | `/api/admin/companies/{id}/deactivate` | Super User | Deactivate company |
| GET/POST | `/api/admin/companies/{id}/branches` | Super User | Branch CRUD |
| PUT | `/api/admin/branches/{id}` | Super User | Update branch |
| GET/POST | `/api/admin/users` | Super User | User CRUD |
| PUT | `/api/admin/users/{id}` | Super User | Update user |
| PUT | `/api/admin/users/{id}/activate` | Super User | Activate user |
| PUT | `/api/admin/users/{id}/deactivate` | Super User | Deactivate user |
| GET/POST | `/api/admin/users/{id}/assignments` | Super User | Assignment CRUD |
| DELETE | `/api/admin/users/{id}/assignments/{assignmentId}` | Super User | Remove assignment |

### LAST_SUPER_USER_PROTECTED Recovery

If the sole active Super User is accidentally deactivated (or their `isSuperUser` flag is cleared), the platform will reject any further mutation that would leave zero active Super Users (409 `LAST_SUPER_USER_PROTECTED`).

**Manual recovery** (requires database access):

```sql
-- Replace <email> with the target user's email
UPDATE users
SET is_super_user = true, is_active = true
WHERE email = '<email>';
```

Restart the backend (or wait for the next request) and log in with that user.

## Upgrading from Wave 3

> **Obsolete.** The Wave 3 → Wave 4 upgrade path (seed script joining
> `user_company_roles` / `authority_configs` / `lov_contexts`) no longer applies
> because Wave 5 drops all three tables. The section is retained for historical
> reference only.
