# Data Model: Platform Foundation, Tenancy & Master Data

**Branch**: `002-platform-foundation-tenancy` | **Date**: 2026-04-10

## Entity Relationship Overview

```
Company (1) ──── (*) Branch
   │                    │
   │                    └──── (*) AuthorityConfig
   │
   ├──── (*) UserCompanyRole ──── (1) User
   │         │
   │         └──── (*) UserEnvironmentPermission
   │
   ├──── (*) Customer
   │
   ├──── (*) Item
   │
   └──── (*) Invoice
              │
              ├──── (*) InvoiceLine ──── (0..1) Item
              │
              └──── (*) InvoiceVatBreakdown

AuditLog (standalone, references company_id + user_id loosely)
```

---

## Entities

### Company

Top-level tenant entity. All business data is scoped to a company.

| Field | Type | Constraints |
|-------|------|-------------|
| id | BIGSERIAL | PK |
| name_ar | VARCHAR(255) | NOT NULL |
| name_en | VARCHAR(255) | NOT NULL |
| vat_number | VARCHAR(20) | NOT NULL, indexed |
| cr_number | VARCHAR(20) | |
| street | VARCHAR(255) | |
| building_number | VARCHAR(10) | |
| city | VARCHAR(100) | |
| district | VARCHAR(100) | |
| postal_code | VARCHAR(10) | |
| country_code | VARCHAR(3) | DEFAULT 'SA' |
| additional_id | VARCHAR(50) | |
| logo_path | VARCHAR(500) | |
| is_active | BOOLEAN | DEFAULT TRUE |
| created_at | TIMESTAMPTZ | DEFAULT NOW() |
| updated_at | TIMESTAMPTZ | DEFAULT NOW() |

**Indexes**: `(vat_number)`

---

### Branch

Subdivision of a company. Authority configurations are per-branch.

| Field | Type | Constraints |
|-------|------|-------------|
| id | BIGSERIAL | PK |
| company_id | BIGINT | FK -> companies, NOT NULL |
| name_ar | VARCHAR(255) | NOT NULL |
| name_en | VARCHAR(255) | NOT NULL |
| branch_code | VARCHAR(20) | NOT NULL |
| is_active | BOOLEAN | DEFAULT TRUE |
| created_at | TIMESTAMPTZ | DEFAULT NOW() |

**Indexes**: `(company_id)`

---

### AuthorityConfig

Links a branch to a tax authority in a specific environment. Stores encrypted credentials.

| Field | Type | Constraints |
|-------|------|-------------|
| id | BIGSERIAL | PK |
| branch_id | BIGINT | FK -> branches, NOT NULL |
| authority | ENUM | (ZATCA, ETA), NOT NULL |
| environment | ENUM | (ZATCA_SANDBOX, ZATCA_SIMULATION, ZATCA_PRODUCTION, ETA_PREPRODUCTION, ETA_PRODUCTION), NOT NULL |
| credentials_encrypted | BYTEA | |
| certificate_encrypted | BYTEA | |
| csid_encrypted | BYTEA | |
| private_key_encrypted | BYTEA | |
| token_data_encrypted | BYTEA | |
| certificate_expiry_date | TIMESTAMPTZ | |
| invoice_counter | BIGINT | DEFAULT 0 |
| previous_invoice_hash | TEXT | |
| invoice_prefix | VARCHAR(20) | |
| invoice_starting_number | BIGINT | DEFAULT 1 |
| invoice_reset_policy | ENUM | (NEVER, ANNUAL, MONTHLY), DEFAULT 'NEVER' |
| enabled_document_types | JSONB | DEFAULT '[]' |
| is_active | BOOLEAN | DEFAULT TRUE |
| created_at | TIMESTAMPTZ | DEFAULT NOW() |
| updated_at | TIMESTAMPTZ | DEFAULT NOW() |

**Unique constraint**: `(branch_id, authority, environment)`

---

### User

Platform-level identity. Not scoped to a single company.

| Field | Type | Constraints |
|-------|------|-------------|
| id | BIGSERIAL | PK |
| name | VARCHAR(255) | NOT NULL |
| email | VARCHAR(255) | NOT NULL, UNIQUE |
| password_hash | VARCHAR(255) | NOT NULL |
| is_active | BOOLEAN | DEFAULT TRUE |
| created_at | TIMESTAMPTZ | DEFAULT NOW() |
| updated_at | TIMESTAMPTZ | DEFAULT NOW() |

**Indexes**: `(email) UNIQUE`

---

### UserCompanyRole

Assignment of a user to a company with a specific role.

| Field | Type | Constraints |
|-------|------|-------------|
| id | BIGSERIAL | PK |
| user_id | BIGINT | FK -> users, NOT NULL |
| company_id | BIGINT | FK -> companies, NOT NULL |
| role | ENUM | (SUPER_ADMIN, COMPANY_ADMIN, ACCOUNTANT, VIEWER), NOT NULL |
| is_active | BOOLEAN | DEFAULT TRUE |
| granted_by | BIGINT | FK -> users |
| created_at | TIMESTAMPTZ | DEFAULT NOW() |
| updated_at | TIMESTAMPTZ | DEFAULT NOW() |

