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

---

## Wave 7 — ETA Document Tables and Submission Engine

### New Tables

| Table | Purpose | Key Constraints |
|-------|---------|-----------------|
| `eta_invoice_headers` | Canonical ETA tax invoice (6 document types: `i`, `c`, `d`, `ei`, `ec`, `ed`) | `uq_eta_invoice_number (company_id, authority_environment_id, invoice_number)`; `CHECK (documentType IN ('i','c','d','ei','ec','ed'))`; `CHECK ((documentType IN ('c','d','ec','ed')) IS NOT TRUE OR original_document_id IS NOT NULL)` |
| `eta_invoice_lines` | Line items within an invoice | `uq_eta_invoice_line (header_id, line_number)`; `CHECK (item_type IN ('GS1','EGS'))`; `CHECK (quantity > 0)`; `ON DELETE CASCADE` |
| `eta_invoice_line_taxes` | Tax components per line (multiple per line — Constitution XI.3) | `ON DELETE CASCADE` |
| `eta_receipt_headers` | ETA v1.2 receipt (23 subtypes) | `uq_eta_receipt_number (company_id, authority_environment_id, receipt_number)`; `buyer_data` nullable (B2C); `original_receipt_id` required for return/cancellation subtypes |
| `eta_receipt_lines` | Line items within a receipt | Same shape as `eta_invoice_lines` referencing `eta_receipt_headers` |
| `eta_receipt_line_taxes` | Tax components per receipt line | Same shape as `eta_invoice_line_taxes` referencing `eta_receipt_lines` |
| `submission_attempts` | Shared cross-authority transmission log | `UNIQUE (document_id, attempt_number)`; append-only after finalisation — deny-list trigger rejects UPDATE on all columns except `result`, `status_code`, `error_summary`, `response_payload_ref`, `completed_at`; includes `submitted_by UUID` for audit traceability |
| `invoice_artifacts` | Shared immutable payload storage (SIGNED_JSON, SIGNED_XML, QR_CODE, CLEARED_XML, ETA_RESPONSE, ZATCA_RESPONSE) | Fully append-only — no UPDATE or DELETE (3-layer enforcement: repository, service, DB trigger); includes `attempt_number INTEGER` for per-attempt artifact lookup (T097) |
| `audit_logs` | Shared immutable audit trail for every state-changing action | Fully append-only — same 3-layer enforcement |

All nine tables are **operational** (Constitution II.5): every read/write filters by `(company_id, authority_environment_id)` from the JWT-derived `TenantContext`. Flyway migrations V48–V53 create these tables and their compound indexes.

### Permission Scopes — INVOICE and RECEIPT (Constitution XVII.6–8)

Wave 7 introduces the INVOICE and RECEIPT permission modules with all 8 defined action permissions:

| Role | VIEW | CREATE | EDIT | DELETE | CANCEL | TRANSFER | REFRESH | SUBMIT |
|------|------|--------|------|--------|--------|----------|---------|--------|
| COMPANY_ADMIN | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| ACCOUNTANT | ✓ | ✓ | — | — | — | — | ✓ | ✓ |
| VIEWER | ✓ | — | — | — | — | — | — | — |

Seeded by Flyway migration `V53a__wave7_invoice_receipt_permissions_seed.sql` (idempotent `INSERT … ON CONFLICT DO NOTHING`). `TRANSFER` is seeded for forward-compatibility with Wave 9 inter-branch transfers; no Wave 7 endpoint consumes it.

### ETA HTTP Base URLs

The `EtaHttpClient` routes outbound calls based on `authority_environments.id`:

| `authority_environment_id` | Environment | ETA Base URL |
|---------------------------|-------------|--------------|
| 1 | PRODUCTION | `https://api.invoicing.eta.gov.eg` |
| 2 | PRE-PRODUCTION | `https://api.preproduction.invoicing.eta.gov.eg` |

> **Confirmed**: Hostnames verified against ETA SDK documentation. The Pre-Production sandbox uses the full `preproduction` subdomain.

Tokens are cached per `(companyId, authorityEnvironmentId)` via Caffeine with TTL = token-expiry − 60 seconds (Constitution V.3 bounded cache). On 401, the cache entry is invalidated and one refresh attempt is made before propagating.

### Wave 7 Error-Code Additions

Full catalogue: [`specs/008-eta-docs-submission/contracts/error-codes.md`](../specs/008-eta-docs-submission/contracts/error-codes.md)

