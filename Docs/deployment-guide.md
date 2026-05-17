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

---

## Wave 6 — Master Data and Certificate Configurations

Wave 6 adds seven operational tables for authority-separated customers, items, and certificate configurations. Migrations V45–V47 apply on top of the Wave 5 V44 baseline.

### Deployment Notes

- **Migrations**: V45 (ETA master data + ETA config), V46 (ZATCA master data + ZATCA config + ZATCA chain state), V47 (compound indexes). These run automatically via Flyway on backend startup — no manual SQL execution needed.
- **No new environment variables**: Wave 6 does not introduce any new env vars. Existing `JWT_SECRET`, `SPRING_DATASOURCE_*`, `BOOTSTRAP_SUPERUSER_*` settings are unchanged. `ENCRYPTION_MASTER_KEY` remains configured in `application.yml` from Wave 2 scaffolding but is **reserved for the Wave 7+ AES-256-GCM field-level encryption upgrade** — it is not wired to any encryption logic in Wave 6 (Constitution XVIII Phase 1 trade-off; all secrets/keys/PEMs stored as plain TEXT per FR-026).
- **No new infrastructure components**: No new Docker services, message queues, or external dependencies. Wave 6 uses the existing PostgreSQL instance and the existing Spring Boot application.
- **No data migration**: Wave 6 creates new empty tables. There is no data to migrate from earlier waves.
- **Save-blind configuration**: Configuration saves perform required-field and length validation only (FR-014a, FR-018a). The platform does not contact the authority during save; bad URLs or credentials surface at first submission.

### Verifying V45–V47 Applied

After deploying, confirm the migrations applied:

```sql
SELECT version, description, installed_on
FROM flyway_schema_history
WHERE version IN ('45', '46', '47')
ORDER BY installed_rank;
```

Verify the seven new tables exist:

```sql
SELECT table_name FROM information_schema.tables
WHERE table_schema = 'public'
  AND table_name IN (
    'eta_customers', 'eta_items',
    'zatca_customers', 'zatca_items',
    'eta_configs', 'zatca_configs',
    'zatca_chain_state'
  );
```

### New Endpoint Surface (Wave 6)

| Method | Path | Permission | Notes |
|--------|------|------------|-------|
| GET | `/api/companies/{id}/eta/customers` | `CUSTOMERS/VIEW` | List ETA customers (paginated) |
| POST | `/api/companies/{id}/eta/customers` | `CUSTOMERS/CREATE` | Create ETA customer |
| GET | `/api/companies/{id}/eta/customers/{customerId}` | `CUSTOMERS/VIEW` | Get ETA customer by ID |
| PUT | `/api/companies/{id}/eta/customers/{customerId}` | `CUSTOMERS/EDIT` | Update ETA customer (set `isActive: false` to deactivate per FR-007) |
| DELETE | `/api/companies/{id}/eta/customers/{customerId}` | `CUSTOMERS/DELETE` | Hard-delete ETA customer |
| GET | `/api/companies/{id}/eta/items` | `ITEMS/VIEW` | List ETA items |
| POST | `/api/companies/{id}/eta/items` | `ITEMS/CREATE` | Create ETA item |
| GET | `/api/companies/{id}/eta/items/{itemId}` | `ITEMS/VIEW` | Get ETA item by ID |
| PUT | `/api/companies/{id}/eta/items/{itemId}` | `ITEMS/EDIT` | Update ETA item (set `isActive: false` to deactivate per FR-008) |
| DELETE | `/api/companies/{id}/eta/items/{itemId}` | `ITEMS/DELETE` | Hard-delete ETA item |
| GET | `/api/companies/{id}/zatca/customers` | `CUSTOMERS/VIEW` | List ZATCA customers |
| POST | `/api/companies/{id}/zatca/customers` | `CUSTOMERS/CREATE` | Create ZATCA customer |
| GET | `/api/companies/{id}/zatca/customers/{customerId}` | `CUSTOMERS/VIEW` | Get ZATCA customer by ID |
| PUT | `/api/companies/{id}/zatca/customers/{customerId}` | `CUSTOMERS/EDIT` | Update ZATCA customer (set `isActive: false` to deactivate) |
| DELETE | `/api/companies/{id}/zatca/customers/{customerId}` | `CUSTOMERS/DELETE` | Hard-delete ZATCA customer |
| GET | `/api/companies/{id}/zatca/items` | `ITEMS/VIEW` | List ZATCA items |
| POST | `/api/companies/{id}/zatca/items` | `ITEMS/CREATE` | Create ZATCA item |
| GET | `/api/companies/{id}/zatca/items/{itemId}` | `ITEMS/VIEW` | Get ZATCA item by ID |
| PUT | `/api/companies/{id}/zatca/items/{itemId}` | `ITEMS/EDIT` | Update ZATCA item (set `isActive: false` to deactivate) |
| DELETE | `/api/companies/{id}/zatca/items/{itemId}` | `ITEMS/DELETE` | Hard-delete ZATCA item |
| GET | `/api/companies/{id}/eta/config` | `CONFIG/VIEW` | Read ETA config (200 null body if never configured) |
| PUT | `/api/companies/{id}/eta/config` | `CONFIG/EDIT` | Upsert ETA config |
| GET | `/api/companies/{id}/zatca/config` | `CONFIG/VIEW` | Read ZATCA config |
| PUT | `/api/companies/{id}/zatca/config` | `CONFIG/EDIT` | Upsert ZATCA config |

