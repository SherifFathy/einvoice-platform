# Configuration Reference

> **Quick start**: Copy `.env.example` to `.env` and fill in the values. Spring Boot picks up the variables via `${VAR}` substitution in `application.yml`. Do **not** commit `.env` to version control.

## Environment Variables

### JWT Configuration

| Variable | Default | Description |
|----------|---------|-------------|
| `JWT_SECRET` | (required) | Base64-encoded HMAC-SHA256 secret for JWT signing. |
| `JWT_TTL_SECONDS` | `28800` (8 hours) | JWT access-token lifetime in seconds. Accepted range: `[60, 86400]`. Startup fails with a configuration error if outside this range. |
| `ENCRYPTION_MASTER_KEY` | (required) | Base64-encoded master key for field-level encryption. Reserved for Wave 7+ AES-256-GCM upgrade (not wired in Wave 6). |

### Bootstrap Super User

| Variable | Default | Description |
|----------|---------|-------------|
| `BOOTSTRAP_SUPERUSER_EMAIL` | (none — must be set) | Email for the bootstrap Super User inserted by Flyway versioned migration `V44__bootstrap_super_user.sql`. Must be a valid email address. |
| `BOOTSTRAP_SUPERUSER_PASSWORD_HASH` | (none — must be set) | BCrypt password hash for the bootstrap Super User. See BCrypt generation recipe below. |

**BCrypt hash generation**:

