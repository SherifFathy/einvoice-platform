# Data Model — Pre-Final Phase Review

## New Tables

### `lov_contexts`
Stores each unique combination of the 3 login LOVs. Shared across the platform.

```sql
CREATE TABLE lov_contexts (
    id          BIGSERIAL PRIMARY KEY,
    authority   VARCHAR(10) NOT NULL,          -- ZATCA | ETA
    doc_type    VARCHAR(20) NOT NULL,          -- INVOICE | RECEIPT
    sub_env     VARCHAR(20) NOT NULL,          -- SANDBOX | SIMULATION | PRODUCTION | PREPROD
    context_key VARCHAR(60) NOT NULL UNIQUE,   -- computed: 'ZATCA-INVOICE-SANDBOX'
    created_at  TIMESTAMPTZ DEFAULT NOW()
);
-- Seed all valid combinations on startup (migration inserts rows)
-- Canonical names (see spec.md §1.2 mapping table):
INSERT INTO lov_contexts (authority, doc_type, sub_env, context_key) VALUES
    ('ZATCA', 'INVOICE', 'SANDBOX',    'ZATCA-INVOICE-SANDBOX'),
    ('ZATCA', 'INVOICE', 'SIMULATION', 'ZATCA-INVOICE-SIMULATION'),
    ('ZATCA', 'INVOICE', 'PRODUCTION', 'ZATCA-INVOICE-PRODUCTION'),
    ('ETA',   'INVOICE', 'PREPROD',    'ETA-INVOICE-PREPROD'),
    ('ETA',   'INVOICE', 'PRODUCTION', 'ETA-INVOICE-PRODUCTION'),
    ('ETA',   'RECEIPT', 'PREPROD',    'ETA-RECEIPT-PREPROD'),
    ('ETA',   'RECEIPT', 'PRODUCTION', 'ETA-RECEIPT-PRODUCTION');
-- Total: 7 rows
CREATE UNIQUE INDEX lov_contexts_key ON lov_contexts(context_key);
```

### `sub_env` → `authority_configs.environment` Mapping (Constitution III.1)

`AuthService.login(...)` MUST resolve `lov_context_id` and derive the `authority_configs.environment` value using this mapping:

| `lov_contexts.authority` | `lov_contexts.sub_env` | `authority_configs.environment` ENUM value |
|--------------------------|------------------------|---------------------------------------------|
| ZATCA | SANDBOX | ZATCA_SANDBOX |
| ZATCA | SIMULATION | ZATCA_SIMULATION |
| ZATCA | PRODUCTION | ZATCA_PRODUCTION |
| ETA | PREPROD | ETA_PREPRODUCTION |
| ETA | PRODUCTION | ETA_PRODUCTION |

Implement as a static `LovContextMapper.toAuthorityEnvironment(String authority, String subEnv)` method in `platform-security`.

---

---

### `user_context_permissions`
Fine-grained permissions per user, per company, per LOV context.

```sql
CREATE TABLE user_context_permissions (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL REFERENCES users(id),
    company_id      BIGINT NOT NULL REFERENCES companies(id),
    lov_context_id  BIGINT NOT NULL REFERENCES lov_contexts(id),
    permission      VARCHAR(30) NOT NULL,   -- e.g. CREATE_INVOICE
    granted_by      BIGINT REFERENCES users(id),
    granted_at      TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE (user_id, company_id, lov_context_id, permission)
);
CREATE INDEX ucp_user_company_ctx ON user_context_permissions(user_id, company_id, lov_context_id);
```

---

## Modified Tables

### `companies` — Remove address fields

Remove these columns (migrate data to branches):
- `street`, `building_number`, `additional_number`, `city`, `district`, `postal_code`, `country_code`, `additional_street`

Remaining columns: `id`, `name_ar`, `name_en`, `vat_number`, `cr_number`, `logo_path`, `is_active`, `created_at`, `updated_at`

