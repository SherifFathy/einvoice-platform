# Phase 1 — Data Model: Wave 5 Foundation Refactoring

**Branch**: `006-wave5-foundation-refactor` | **Date**: 2026-05-02
**Source spec**: [spec.md](./spec.md) §Key Entities + Functional Requirements
**Source plan**: [plan.md](./plan.md) §Project Structure → migrations
**Source implementation reference**: `Docs/implementation-plan.md` §Wave 5 (V37–V43 SQL)

This document is the canonical entity reference for `/speckit.tasks`. SQL DDL lives in the implementation plan; this file captures **conceptual** entity shape, validation rules, relationships, and lifecycle so tasks can be derived without round-tripping the DDL.

## Entity tier classification (per Constitution II.2)

| Tier | Entities | Carries `authority_environment_id`? |
|------|----------|--------------------------------------|
| **Catalogue** (system seed) | `AuthorityEnvironment`, `TransactionRole`, `TransactionRolePermission` | – (catalogue rows are themselves the authority+environment data, or fixed by authority+transactionType) |
| **Global** | `Company`, `Branch`, `User` | **No** |
| **Operational** | `UserCompanyTransactionRole` (assignment) | **Yes** |

(Wave 6+ will add additional Operational entities: `EtaConfig`, `ZatcaConfig`, `EtaCustomer`, `ZatcaCustomer`, `EtaItem`, `ZatcaItem`, `ZatcaChainState`, document tables, etc. — out of scope for this wave.)

---

## 1. AuthorityEnvironment (catalogue)

**Purpose**: Authoritative registry of valid (Authority, Environment) pairs. Constitution Principle III.

**Fields**:
- `id` — small integer, primary key (1..5, fixed by V37 seed)
- `authority` — `'ETA' | 'ZATCA'`
- `environment` — `'PRODUCTION' | 'PREPROD' | 'SIMULATION' | 'SANDBOX'`
- `label` — human-readable display text (e.g., `"ETA Pre-Production"`)
- `isActive` — boolean, default `true`

**Constraints**:
- Unique `(authority, environment)`.
- Five seed rows fixed in V37 (Constitution III.2 table).

**Lifecycle**: Seeded once. Inactive flag may be flipped (out of Wave 5 UI scope) but rows never deleted.

**Used by**:
- `User.authorityEnvironments` — derived through assignments.
- JWT claim `authorityEnvironmentId`.
- `UserCompanyTransactionRole.authorityEnvironmentId` (FK).
- Login flow (`POST /api/auth/environments`).

---

## 2. Company (global)

**Purpose**: A legal/business entity that may be onboarded against one or more (authority, environment) pairs.

**Fields**:
- `id` — UUID, primary key
- `nameAr`, `nameEn` — non-empty strings (≤255)
- `taxNumber` — non-empty string (≤100). **No DB unique constraint** (per FR-007 + Decision 6).
- `crNumber` — optional string (≤100)
- `logoPath` — optional string (≤500); filesystem path, not bytes. **Wave 5 scope: schema-only.** No UI control or API field accepts a value in Wave 5 (per spec.md Q2 / FR-004 revised); `logo_path` stays NULL for all Wave 5 rows. Activation by the future wave that introduces a logo consumer (document PDFs / branded headers).
- `isActive` — boolean, default `true`
- `createdAt`, `updatedAt` — timestamps

**Validation rules**:
- `nameAr`, `nameEn`, `taxNumber` required.
- **Tax-number uniqueness** (FR-007 + Decision 6): when assigning a company to an `authority_environment_id` (via `UserCompanyTransactionRole` create OR — Wave 6+ — via `*_configs` create), no other **active** company in that pair may share `taxNumber`.

**Lifecycle**: Created → Active → (optionally Deactivated). No hard delete in Wave 5 — `isActive=false` only.

**Indexes**: `idx_companies_tax (tax_number)`, `idx_companies_active (is_active)`.

**Relationships**:
- 1 Company has 0..N `Branch`.
- 1 Company has 0..N `UserCompanyTransactionRole` (assignments).

---

## 3. Branch (global, under Company)

**Purpose**: Physical or logical operating location belonging to a Company.

**Fields**:
- `id` — UUID, primary key
- `companyId` — UUID, FK → `Company.id`, required
- `nameAr`, `nameEn` — non-empty strings (≤255)
- `branchCode` — optional string (≤50); unique within company when present
- Address: `addressLine1`, `addressLine2`, `city`, `region`, `postalCode`, `country` (default `'EG'`), `buildingNumber`, `additionalNo`
- `taxpayerActivityCode` — optional string (≤50); ETA-specific, may be null for ZATCA-only branches
- `isActive` — boolean, default `true`
- `createdAt`, `updatedAt`