All endpoints reject Admin Mode with `403 COMPANY_CONTEXT_REQUIRED`.

**Deactivation design**: There is no separate `PATCH …/deactivate` endpoint. Deactivation is performed via the standard `PUT` endpoint with `isActive: false` in the request body (the `isActive` field is part of every `WriteRequest` schema). The service layer detects the `true → false` transition and applies deactivation logic. Hard delete (`DELETE`) and deactivation (`PUT` with `isActive: false`) are two independent removal capabilities per FR-007/FR-008.

**Query parameters on list endpoints** (GET `/customers` and GET `/items` for both authorities):
- `q` — substring search across name and tax/VAT number/code fields (FR-024, FR-025)
- `companyId` — filter to a single company within the user's assigned set (FR-022)
- `includeInactive` — include `isActive=false` rows (default: `false`; FR-007, FR-008)
- `page`, `size`, `sort` — standard pagination (0-based, max 100, default sort `name_en,asc`)

### Wave 6 Permission Scopes

Wave 6 introduces three new permission scopes (seeded in V42 per Constitution XVII.7). Users will see menu entries and buttons governed by these scopes:

| Scope | Actions | `COMPANY_ADMIN` | `ACCOUNTANT` | `VIEWER` |
|-------|---------|:---:|:---:|:---:|
| `CUSTOMERS` | VIEW, CREATE, EDIT, DELETE, REFRESH | All | VIEW, CREATE, REFRESH | VIEW |
| `ITEMS` | VIEW, CREATE, EDIT, DELETE, REFRESH | All | VIEW, CREATE, REFRESH | VIEW |
| `CONFIG` | VIEW, CREATE, EDIT, DELETE, REFRESH | All | — | — |

Users without `CUSTOMERS/VIEW`, `ITEMS/VIEW`, or `CONFIG/VIEW` will not see the respective menu entries in the sidebar (gated by `appHasPermission` directive per Constitution XIV.4).

### Operational Mode Requirement

The three new frontend screens (Customers, Items, Configuration) are operational-only. Users must be in `OPERATIONAL_MODE` with an active company context. Admin Mode access is rejected at the API level by the `@RequireOperationalMode` annotation (Constitution VII.4).

---

## Wave 7 — ETA Document Tables and Submission Engine