```sql
-- Migration: V30__move_address_to_branches.sql
ALTER TABLE companies
    DROP COLUMN IF EXISTS street,
    DROP COLUMN IF EXISTS building_number,
    DROP COLUMN IF EXISTS additional_number,
    DROP COLUMN IF EXISTS city,
    DROP COLUMN IF EXISTS district,
    DROP COLUMN IF EXISTS postal_code,
    DROP COLUMN IF EXISTS country_code,
    DROP COLUMN IF EXISTS additional_street;
```

---

### `branches` — Add address fields

```sql
ALTER TABLE branches
    ADD COLUMN street             VARCHAR(255),
    ADD COLUMN building_number    VARCHAR(20),
    ADD COLUMN additional_number  VARCHAR(20),
    ADD COLUMN city               VARCHAR(100),
    ADD COLUMN district           VARCHAR(100),
    ADD COLUMN postal_code        VARCHAR(20),
    ADD COLUMN country_code       CHAR(2),
    ADD COLUMN additional_street  VARCHAR(255);
```

---

### `customers` — Add `lov_context_id`

```sql
ALTER TABLE customers ADD COLUMN lov_context_id BIGINT NOT NULL DEFAULT 1 REFERENCES lov_contexts(id);  -- DEFAULT 1 covers existing rows; drop after backfill if desired
CREATE INDEX customers_company_context ON customers(company_id, lov_context_id);
DROP INDEX IF EXISTS customers_company_vat;
CREATE INDEX customers_company_context_vat ON customers(company_id, lov_context_id, vat_number);
```

---

### `items` — Add `lov_context_id`

```sql
ALTER TABLE items ADD COLUMN lov_context_id BIGINT NOT NULL DEFAULT 1 REFERENCES lov_contexts(id);  -- DEFAULT 1 covers existing rows; drop after backfill if desired
-- Replace old unique index
DROP INDEX IF EXISTS items_company_code;
CREATE UNIQUE INDEX items_company_context_code ON items(company_id, lov_context_id, code);
```

---

### `invoices` — Add `lov_context_id`

```sql
ALTER TABLE invoices ADD COLUMN lov_context_id BIGINT NOT NULL DEFAULT 1 REFERENCES lov_contexts(id);  -- DEFAULT 1 covers existing rows; drop after backfill if desired
CREATE INDEX invoices_company_context ON invoices(company_id, lov_context_id);
```

---

### `branches` — Add `lov_context_id`

```sql
-- Default to ZATCA-INVOICE-SANDBOX (id=1) for existing branches during dev migration.
-- Production onboarding sets context explicitly during branch creation.
ALTER TABLE branches ADD COLUMN lov_context_id BIGINT NOT NULL DEFAULT 1 REFERENCES lov_contexts(id);
-- Remove default after backfill so new branches must supply context explicitly:
ALTER TABLE branches ALTER COLUMN lov_context_id DROP DEFAULT;
```

---

### `user_company_roles` — Add `SUPER_USER` to role enum

```sql
ALTER TYPE role_enum ADD VALUE IF NOT EXISTS 'SUPER_USER';
```

---

### `users` — Add `is_super_user` shortcut flag

For fast bypass checks without joining `user_company_roles`:

```sql
ALTER TABLE users ADD COLUMN is_super_user BOOLEAN NOT NULL DEFAULT FALSE;
```

---

## JWT Claims — Extended

After login, the JWT includes:

```json
{
  "user_id": 42,
  "email": "user@example.com",
  "is_super_user": false,
  "active_company_id": 5,
  "role": "ACCOUNTANT",
  "active_authority": "ZATCA",
  "active_doc_type": "INVOICE",
  "active_sub_env": "SANDBOX",
  "lov_context_id": 1,
  "permissions": ["CREATE_INVOICE","VIEW_INVOICE_LIST","VIEW_CUSTOMER_LIST"],
  "available_companies": [{"id":5,"name":"Saudi Co"}]
}
```

---

## Bulk Upload Templates

### Items Template (`items_template.xlsx`) — Column Order