**Validation rules**:
- `branchCode`, when provided, MUST be unique per `companyId` (DB constraint `uq_branch_code (company_id, branch_code)`).
- Address fields all optional in Wave 5 schema (Wave 6+ may make them required when binding to ZATCA configs).

**Lifecycle**: Created → Active → (optionally Deactivated). No hard delete.

**Relationships**:
- N Branches belong to 1 Company.
- (Wave 6+) Branches will be referenced from `EtaConfig.branchId` / `ZatcaConfig.branchId`.

---

## 4. User (global)

**Purpose**: Platform-level identity. Not bound to any authority/environment/company at the identity level (Constitution XVII.1).

**Fields**:
- `id` — UUID, primary key
- `name` — non-empty string (≤255), display name
- `email` — non-empty string (≤255), **unique** (DB constraint)
- `passwordHash` — non-empty BCrypt hash (BCrypt cost 10, existing default)
- `isSuperUser` — boolean, default `false`
- `isActive` — boolean, default `true`
- `createdAt`, `updatedAt`

**Validation rules**:
- `email` syntactically valid + globally unique.
- `passwordHash` non-empty (no complexity beyond non-empty per FR-006a).
- **At-least-one-active-Super-User invariant** (FR-036a + Decision 7): any update that would set `isSuperUser=false` OR `isActive=false` on a row where `isSuperUser=true` MUST first verify another active Super User exists.

**Lifecycle**: Created → Active → (Deactivated | Reactivated). No hard delete.

**Relationships**:
- 1 User has 0..N `UserCompanyTransactionRole` (assignments).
- 1 User may have authored 0..N assignments via `grantedBy` self-reference.

---

## 5. TransactionRole (catalogue)

**Purpose**: Named role for a specific (authority, transaction type) combination. Seeded by V41.

**Fields**:
- `id` — UUID, primary key
- `authority` — `'ETA' | 'ZATCA'`
- `transactionType` — `'INVOICE' | 'RECEIPT' | 'STANDARD' | 'SIMPLIFIED' | 'CUSTOMERS' | 'ITEMS' | 'CONFIG'`
- `roleCode` — `'COMPANY_ADMIN' | 'ACCOUNTANT' | 'VIEWER'`
- `description` — string (≤255)

**Constraints**:
- Unique `(authority, transactionType, roleCode)`.

**Seed coverage** (per implementation plan §5.1 V41):
- ETA × {INVOICE, RECEIPT} × {COMPANY_ADMIN, ACCOUNTANT, VIEWER} = 6 rows
- ETA × {CUSTOMERS, ITEMS} × {COMPANY_ADMIN, ACCOUNTANT} = 4 rows
- ETA × CONFIG × COMPANY_ADMIN = 1 row
- ZATCA × {STANDARD, SIMPLIFIED} × {COMPANY_ADMIN, ACCOUNTANT, VIEWER} = 6 rows
- ZATCA × {CUSTOMERS, ITEMS} × {COMPANY_ADMIN, ACCOUNTANT} = 4 rows
- ZATCA × CONFIG × COMPANY_ADMIN = 1 row
- **Total: 22 rows**.

**Lifecycle**: Seeded once. Immutable in Wave 5.

---

## 6. TransactionRolePermission (catalogue)

**Purpose**: The set of permission codes granted by each TransactionRole.

**Fields**:
- `id` — UUID, primary key
- `roleId` — UUID, FK → `TransactionRole.id` (`ON DELETE CASCADE`)
- `permissionCode` — string (≤20). One of:
  - **Document modules** (INVOICE, RECEIPT, STANDARD, SIMPLIFIED): `VIEW`, `CREATE`, `EDIT`, `DELETE`, `CANCEL`, `TRANSFER`, `REFRESH`, `SUBMIT`
  - **Master-data / config modules** (CUSTOMERS, ITEMS, CONFIG): `VIEW`, `CREATE`, `EDIT`, `DELETE`, `REFRESH`

**Constraints**:
- Unique `(roleId, permissionCode)`.