Run this jbang one-liner from any directory (requires [jbang](https://jbang.dev/)):

```java
// jbang --deps org.springframework.security:spring-security-crypto:6.4.3
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
System.out.println(new BCryptPasswordEncoder().encode("your-password"));
```

On **Unix / macOS / WSL**, `htpasswd` is an alternative:

```bash
htpasswd -bnBC 10 "" 'your-password' | tr -d ':\n'
```

When both variables are set, the migration inserts a Super User row on first run (idempotent via `ON CONFLICT DO NOTHING`). When either is unset, the migration logs a warning and skips insertion — intended for CI/test environments where test fixtures create users instead.

### Database

| Variable | Default | Description |
|----------|---------|-------------|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5433/einvoice` | PostgreSQL JDBC URL. Use `postgres` as host when running inside Docker Compose. |
| `SPRING_DATASOURCE_USERNAME` | `einvoice` | Database username. |
| `SPRING_DATASOURCE_PASSWORD` | `einvoice_dev` | Database password. |

### Spring Profiles

| Variable | Default | Description |
|----------|---------|-------------|
| `SPRING_PROFILES_ACTIVE` | `dev` | Active Spring profile. Available: `dev`, `test`, `simulation`, `production`. |

---

## Wave 5 Permissions Parity (SC-004 Evidence)

The following default permission sets are seeded by Flyway migration `V42__create_transaction_role_permissions.sql` and verified by integration tests `PermissionParityIT` (backend) and `permission-parity.spec.ts` (frontend). The `*appHasPermission` directive in the Angular shell reads the same `GET /api/session/context` payload, ensuring UI/backend parity.

### Document Modules (INVOICE, RECEIPT, STANDARD, SIMPLIFIED)

| Role | VIEW | CREATE | EDIT | DELETE | CANCEL | TRANSFER | REFRESH | SUBMIT |
|------|------|--------|------|--------|--------|----------|---------|--------|
| COMPANY_ADMIN | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| ACCOUNTANT | ✓ | ✓ | — | — | — | — | ✓ | ✓ |
| VIEWER | ✓ | — | — | — | — | — | — | — |

### Master-Data / Config Modules (CUSTOMERS, ITEMS, CONFIG)

| Role | VIEW | CREATE | EDIT | DELETE | REFRESH |
|------|------|--------|------|--------|---------|
| COMPANY_ADMIN | ✓ | ✓ | ✓ | ✓ | ✓ |
| ACCOUNTANT (CUSTOMERS/ITEMS only) | ✓ | ✓ | — | — | ✓ |
| CONFIG has no ACCOUNTANT role | — | — | — | — | — |

### Super User Bypass

Super Users in `OPERATIONAL_MODE` receive all permissions set to `true` on every module for every accessible company — they are not subject to the RBAC table (Constitution XVII.3). Super Users in `ADMIN_MODE` see no companies and no operational modules (admin sidebar only).

---

## Wave 5 Error-Code Catalogue

Full catalogue: [`specs/006-wave5-foundation-refactor/contracts/error-codes.md`](../specs/006-wave5-foundation-refactor/contracts/error-codes.md)

| Code | HTTP | When |
|------|------|------|
| `BAD_CREDENTIALS` | 401 | Unknown email, wrong password, or inactive user (generic — no enumeration) |
| `INVALID_AUTHORITY_ENVIRONMENT` | 400 | `(authority, environment)` not in catalogue or inactive |
| `COMPANY_CONTEXT_REQUIRED` | 401 / 403 | Regular user omitted `companyId` at login, or Super User in Admin Mode hit operational endpoint |
| `UNAUTHORIZED_CONTEXT` | 401 | Regular user has no active assignment for the selected context |
| `INACTIVE_COMPANY` | 400 | Selected company is deactivated |
| `FORBIDDEN` | 403 | Authenticated user lacks required permission |
| `LAST_SUPER_USER_PROTECTED` | 409 | Mutation would leave zero active Super Users |
| `EMAIL_ALREADY_EXISTS` | 409 | Unique-email constraint violation |
| `TAX_NUMBER_DUPLICATE_IN_CONTEXT` | 400 | Another active company in the same `authority_environment_id` uses the tax number |
| `INVALID_ROLE_FOR_AUTHORITY` | 400 | `(authority, transactionType, roleCode)` triple not in `transaction_roles` |
| `ASSIGNMENT_EXISTS` | 409 | Duplicate assignment unique constraint |
| `BRANCH_CODE_DUPLICATE_IN_COMPANY` | 400 | `branchCode` collides within same `companyId` |
| `VALIDATION_ERROR` | 400 | Malformed request / missing required field |
| `UNAUTHENTICATED` | 401 | Token missing, invalid, or expired |

---

## Wave 6 — Master Data and Certificate Configurations

### New Tables

| Table | Purpose | Key Constraints |
|-------|---------|-----------------|
| `eta_customers` | ETA buyer/recipient records per company and environment | `uq_eta_customer_tax (company_id, authority_environment_id, tax_number)`; `idx_eta_customers_ctx (company_id, authority_environment_id)` |
| `eta_items` | ETA product/service records per company and environment | `uq_eta_item_code (company_id, authority_environment_id, internal_code)`; `idx_eta_items_ctx (company_id, authority_environment_id)` |
| `zatca_customers` | ZATCA buyer/recipient records per company and environment | `uq_zatca_customer_vat (company_id, authority_environment_id, vat_number)`; `idx_zatca_customers_ctx (company_id, authority_environment_id)` |
| `zatca_items` | ZATCA product/service records per company and environment | `uq_zatca_item_code (company_id, authority_environment_id, internal_code)`; `idx_zatca_items_ctx (company_id, authority_environment_id)` |
| `eta_configs` | ETA submission credentials and POS descriptors per company/environment (singleton) | `uq_eta_config (company_id, authority_environment_id)`; `idx_eta_configs_ctx (company_id, authority_environment_id)` |
| `zatca_configs` | ZATCA cryptographic/onboarding artefacts per company/environment (singleton) | `uq_zatca_config (company_id, authority_environment_id)`; `idx_zatca_configs_ctx (company_id, authority_environment_id)` |
| `zatca_chain_state` | Running ZATCA hash-chain state per company/environment (singleton) | `uq_zatca_chain (company_id, authority_environment_id)`; `idx_zatca_chain_ctx (company_id, authority_environment_id)` |

All seven tables are **operational** (Constitution II.5): every read/write filters by `(company_id, authority_environment_id)` from the JWT-derived `TenantContext`. Flyway migrations V45 and V46 create these seven tables; V47 finalises operational compound indexes.

### Permission Scopes (Constitution XVII.7)

The master-data permission scopes were partially seeded in Wave 5 (V42). Wave 6 endpoints bind to them via `@RequiresPermission`. The current seeding state:

| Scope | Actions | COMPANY_ADMIN | ACCOUNTANT | VIEWER |
|-------|---------|---------------|------------|--------|
| `CUSTOMERS` | VIEW, CREATE, EDIT, DELETE, REFRESH | All five (V42) | VIEW, CREATE, REFRESH (V42) | VIEW only — seeded if VIEWER role exists for CUSTOMERS/ITEMS in `transaction_roles` (T034 verifies) |
| `ITEMS` | VIEW, CREATE, EDIT, DELETE, REFRESH | All five (V42) | VIEW, CREATE, REFRESH (V42) | VIEW only — same condition as CUSTOMERS |
| `CONFIG` | VIEW, CREATE, EDIT, DELETE, REFRESH | All five (V42) | — | — |

**Note**: V41 `transaction_roles` only creates VIEWER rows for INVOICE, RECEIPT, STANDARD, and SIMPLIFIED — not for CUSTOMERS, ITEMS, or CONFIG. Therefore V42 does not seed `CUSTOMERS/VIEW` or `ITEMS/VIEW` for VIEWER. T034 in Phase 2 will verify this gap and, if needed, add `V47a__wave6_master_data_permissions_seed.sql` to create VIEWER role rows and grant VIEW on CUSTOMERS/ITEMS.

`REFRESH` is granted in the V42 seed but is not bound to any Wave 6 endpoint; it is reserved for Wave 7+ refresh-from-authority flows.

### Phase-1 Plain-Text Storage Trade-Off (FR-026, Constitution XVIII)

Secret, key, and certificate columns on `eta_configs` and `zatca_configs` are stored as plain `TEXT` in PostgreSQL. There is no field-level encryption, no masking, and no `bytea`/encrypted-column wrapper. This is a deliberate Phase-1 trade-off:

- The configuration screen displays plaintext values to authorized users (those with `CONFIG/VIEW` permission) so they can verify and update previously entered credentials.
- Phase 2 will introduce AES-256-GCM encryption at the storage layer with **no schema migration required** (Constitution XVIII.3) — all columns are already TEXT, wide enough for base64-encoded ciphertext.
- For ETA configurations in **Production** environment (`authority_environment_id = 1`): `tokenName` and `tokenPass` are required. For Pre-production (`authority_environment_id = 2`) they remain optional (FR-013).

### UI Language Rule (FR-027, FR-028)

All Wave 6 screen chrome (labels, buttons, column headers, tooltips, validation messages) is English-only — no Angular i18n or locale strings. Arabic name fields and Arabic address inputs use `dir="rtl"` while surrounding layout remains LTR. This rule carries forward from Wave 5 Q2 and applies to all future waves.

### Search Performance Trade-Off (SC-006, FR-024/025)

Customer and item search (`?q=` parameter) uses leading-wildcard `LIKE %query%` over `LOWER(name_en)`, `LOWER(name_ar)`, and `LOWER(tax_number/vat_number/internal_code)`, OR-joined across three columns. The compound `(company_id, authority_environment_id)` B-tree index bounds each query to a single tenant partition, so the post-filter sequential scan visits only rows belonging to that partition. At the Wave 6 target scale of 5,000 rows per company per partition, this satisfies SC-006 (p95 < 2s for first page). As data grows beyond ~10,000 rows per partition, leading-wildcard LIKE will degrade and a follow-up migration should add `pg_trgm` GIN indexes on the six search columns. That work is deferred to a Phase-2 ticket.

### Wave 6 Error-Code Additions

Full catalogue: [`specs/007-wave6-master-data-configs/contracts/error-codes.md`](../specs/007-wave6-master-data-configs/contracts/error-codes.md)

| Code | HTTP | When |
|------|------|------|
| `DUPLICATE_TAX_NUMBER_IN_CONTEXT` | 409 | ETA customer `taxNumber` uniqueness violation within scope |
| `DUPLICATE_VAT_NUMBER_IN_CONTEXT` | 409 | ZATCA customer `vatNumber` uniqueness violation within scope |
| `DUPLICATE_INTERNAL_CODE_IN_CONTEXT` | 409 | Item `internalCode` uniqueness violation within scope |
| `INVALID_AUTHORITY_FOR_ROUTE` | 403 | JWT authority does not match the endpoint's authority prefix |
| `BRANCH_ID_NOT_ALLOWED` | 400 | Request body contained `branchId` on a config endpoint (spec Q4) |
| `INVALID_CUSTOMER_TYPE` | 400 | `customerType` not in authority-specific allowlist |
| `INVALID_VAT_CATEGORY` | 400 | `vatCategory` not in `{S, Z, E, O}` |
| `INVALID_ITEM_TYPE` | 400 | `itemType` not in `{GS1, EGS}` |
| `INVALID_ADDRESS_DATA` | 400 | `addressData` JSON missing required keys for the authority |
| `INVALID_ENVIRONMENT_FOR_AUTHORITY` | 400 | JWT environment does not match the endpoint's authority |