**Unique constraint**: `(user_id, company_id)`

---

### UserEnvironmentPermission

Grants a user access to a specific environment within their company role.

| Field | Type | Constraints |
|-------|------|-------------|
| id | BIGSERIAL | PK |
| user_company_role_id | BIGINT | FK -> user_company_roles, NOT NULL |
| environment | ENUM | (ZATCA_SANDBOX, ZATCA_SIMULATION, ZATCA_PRODUCTION, ETA_PREPRODUCTION, ETA_PRODUCTION), NOT NULL |
| granted_by | BIGINT | FK -> users |
| granted_at | TIMESTAMPTZ | DEFAULT NOW() |

**Unique constraint**: `(user_company_role_id, environment)`

---

### Customer

Buyer entity scoped to a company. Supports soft delete.

| Field | Type | Constraints |
|-------|------|-------------|
| id | BIGSERIAL | PK |
| company_id | BIGINT | FK -> companies, NOT NULL |
| name_ar | VARCHAR(255) | |
| name_en | VARCHAR(255) | NOT NULL |
| vat_number | VARCHAR(20) | |
| id_type | VARCHAR(20) | |
| id_value | VARCHAR(50) | |
| street | VARCHAR(255) | |
| building_number | VARCHAR(10) | |
| city | VARCHAR(100) | |
| district | VARCHAR(100) | |
| postal_code | VARCHAR(10) | |
| country_code | VARCHAR(3) | DEFAULT 'SA' |
| customer_type | ENUM | (B2B, B2C), NOT NULL |
| contact_email | VARCHAR(255) | |
| contact_phone | VARCHAR(20) | |
| is_active | BOOLEAN | DEFAULT TRUE |
| created_at | TIMESTAMPTZ | DEFAULT NOW() |

**Validation rules**:
- Cannot be soft-deleted if referenced by any invoice (FR-008)
- VAT number mandatory for B2B customers

**Indexes**: `(company_id, vat_number)`, `(company_id, name_en)`

---

### Item

Product or service in a company's catalog.

| Field | Type | Constraints |
|-------|------|-------------|
| id | BIGSERIAL | PK |
| company_id | BIGINT | FK -> companies, NOT NULL |
| code | VARCHAR(50) | NOT NULL |
| name_ar | VARCHAR(255) | |
| name_en | VARCHAR(255) | NOT NULL |
| unit_of_measure | VARCHAR(20) | NOT NULL |
| unit_price | DECIMAL(18,4) | NOT NULL |
| vat_category | VARCHAR(10) | NOT NULL |
| vat_rate | DECIMAL(5,2) | NOT NULL |
| description | TEXT | |
| authority_scope | ENUM | (ZATCA, ETA, BOTH), DEFAULT 'BOTH' |
| is_active | BOOLEAN | DEFAULT TRUE |
| created_at | TIMESTAMPTZ | DEFAULT NOW() |

**Unique constraint**: `(company_id, code)`

**Indexes**: `(company_id, code) UNIQUE`

---

### Invoice

Financial document (draft only in Wave 1).

| Field | Type | Constraints |
|-------|------|-------------|
| id | UUID | PK, DEFAULT gen_random_uuid() |
| company_id | BIGINT | FK -> companies, NOT NULL |
| branch_id | BIGINT | FK -> branches, NOT NULL |
| invoice_number | VARCHAR(50) | |
| type | ENUM | (TAX_INVOICE, SIMPLIFIED_TAX_INVOICE, CREDIT_NOTE, DEBIT_NOTE), NOT NULL |
| subtype_flags | JSONB | DEFAULT '{}' |
| status | ENUM | (DRAFT, VALIDATED, READY_FOR_SUBMISSION, ...), DEFAULT 'DRAFT' |
| issue_date | DATE | NOT NULL |
| supply_date | DATE | |
| supply_end_date | DATE | |
| currency | VARCHAR(3) | DEFAULT 'SAR' |
| buyer_id | BIGINT | FK -> customers, NULLABLE |
| seller_data | JSONB | |
| buyer_data | JSONB | |
| payment_means_code | VARCHAR(5) | |
| payment_terms | TEXT | |
| prepaid_amount | DECIMAL(18,2) | DEFAULT 0 |
| total_line_net | DECIMAL(18,2) | |
| total_allowances | DECIMAL(18,2) | DEFAULT 0 |
| total_without_vat | DECIMAL(18,2) | |
| total_vat | DECIMAL(18,2) | |
| total_with_vat | DECIMAL(18,2) | |
| amount_due | DECIMAL(18,2) | |
| authority | ENUM | (ZATCA, ETA), NOT NULL |
| environment | ENUM | NOT NULL |
| original_invoice_id | UUID | FK -> invoices, NULLABLE |
| notes | TEXT | |
| created_by | BIGINT | FK -> users, NOT NULL |
| created_at | TIMESTAMPTZ | DEFAULT NOW() |
| updated_at | TIMESTAMPTZ | DEFAULT NOW() |