**Seed logic** (V42, runs after V41):
- `COMPANY_ADMIN` on document tx_type → all 8 actions.
- `COMPANY_ADMIN` on CUSTOMERS/ITEMS/CONFIG → all 5 master-data actions.
- `ACCOUNTANT` on document tx_type → VIEW, CREATE, REFRESH, SUBMIT.
- `ACCOUNTANT` on CUSTOMERS/ITEMS → VIEW, CREATE, REFRESH.
- `VIEWER` on any → VIEW only.

(Constitution XVII.8 default sets are honored exactly.)

**Lifecycle**: Seeded once. Immutable in Wave 5.

---

## 7. UserCompanyTransactionRole (operational — "Assignment")

**Purpose**: Grants a User a Role on a (Company, AuthorityEnvironment, TransactionType) tuple. The single source of operational permission.

**Fields**:
- `id` — UUID, primary key
- `userId` — UUID, FK → `User.id`
- `companyId` — UUID, FK → `Company.id`
- `authorityEnvironmentId` — small integer, FK → `AuthorityEnvironment.id`
- `transactionType` — `'INVOICE' | 'RECEIPT' | 'STANDARD' | 'SIMPLIFIED' | 'CUSTOMERS' | 'ITEMS' | 'CONFIG'`
- `roleCode` — `'COMPANY_ADMIN' | 'ACCOUNTANT' | 'VIEWER'`
- `isActive` — boolean, default `true`
- `grantedBy` — UUID, nullable, FK → `User.id`
- `grantedAt` — timestamp

**Constraints**:
- Unique `(userId, companyId, authorityEnvironmentId, transactionType)` — i.e., a user has at most one role per module per (company, authority, environment).
- Combination `(authority-derived-from-authorityEnvironmentId, transactionType, roleCode)` MUST resolve to an existing `TransactionRole` row. (Validated in service layer; not a DB FK because it spans two columns.)

**Indexes**:
- `idx_uctr_user_env (userId, authorityEnvironmentId)` — drives the post-credentials Company dropdown lookup.
- `idx_uctr_company_env (companyId, authorityEnvironmentId)` — drives the dashboard's "all companies for this env" view.

**Lifecycle**:
- Created (`isActive=true`) → may be flipped to `isActive=false` → may be hard-deleted via `DELETE /api/admin/users/{userId}/assignments/{assignmentId}`.
- Per Q1: an inactive or deleted assignment removes future-login access; existing tokens remain valid until expiry.

**Relationships**:
- N assignments per User; N per Company; N per AuthorityEnvironment.
- Assignment + AuthorityEnvironment together resolve to which TransactionRole/permissions apply.

---

## 8. TenantContext (runtime — not persisted)

**Purpose**: Per-request thread-local snapshot of the active session. Constructed by `TenantFilter` from JWT claims; cleared at request end.

**Fields** (per spec §5.2 + Constitution XV.6):
- `userId` — UUID
- `companyId` — UUID, **nullable** in `ADMIN_MODE`
- `authorityEnvironmentId` — small integer
- `authority` — `'ETA' | 'ZATCA'`
- `environment` — `'PRODUCTION' | 'PREPROD' | 'SIMULATION' | 'SANDBOX'`
- `mode` — `'ADMIN_MODE' | 'OPERATIONAL_MODE'`
- `isSuperUser` — boolean
- *(permissions NOT held here — resolved on demand by `PermissionService`)*

**Lifecycle**: Constructed at filter chain entry; held in `ThreadLocal`; cleared in `finally`.

---

## 9. SessionContext (runtime — response payload, not persisted)

**Purpose**: The shape returned by `GET /api/session/context` (FR-025, FR-026). Authoritative source for Angular sidebar + button gating.

**Shape** (full schema in `contracts/auth-api.openapi.yaml`):
```
{
  userId: UUID,
  isSuperUser: boolean,
  mode: 'ADMIN_MODE' | 'OPERATIONAL_MODE',
  loginContext: {
    authority: 'ETA' | 'ZATCA',
    environment: string,
    authorityEnvironmentId: integer
  },
  companies: [
    {
      companyId: UUID,
      companyNameEn: string,
      companyNameAr: string,
      isActive: boolean,
      modules: {
        // ETA session keys: invoice, receipt, customers, items, configuration
        // ZATCA session keys: standard, simplified, customers, items, configuration
        <moduleKey>: {
          visible: boolean,
          permissions: {
            view: boolean, create: boolean, edit: boolean,
            delete: boolean, cancel?: boolean, transfer?: boolean,
            refresh: boolean, submit?: boolean
          }
        }
      }
    }
  ]
}
```