| Code | HTTP | When |
|------|------|------|
| `DUPLICATE_INVOICE_NUMBER` | 409 | Invoice number already exists for `(companyId, authorityEnvironmentId)` |
| `DUPLICATE_RECEIPT_NUMBER` | 409 | Receipt number already exists for `(companyId, authorityEnvironmentId)` |
| `MISSING_ORIGINAL_DOCUMENT` | 400 | Credit/debit note or return/cancellation receipt missing required original document reference |
| `INCOMPATIBLE_ORIGINAL_DOCUMENT` | 400 | Original document type is incompatible with the new document type |
| `TOTALS_INCONSISTENT` | 400 | Line or header totals do not reconcile at 5-decimal precision (FR-024) |
| `NO_CERTIFICATE_CONFIGURED` | 409 | No ETA certificate configured for `(companyId, authorityEnvironmentId)` at submission time (FR-008) |
| `DOCUMENT_NOT_DRAFT` | 409 | Edit or delete attempted on a non-DRAFT document (FR-007) |
| `INVALID_LIFECYCLE_TRANSITION` | 409 | Action not allowed from the current state per lifecycle matrix |
| `OPTIMISTIC_LOCK_CONFLICT` | 409 | `If-Match` version does not match current `@Version` (FR-025); response includes `expectedVersion`, `actualVersion`, and full `current` document body |
| `APPEND_ONLY_VIOLATION` | 500 | Internal — surfaces only if a future code path tries to UPDATE/DELETE an append-only table; should never occur in normal operation (defence-in-depth surface) |
| `BULK_BATCH_LIMIT_EXCEEDED` | 400 | Bulk check-status request exceeds 200 document limit |

Two additional outcome codes — `SUBMISSION_AMBIGUOUS` and `ETA_VALIDATION_ERROR` — surface in 200 OK submission/response bodies rather than as HTTP errors; see [`contracts/error-codes.md`](../specs/008-eta-docs-submission/contracts/error-codes.md).

### Document Lifecycle States

Both invoices and receipts share the same seven-state lifecycle:

```
DRAFT → SUBMITTING → { VALID | REJECTED | IN_REVIEW | SUBMISSION_AMBIGUOUS }
IN_REVIEW → CHECK_STATUS → { VALID | REJECTED }
VALID → CANCEL → CANCELLED (if ETA accepts)
SUBMISSION_AMBIGUOUS → RETRY → { VALID | REJECTED | IN_REVIEW | SUBMISSION_AMBIGUOUS }
REJECTED → ∅ (terminal — clone-to-new-draft creates a new row)
CANCELLED → ∅ (terminal)
```

### Optimistic Concurrency (FR-025)

`eta_invoice_headers` and `eta_receipt_headers` carry a `version` column (JPA `@Version`, default 0). `GET` responses include `ETag: "<version>"`. `PUT` requires `If-Match` header. On version mismatch, the API returns 409 `OPTIMISTIC_LOCK_CONFLICT` with `{ expectedVersion, actualVersion, current: {...} }`.

### Five-Decimal Money Precision (Constitution XIII.5)

All monetary columns on invoice/receipt headers and lines use `NUMERIC(18,5)`. The `EtaMoneyMath` helper centralises rounding (`RoundingMode.HALF_UP`, 5 fractional digits) for line-total and header-total reconciliation.

### Post-Implementation Notes (T118)

- **ETA Pre-Production hostname**: Confirmed as `api.preproduction.invoicing.eta.gov.eg` (full word, not `preprod`). Updated in `EtaHttpClient`, `configuration-reference.md`, and `deployment-guide.md`.
- **Token TTL**: ETA Pre-Production access tokens expire after 3600 seconds (1 hour). The `EtaTokenManager` caches with TTL = expiry − 60s = 3540 seconds.
- **submission_attempts trigger**: Uses a deny-list of immutable columns (id, company_id, authority_environment_id, transaction_type, document_id, attempt_number, submitted_by, request_payload_ref, submitted_at). Changing any of these raises `append_only_table`. Only the finalisation columns (result, status_code, error_summary, response_payload_ref, completed_at) are updatable. The deny-list is appropriate because the protected column set is stable and finite.
- **V54 folded into V52**: The unscheduled V54 migration has been removed. Its columns (`attempt_number` on `invoice_artifacts`, `submitted_by` on `submission_attempts`) and the compound index are now part of V52 directly.
- **Lifecycle matrix fix**: `IN_REVIEW + CHECK_STATUS` was missing a target state transition in both `EtaInvoiceLifecycle` and `EtaReceiptLifecycle`. Fixed to return `IN_REVIEW` (self-loop). This was caught by the codegen now propagating exceptions instead of swallowing them.
- **T117 (quickstart A–L)**: Requires ETA Pre-Production sandbox credentials. To be run manually and documented as a follow-up.

---

## Wave 8 — ZATCA Document Tables and Submission Engine

### New Tables

| Table | Purpose | Key Constraints |
|-------|---------|-----------------|
| `zatca_standard_headers` | ZATCA Standard B2B tax documents (clearance flow). Buyer mandatory. 7-state lifecycle. `@Version` for optimistic concurrency. | `uq_zatca_standard_number (company_id, authority_environment_id, invoice_number)`; `original_invoice_id` self-reference FK; `CHECK (status IN ('DRAFT','SUBMITTING','SUBMITTED','IN_REVIEW','ACCEPTED','REJECTED','CANCELLED'))` |
| `zatca_standard_lines` | Lines for Standard documents. VAT inline (no separate tax table — Constitution XI.2). | `uq_zatca_standard_line (header_id, line_number)`; `CHECK (quantity > 0)`; `chk_vat_exempt_reason` CHECK constraint: `(vat_category_code IN ('E','O') AND exemption_reason_code IS NOT NULL AND exemption_reason_text IS NOT NULL) OR vat_category_code IN ('S','Z')` — only exempt/zero-rated categories (E, O) require both reason fields; standard-rated (S) and zero-rated (Z) lines must omit them |
| `zatca_simplified_headers` | ZATCA Simplified B2C documents (reporting flow). Buyer optional. 7-state lifecycle. `@Version` for optimistic concurrency. | `uq_zatca_simplified_number (company_id, authority_environment_id, invoice_number)`; `reporting_status` column (replaces `clearance_status`); `original_invoice_id` self-reference within Simplified class |
| `zatca_simplified_lines` | Lines for Simplified documents. Same shape as Standard lines. | Same constraints as `zatca_standard_lines` including `chk_vat_exempt_reason` (`vat_category_code` E/O requires both reason fields; S/Z lines must omit them) |