| Column | Header Label | Required | Notes |
|--------|-------------|----------|-------|
| A | Item Code | Yes | Unique per company+context |
| B | Name (Arabic) | Yes | |
| C | Name (English) | Yes | |
| D | Unit of Measure | Yes | e.g. EA, KG, L |
| E | Unit Price | Yes | Decimal, 4 places |
| F | VAT Category | Yes | S, Z, E, O |
| G | VAT Rate (%) | Yes | e.g. 15.00 |
| H | Authority Scope | Yes | ZATCA, ETA, BOTH |
| I | Description | No | |

### Customers Template (`customers_template.xlsx`) — Column Order

| Column | Header Label | Required | Notes |
|--------|-------------|----------|-------|
| A | Name (Arabic) | Yes | |
| B | Name (English) | Yes | |
| C | VAT Number | Conditional | Required for B2B |
| D | ID Type | Yes | NIN, IQAMA, PASSPORT, CRN, OTHER |
| E | ID Value | Yes | |
| F | Customer Type | Yes | B2B, B2C |
| G | Contact Email | No | |
| H | Contact Phone | No | |
| I | Country Code | Yes | 2-letter ISO |

---

## Initial Permission Seed (Constitution — existing users must not be locked out)

After V33 creates `user_context_permissions`, every existing `user_company_roles` record has zero permissions. V33 MUST include a seed INSERT to give existing users a default permission set so they are not locked out post-migration.

**Seed strategy (dev / CI)**: Default all existing users to context `id=1` (ZATCA-INVOICE-SANDBOX). COMPANY_ADMIN and ACCOUNTANT get all 14 permissions. VIEWER gets the three VIEW permissions only.

**Seed strategy (production upgrade from Wave 3)**: Do NOT rely on the Flyway V33 seed. Instead, run `platform-core/src/main/resources/db/seed/production-permissions.sql` as a post-upgrade step (documented in `Docs/deployment-guide.md`). That script joins `user_company_roles` → `authority_configs` → `lov_contexts` to seed each user only for the contexts their company is actually configured for.

```sql
-- V33 seed: grant default permissions to existing user_company_roles
INSERT INTO user_context_permissions (user_id, company_id, lov_context_id, permission, granted_by)
SELECT
    ucr.user_id,
    ucr.company_id,
    1 AS lov_context_id,   -- default context (ZATCA-INVOICE-SANDBOX) for dev seed
    p.permission,
    ucr.user_id            -- self-granted during migration
FROM user_company_roles ucr
CROSS JOIN (
    VALUES
        ('CREATE_INVOICE'),('CREATE_CUSTOMER'),('CREATE_ITEM'),
        ('EDIT_INVOICE'),('EDIT_CUSTOMER'),('EDIT_ITEM'),
        ('DELETE_INVOICE'),('DELETE_CUSTOMER'),('DELETE_ITEM'),
        ('TRANSFER_INVOICE'),('REFRESH_INVOICE'),
        ('VIEW_INVOICE_LIST'),('VIEW_CUSTOMER_LIST'),('VIEW_ITEM_LIST')
) AS p(permission)
WHERE ucr.role IN ('COMPANY_ADMIN', 'ACCOUNTANT')
ON CONFLICT DO NOTHING;

-- VIEWER role: view-only permissions
INSERT INTO user_context_permissions (user_id, company_id, lov_context_id, permission, granted_by)
SELECT ucr.user_id, ucr.company_id, 1, p.permission, ucr.user_id
FROM user_company_roles ucr
CROSS JOIN (VALUES ('VIEW_INVOICE_LIST'),('VIEW_CUSTOMER_LIST'),('VIEW_ITEM_LIST')) AS p(permission)
WHERE ucr.role = 'VIEWER'
ON CONFLICT DO NOTHING;
```

---

## Migration File Order

| File | Purpose |
|------|---------|
| V30__add_lov_contexts.sql | Create `lov_contexts` table + seed 7 canonical rows |
| V31__add_context_id_to_entities.sql | Add `lov_context_id` to customers, items, invoices, branches (NOT companies) |
| V32__move_address_to_branches.sql | Remove address from companies, add to branches |
| V33__add_user_context_permissions.sql | Create `user_context_permissions` table + seed default permissions for existing roles |
| V34__add_super_user_role.sql | Extend role enum + add `is_super_user` to users |