**Population rules**:
- For a regular user: `companies[]` contains exactly the companies the user has at least one active assignment for under the active `authorityEnvironmentId`.
- For a Super User in `OPERATIONAL_MODE`: `companies[]` contains all active companies for the active `authorityEnvironmentId`; every module's `permissions.*` flags are `true` (Constitution XVII.3).
- For a Super User in `ADMIN_MODE`: `companies[]` is empty; the Angular shell displays admin sidebar.
- A module's `visible` is `true` iff the user has at least the `VIEW` permission on it; for non-visible modules, `permissions.*` flags are all `false` (kept in payload for consistent typing).

---

## State diagrams

### Company

```text
        create()                   deactivate()
   ∅ ─────────────► ACTIVE ──────────────────────► INACTIVE
                       ▲                                │
                       └────── (no reactivate API) ─────┘
                                  (Wave 5: hidden behind manual DB op
                                   if needed; UI does not expose)
```

### User

```text
        create()                deactivate()
   ∅ ─────────────► ACTIVE ────────────────► INACTIVE
                       ▲                          │
                       └────── activate() ────────┘    (FR-034 explicitly
                                                        permits activate)
```

### Assignment (UserCompanyTransactionRole)

```text
        grant()                   set isActive=false / delete()
   ∅ ──────────────► ACTIVE ────────────────────────────────────► (gone)
                       ▲                                              │
                       └────────── grant() new row ───────────────────┘
```

### Session

```text
       successful login                    token expiry (~8h)
   ∅ ────────────────────► ACTIVE SESSION ───────────────────► EXPIRED → re-login required
                                  │                                ▲
                                  └────── logout() ────────────────┘
                                  (no refresh; no force-logout — Decision 1)
```

---

## Cross-entity invariants enforced in service layer

| ID | Invariant | Source | Enforcement point |
|----|-----------|--------|-------------------|
| INV-01 | Login `(authority, environment)` MUST exist as an active `AuthorityEnvironment` | FR-001, Constitution III.1 | `AuthService.login` step 2 |
| INV-02 | Regular-user login MUST have `companyId`; Super-User MAY omit it | FR-017, FR-019 | `AuthService.login` step 5–6 |
| INV-03 | Regular-user login `(user, company, authorityEnv)` MUST have ≥1 active `UserCompanyTransactionRole` | FR-018 | `AuthService.login` step 6 |
| INV-04 | Operational request MUST have `mode = OPERATIONAL_MODE` | FR-030, Constitution VII.4 | `PermissionAspect` (Decision 9) |
| INV-05 | Operational query MUST scope by `(companyId, authorityEnvironmentId)` from JWT — NOT request payload | FR-029, FR-031, Constitution II.4 | Repository helper / aspect |
| INV-06 | Tax-number unique per `authority_environment_id` for active companies | FR-007 + Decision 6 | `AdminAssignmentService.create` (and Wave 6+ config-create) |
| INV-07 | At least one active Super User MUST exist | FR-036a, Decision 7 | `AdminUserService` pre-persist check |
| INV-08 | Admin endpoint MUST require `isSuperUser=true` | FR-036, Constitution VII.2 | `SecurityConfig` route matcher + `@PreAuthorize` |
| INV-09 | JWT permission claims MUST be absent | FR-022, Constitution XV.8 | `JwtTokenProvider.create` — type-checked claim builder |
| INV-10 | Same generic credentials error on bad email vs bad password | FR-024 | `AuthService.login` step 1 |
| INV-11 | Branch `branchCode` unique per `companyId` when present | FR-005 | DB constraint `uq_branch_code` |

---

## Index plan (Wave 5 only)

Already covered in implementation plan SQL but consolidated here for `/speckit.tasks`:

| Index | Purpose |
|-------|---------|
| `users_email_key` (unique) | Login lookup |
| `idx_companies_tax` | Tax-number uniqueness probe |
| `idx_companies_active` | Active-company filtering for dashboards |
| `idx_branches_company` | Branch list by company |
| `idx_uctr_user_env` | Post-credentials Company dropdown |
| `idx_uctr_company_env` | Super-User Operational-Mode dashboard |
| `uq_user_company_env_tx` | Assignment dedupe (the canonical RBAC unique key) |

Wave 5 does **not** add the `(company_id, authority_environment_id)` compound indexes Constitution XXV.2 calls for on operational tables — those tables (eta_*, zatca_*) don't exist yet (Wave 6+).