All four tables are **operational** (Constitution II.5): every read/write filters by `(company_id, authority_environment_id)` from the JWT-derived `TenantContext`. Flyway migrations V54–V57 create these tables, their compound indexes, and the `base_url` config column.

### Permission Scopes — STANDARD and SIMPLIFIED (Constitution XVII.6–8)

Wave 8 uses the STANDARD and SIMPLIFIED permission modules (seeded by V53a for INVOICE/RECEIPT, extended in Wave 8 for ZATCA classes):

| Role | VIEW | CREATE | EDIT | DELETE | CANCEL | TRANSFER | REFRESH | SUBMIT |
|------|------|--------|------|--------|--------|----------|---------|--------|
| COMPANY_ADMIN | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| ACCOUNTANT | ✓ | ✓ | — | — | — | — | ✓ | ✓ |
| VIEWER | ✓ | — | — | — | — | — | — | — |

### ZATCA Base URL Configuration

The `ZatcaHttpClient` resolves the outbound base URL at request time from `zatca_configs.base_url` (Wave 6 singleton per `(company_id, authority_environment_id)`). The platform does not hardcode ZATCA URLs — they are stored in the configuration screen and consumed per submission.

| `authority_environment_id` | Environment | Typical `base_url` |
|---------------------------|-------------|---------------------|
| 3 | PRODUCTION | `https://gw-fatoora.zatca.gov.sa/e-invoiceing` |
| 5 | SANDBOX | `https://gw-fatoora.zatca.gov.sa/e-invoiceing/simulation` |

### Chain-Busy Timeout (FR-009a, Q2)

ZATCA submissions acquire a pessimistic `SELECT FOR UPDATE` lock on the `zatca_chain_state` row with `SET LOCAL lock_timeout = '30s'`. If the lock cannot be acquired within this window:
- The API returns **HTTP 503** with error code `CHAIN_BUSY`.
- The document remains in `DRAFT` status.
- No `submission_attempts` row is created.
- The `zatca_chain_state.invoice_counter` is unchanged.

This timeout is **not configurable** via environment variable; it is hardcoded in `ZatcaChainService.acquireForUpdate()`.

### Bulk Check Status (FR-018a, Q5)

The bulk check-status endpoint (`POST /zatca/standard/check-status` and `POST /zatca/simplified/check-status`) accepts an unbounded `documentIds[]` list and streams results as NDJSON (`application/x-ndjson`). Key tunables:
- **Rate pacing**: 300 ms between ZATCA calls (default 200 calls/min). Hardcoded in `BulkCheckStatusService`.
- **Cancellation**: Each run is tracked by a `Run-Id` response header. `DELETE /api/runs/{runId}` sets the cancel flag, causing remaining documents to emit `CANCELLED_NO_OP`.
- **Run GC**: Completed runs are garbage-collected after 10 minutes by a scheduled cleanup.

### Wave 8 Error-Code Additions

Full catalogue: [`specs/009-zatca-docs-submission/contracts/error-codes.md`](../specs/009-zatca-docs-submission/contracts/error-codes.md)

| Code | HTTP | When |
|------|------|------|
| `DUPLICATE_STANDARD_NUMBER` | 409 | Standard invoice number uniqueness violation within `(companyId, authorityEnvironmentId)` |
| `DUPLICATE_SIMPLIFIED_NUMBER` | 409 | Simplified invoice number uniqueness violation within `(companyId, authorityEnvironmentId)` |
| `MISSING_BUYER_FOR_STANDARD` | 400 | Standard document submitted without buyer data (FR-008) |
| `VAT_EXEMPTION_REASON_REQUIRED` | 400 | VAT category E or O missing `exemptionReasonCode` and `exemptionReasonText` (FR-004) |
| `CHAIN_BUSY` | 503 | Chain lock acquisition timed out after ~30 s (FR-009a, Q2) |
| `WRONG_ORIGINAL_CLASS` | 400 | Credit/debit note references an original document of a different class (FR-030) |

### Two-Decimal Money Precision (Constitution XIII.6)

ZATCA document totals and line amounts use `NUMERIC(18,2)` per ZATCA Phase-2 spec. The `ZatcaMoneyMath` helper centralises rounding (`RoundingMode.HALF_EVEN`, 2 fractional digits). Source quantities and unit prices use `NUMERIC(18,5)` for computational precision.