Wave 7 adds nine operational tables for ETA invoices, receipts, and shared submission infrastructure. Migrations V48–V53 apply on top of the Wave 6 V47 baseline. A supplementary permission seed migration V53a adds INVOICE and RECEIPT permission rows. The `invoice_artifacts` table includes an `attempt_number` column and the `submission_attempts` table includes a `submitted_by` column, both created directly in V52.

### Deployment Notes

- **Migrations**: V48 (`eta_invoice_headers`), V49 (`eta_invoice_lines` + `eta_invoice_line_taxes`), V50 (`eta_receipt_headers`), V51 (`eta_receipt_lines` + `eta_receipt_line_taxes`), V52 (`submission_attempts` + `invoice_artifacts` + `audit_logs` + `append_only_guard` trigger), V53 (compound indexes across all 8 new tables), V53a (INVOICE + RECEIPT permission seeds). These run automatically via Flyway on backend startup.
- **V52 trigger verification**: After deploying, confirm the `append_only_guard` trigger exists and enforces immutability on `invoice_artifacts` and `audit_logs`:
  ```sql
  -- Should raise ERROR: append_only_table
  UPDATE invoice_artifacts SET content = 'x' WHERE id = '<any-id>';
  DELETE FROM audit_logs WHERE id = <any-id>;
  ```
  For `submission_attempts`, the trigger uses a deny-list of immutable columns: `id`, `company_id`, `authority_environment_id`, `transaction_type`, `document_id`, `attempt_number`, `submitted_by`, `request_payload_ref`, and `submitted_at` may not be changed. Only `result`, `status_code`, `error_summary`, `response_payload_ref`, and `completed_at` may be updated. Any UPDATE touching a deny-listed column raises `append_only_table`.
- **Outbound HTTPS access**: The app container must be able to reach ETA endpoints:
  - Production: `https://api.invoicing.eta.gov.eg`
  - Pre-Production: `https://api.preproduction.invoicing.eta.gov.eg`
  Verify connectivity from the app container: `curl -I https://api.preproduction.invoicing.eta.gov.eg`.
- **No new environment variables**: Wave 7 does not introduce new env vars. Existing `JWT_SECRET`, `SPRING_DATASOURCE_*`, `BOOTSTRAP_SUPERUSER_*`, and `ENCRYPTION_MASTER_KEY` settings are unchanged.
- **No new infrastructure components**: No new Docker services, message queues, or external dependencies. Wave 7 uses the existing PostgreSQL instance and the existing Spring Boot application.

### Verifying V48–V53 Applied

After deploying, confirm the migrations applied:

```sql
SELECT version, description, installed_on
FROM flyway_schema_history
WHERE version IN ('48', '49', '50', '51', '52', '53', '53a')
ORDER BY installed_rank;
```

Verify all 9 new tables exist:

```sql
SELECT table_name FROM information_schema.tables
WHERE table_schema = 'public'
  AND table_name IN (
    'eta_invoice_headers', 'eta_invoice_lines', 'eta_invoice_line_taxes',
    'eta_receipt_headers', 'eta_receipt_lines', 'eta_receipt_line_taxes',
    'submission_attempts', 'invoice_artifacts', 'audit_logs'
  );
```

Verify the 16 compound indexes:

```sql
SELECT indexname FROM pg_indexes
WHERE schemaname = 'public'
  AND (indexname LIKE 'idx_eta_%'
       OR indexname LIKE 'idx_submission_%'
       OR indexname LIKE 'idx_artifacts_%'
       OR indexname LIKE 'idx_audit_%');
```

### New Endpoint Surface (Wave 7)

Wave 7 adds approximately 24 new REST endpoints for invoice/receipt CRUD, submission, cancellation, retry, check-status (single + bulk), submission history, artifact download, and clone-to-new-draft. All are gated by `@RequiresPermission` with INVOICE or RECEIPT module actions. All reject Admin Mode with `403 COMPANY_CONTEXT_REQUIRED`.