**Validation rules**:
- issue_date NOT in future (BR-KSA-04)
- supply_end_date > supply_date when both present (BR-KSA-15)
- buyer_id mandatory for B2B Tax Invoices
- At least one invoice line
- original_invoice_id mandatory for CREDIT_NOTE and DEBIT_NOTE (BR-KSA-56)
- Subtype flags: self_billed + export invalid (BR-KSA-07)
- Positive quantities and amounts

**Indexes**: `(company_id, status)`, `(company_id, issue_date)`, `(company_id, invoice_number, authority, environment) UNIQUE`

---

### InvoiceLine

Line item within an invoice.

| Field | Type | Constraints |
|-------|------|-------------|
| id | BIGSERIAL | PK |
| invoice_id | UUID | FK -> invoices, NOT NULL |
| item_id | BIGINT | FK -> items, NULLABLE |
| description_ar | VARCHAR(500) | |
| description_en | VARCHAR(500) | NOT NULL |
| quantity | DECIMAL(18,4) | NOT NULL, > 0 |
| unit | VARCHAR(20) | NOT NULL |
| unit_price | DECIMAL(18,4) | NOT NULL, > 0 |
| discount_amount | DECIMAL(18,2) | DEFAULT 0, >= 0 |
| vat_category | VARCHAR(10) | NOT NULL |
| vat_rate | DECIMAL(5,2) | NOT NULL, >= 0 |
| line_net_amount | DECIMAL(18,2) | calculated |
| line_vat_amount | DECIMAL(18,2) | calculated |
| line_total | DECIMAL(18,2) | calculated |
| sort_order | INT | NOT NULL |

**Calculation rules**:
- line_net_amount = (unit_price * quantity) - discount_amount
- line_vat_amount = line_net_amount * (vat_rate / 100)
- line_total = line_net_amount + line_vat_amount

---

### InvoiceVatBreakdown

Aggregated VAT calculation per category/rate for an invoice.

| Field | Type | Constraints |
|-------|------|-------------|
| id | BIGSERIAL | PK |
| invoice_id | UUID | FK -> invoices, NOT NULL |
| vat_category_code | VARCHAR(10) | NOT NULL |
| vat_rate | DECIMAL(5,2) | NOT NULL |
| taxable_amount | DECIMAL(18,2) | NOT NULL |
| tax_amount | DECIMAL(18,2) | NOT NULL |

---

### AuditLog

Immutable record of administrative actions.

| Field | Type | Constraints |
|-------|------|-------------|
| id | BIGSERIAL | PK |
| company_id | BIGINT | NOT NULL |
| user_id | BIGINT | NOT NULL |
| action | VARCHAR(100) | NOT NULL |
| entity_type | VARCHAR(50) | NOT NULL |
| entity_id | VARCHAR(50) | NOT NULL |
| payload_before | JSONB | |
| payload_after | JSONB | |
| ip_address | VARCHAR(45) | |
| timestamp | TIMESTAMPTZ | DEFAULT NOW() |

**Access control**: NO UPDATE/DELETE permissions on this table — append-only enforced at database level.

**Indexes**: `(company_id, timestamp)`, `(entity_type, entity_id)`

---

## State Transitions

### Invoice Status (Wave 1)

```
DRAFT (only state in Wave 1)
  - Create: -> DRAFT
  - Update: DRAFT -> DRAFT
  - Cancel: DRAFT -> CANCELLED (soft delete)
```

Full state machine (Wave 2+):
```
DRAFT -> VALIDATED -> READY_FOR_SUBMISSION -> SUBMISSION_IN_PROGRESS -> (authority-specific terminal states)
```

### Company Active Status

```
ACTIVE (default on creation)
  - Deactivate: ACTIVE -> INACTIVE (blocks all user access)
  - Activate: INACTIVE -> ACTIVE (restores access)
```

---

## Enum Definitions

| Enum | Values |
|------|--------|
| Authority | ZATCA, ETA |
| Environment | ZATCA_SANDBOX, ZATCA_SIMULATION, ZATCA_PRODUCTION, ETA_PREPRODUCTION, ETA_PRODUCTION |
| Role | SUPER_ADMIN, COMPANY_ADMIN, ACCOUNTANT, VIEWER |
| InvoiceType | TAX_INVOICE, SIMPLIFIED_TAX_INVOICE, CREDIT_NOTE, DEBIT_NOTE |
| InvoiceStatus | DRAFT, CANCELLED (Wave 1); VALIDATED, READY_FOR_SUBMISSION, SUBMISSION_IN_PROGRESS, ... (Wave 2+) |
| CustomerType | B2B, B2C |
| AuthorityScope | ZATCA, ETA, BOTH |
| InvoiceResetPolicy | NEVER, ANNUAL, MONTHLY |
