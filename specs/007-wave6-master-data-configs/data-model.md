# Phase 1 — Data Model: Wave 6 Master Data and Certificate Configurations

**Branch**: `007-wave6-master-data-configs` | **Date**: 2026-05-05
**Source spec**: [spec.md](./spec.md) §Key Entities + Functional Requirements
**Source plan**: [plan.md](./plan.md) §Project Structure → migrations
**Source research**: [research.md](./research.md) Decisions 1–10
**Source implementation reference**: `Docs/implementation-plan.md` §Wave 6 (V45–V47 SQL)

This document is the canonical entity reference for `/speckit.tasks`. Raw DDL lives in the implementation-plan; this file captures **conceptual** entity shape, validation rules, relationships, and lifecycle so tasks can be derived without round-tripping the DDL.

## Entity tier classification (per Constitution II.2)

| Tier            | Entities                                                                                                | Carries `(companyId, authorityEnvironmentId)`? |
|-----------------|---------------------------------------------------------------------------------------------------------|------------------------------------------------|
| **Operational** | `EtaCustomer`, `EtaItem`, `ZatcaCustomer`, `ZatcaItem`, `EtaConfig`, `ZatcaConfig`, `ZatcaChainState`   | **Yes — both**                                  |

Wave 6 introduces no Catalogue or Global entities. All seven entities are Operational; every read and write flows through the compound `(companyId, authorityEnvironmentId)` filter (Constitution XV.7, research Decision 1).

---

## 1. EtaCustomer (operational)

**Purpose**: A buyer or recipient referenced on Egyptian-authority documents, partitioned per company and environment so the same legal entity can have separate records in ETA Pre-production and ETA Production without conflict.

**Fields**:
- `id` — UUID, primary key (`gen_random_uuid()` default).
- `companyId` — UUID, FK → `companies(id)`. Required.
- `authorityEnvironmentId` — SMALLINT, FK → `authority_environments(id)`. Required. Service-layer validates this is one of `1` (ETA PRODUCTION) or `2` (ETA PREPROD).
- `customerType` — VARCHAR(30). Validated at the service layer against `{B, P, F}` (Decision 3).
- `nameAr` — VARCHAR(255). Optional. Stored as-is.
- `nameEn` — VARCHAR(255). **Required**, non-empty after trim.
- `taxNumber` — VARCHAR(100). Optional but recommended; required by FR-006's uniqueness constraint to be non-null when `customerType = B` (business). Application-layer validation rejects null `taxNumber` for `customerType = B`.
- `idType` — VARCHAR(50). Optional. Free-text (e.g., `national_id`, `passport`, `commercial_register`).
- `idValue` — VARCHAR(100). Optional. Free-text.
- `addressData` — JSONB. Required. Shape per Decision 4 (ETA address shape with `country`, `governorate`, `regionCity`, `street`, `buildingNumber` required keys).
- `contactEmail` — VARCHAR(255). Optional. Standard email format if present.
- `contactPhone` — VARCHAR(50). Optional. Free-text.
- `isActive` — boolean, default `true`.
- `createdAt`, `updatedAt` — timestamps with timezone, auto-managed.

**Validation rules**:
- `nameEn` non-empty and ≤255 chars.
- `customerType ∈ {B, P, F}` if present (research Decision 3).
- `addressData` parses as JSON object containing the required ETA address keys (research Decision 4).
- `contactEmail` matches a permissive RFC 5322-compatible pattern when non-null.

**Constraints (DB level)**:
- `uq_eta_customer_tax UNIQUE (company_id, authority_environment_id, tax_number)` — uniqueness scoped to the tenant tuple per FR-006. Note: `tax_number` may be NULL for `customerType = P` / `F`; PostgreSQL treats NULLs as distinct, so multiple NULL rows are allowed.
- Index `idx_eta_customers_ctx ON (company_id, authority_environment_id)` (V45).

**Lifecycle**:
- Create → `isActive=true`, `createdAt=updatedAt=now()`.
- Update → mutates writable fields; `updatedAt=now()`. `taxNumber` is mutable but a service-level guard checks it does not collide with another active customer in the same scope.
- Hard delete (per spec Q1) → row removed; no FK from Wave 6 prevents this. Wave 7 will add a pre-delete reference check.
- Deactivate (separate action) → `isActive=false`; row preserved; excluded from default lists (research Decision 7).

**Used by**:
- Wave 7 invoice/receipt header tables (FK arrives in V47+).
- Wave 6 customer screens (Story 1, Story 5, Story 6).

---

## 2. EtaItem (operational)

**Purpose**: A product or service used on Egyptian-authority document lines, partitioned per company and environment.

**Fields**:
- `id` — UUID, primary key.
- `companyId` — UUID, FK → `companies(id)`. Required.
- `authorityEnvironmentId` — SMALLINT, FK → `authority_environments(id)`. Required. Validated to be ETA env (1 or 2).
- `internalCode` — VARCHAR(100). **Required**, non-empty.
- `itemType` — VARCHAR(10). **Required**, one of `{GS1, EGS}` (validated at service layer).
- `itemCode` — VARCHAR(100). **Required**.
- `nameAr` — VARCHAR(255). Optional.
- `nameEn` — VARCHAR(255). **Required**, non-empty.
- `unitType` — VARCHAR(50). Optional but recommended.
- `unitPrice` — NUMERIC(18,5). Optional but typically non-null for usable items.
- `taxType` — VARCHAR(30). Optional. Free-text per ETA spec.
- `taxSubtype` — VARCHAR(30). Optional. Free-text per ETA spec.
- `taxRate` — NUMERIC(8,5). Optional.
- `isActive` — boolean, default `true`.
- `createdAt`, `updatedAt` — timestamps.

**Validation rules**:
- `internalCode` and `itemCode` non-empty after trim.
- `itemType ∈ {GS1, EGS}` (FR-009).
- `unitPrice >= 0` when present.
- `taxRate >= 0` when present.

**Constraints (DB level)**:
- `uq_eta_item_code UNIQUE (company_id, authority_environment_id, internal_code)` — FR-011.
- Index `idx_eta_items_ctx ON (company_id, authority_environment_id)` (V45).

**Lifecycle**: Same as `EtaCustomer` (create / update / hard-delete / deactivate).

**Used by**:
- Wave 7 invoice/receipt line tables (FK arrives in V47+).
- Wave 6 item screens (Story 2, Story 5, Story 6).

---

## 3. ZatcaCustomer (operational)

**Purpose**: A buyer or recipient referenced on Saudi-authority documents.

**Fields**:
- `id` — UUID, primary key.
- `companyId` — UUID, FK → `companies(id)`. Required.
- `authorityEnvironmentId` — SMALLINT, FK → `authority_environments(id)`. Required. Validated to be a ZATCA env (3, 4, or 5).
- `customerType` — VARCHAR(30). Validated at service layer against `{B, P}` (Decision 3 — note ZATCA does not have an `F` case).
- `nameAr` — VARCHAR(255). Optional.
- `nameEn` — VARCHAR(255). **Required**, non-empty.
- `vatNumber` — VARCHAR(100). Optional but required by FR-006 to be non-null when `customerType = B`.
- `idType` — VARCHAR(50). Optional. Free-text (`national_id`, `commercial_registration`, `iqama`, `passport`, etc.).
- `idValue` — VARCHAR(100). Optional.
- `addressData` — JSONB. Required. Shape per Decision 4 (ZATCA address shape).
- `contactEmail`, `contactPhone` — VARCHAR(255), VARCHAR(50). Optional.
- `isActive`, `createdAt`, `updatedAt` — as above.

**Validation rules**:
- `nameEn` non-empty, ≤255.
- `customerType ∈ {B, P}` if present.
- `addressData` JSON object with required ZATCA keys (`streetName`, `buildingNumber`, `city`, `postalCode`, `districtName`, `country`).
- `contactEmail` permissive RFC 5322 pattern.
- ZATCA business customers (`customerType = B`) **MUST** carry a 15-digit `vatNumber` that begins with `3` and ends with `3` per ZATCA spec; service-level format check (regex `^3[0-9]{13}3$`) — Wave 6 stores the value only if it matches the regex. Non-business customers (`customerType = P`) are not subject to this format check and may have a null `vatNumber`.

**Constraints (DB level)**:
- `uq_zatca_customer_vat UNIQUE (company_id, authority_environment_id, vat_number)` — FR-006.
- Index `idx_zatca_customers_ctx ON (company_id, authority_environment_id)` (V46).

**Lifecycle**: Same as `EtaCustomer`.

**Used by**:
- Wave 8 ZATCA standard/simplified header tables (FK arrives in V48+).
- Wave 6 customer screens.

---

## 4. ZatcaItem (operational)

**Purpose**: A product or service used on Saudi-authority document lines.

**Fields**:
- `id` — UUID, primary key.
- `companyId` — UUID, FK → `companies(id)`. Required.
- `authorityEnvironmentId` — SMALLINT, FK → `authority_environments(id)`. Required. Validated to be a ZATCA env.
- `internalCode` — VARCHAR(100). **Required**.
- `itemCode` — VARCHAR(100). Optional (ZATCA does not require an external code; `internalCode` is sufficient).
- `nameAr` — VARCHAR(255). Optional.
- `nameEn` — VARCHAR(255). **Required**.
- `unitType` — VARCHAR(50). Optional.
- `unitPrice` — NUMERIC(18,5). Optional.
- `vatCategory` — VARCHAR(10). **Required**, one of `{S, Z, E, O}` (FR-010).
- `vatRate` — NUMERIC(8,2). Required when `vatCategory = S`; must be `0` when `vatCategory ∈ {Z, E, O}` (service-layer rule).
- `isActive`, `createdAt`, `updatedAt`.

**Validation rules**:
- `internalCode` non-empty.
- `vatCategory ∈ {S, Z, E, O}` (FR-010).
- `vatRate ≥ 0` when present; `vatRate = 0` when `vatCategory ∈ {Z, E, O}`.

**Constraints (DB level)**:
- `uq_zatca_item_code UNIQUE (company_id, authority_environment_id, internal_code)` — FR-011.
- Index `idx_zatca_items_ctx ON (company_id, authority_environment_id)` (V46).

**Lifecycle**: Same as `EtaCustomer`.

**Used by**:
- Wave 8 ZATCA line tables.
- Wave 6 item screens.

---

## 5. EtaConfig (operational singleton per scope)

**Purpose**: ETA submission credentials and POS device descriptors for one (company, authority_environment) pair. Read by Wave 7 submission flows; written only via Wave 6 configuration screen.

**Fields**:
- `id` — UUID, primary key.
- `companyId` — UUID, FK → `companies(id)`. Required.
- `branchId` — UUID, FK → `branches(id)`. **Schema-only in Wave 6** (per spec Q4): column exists; UI does not expose it; API does not accept it; every Wave-6 row has `branchId = NULL`.
- `authorityEnvironmentId` — SMALLINT, FK → `authority_environments(id)`. Required. Validated to be an ETA env (1 or 2).
- `clientId` — TEXT. **Required**, non-empty.
- `clientSecret1` — TEXT. **Required**, non-empty.
- `clientSecret2` — TEXT. **Required**, non-empty (research Decision 5).
- `tokenName` — TEXT. Optional. Required by ETA Production (env 1), optional for Pre-production (env 2). Service-level rule.
- `tokenPass` — TEXT. Optional. Same constraint as `tokenName`.
- `submissionUrl` — TEXT. **Required**, non-empty. **Save-blind** per spec Q3 — no URL syntax check beyond non-empty.
- `tokenUrl` — TEXT. **Required**, non-empty. Save-blind.
- `posSerial`, `posOsVersion`, `posModel` — TEXT. Optional. Receipt-flow descriptors used by Wave 7's ETA Receipt module.
- `isActive` — boolean, default `true`. **Wave 7+ reservation, not used in Wave 6.** The column is provisioned by V45 so a future wave that introduces config-level deactivation (e.g., during environment migration or scheduled credential expiry) can land it as a service-layer change with no schema migration. Wave 6 services never read this column, never write it explicitly (it inherits the DDL default `true`), and never expose it through the API or UI.
- `createdAt`, `updatedAt` — timestamps.

**Validation rules**:
- All required-marked fields non-null and non-empty after trim (Q3 save-blind: only this).
- For `authorityEnvironmentId = 1` (Production): `tokenName` and `tokenPass` must be non-null.
- `branchId` MUST NOT be sent in the request body (research Decision implied by spec Q4). Any request body containing the `branchId` field — null or otherwise — is rejected with `400 BRANCH_ID_NOT_ALLOWED` (see `contracts/error-codes.md`). Stored value is always NULL in Wave 6.

**Constraints (DB level)**:
- `uq_eta_config UNIQUE (company_id, authority_environment_id)` — singleton invariant.
- Index `idx_eta_configs_ctx ON (company_id, authority_environment_id)` (V47).

**Lifecycle**:
- Read → returns `200` with the row, or `200` with all-null body when no row exists for the scope (research Decision 2).
- Replace (`PUT`) → upsert keyed by `(companyId, authorityEnvironmentId)` (Decision 2). One row, replaced in place.
- No `DELETE` endpoint; deactivation via `isActive` is reserved but not exposed in Wave 6 UI.

**Used by**:
- Wave 7 ETA submission flow (`platform-eta` rebuild).
- Wave 6 configuration screen (Story 3).

---

## 6. ZatcaConfig (operational singleton per scope)

**Purpose**: ZATCA cryptographic and onboarding artefacts for one (company, authority_environment) pair.

**Fields**:
- `id` — UUID, primary key.
- `companyId` — UUID, FK → `companies(id)`. Required.
- `branchId` — UUID, FK → `branches(id)`. **Schema-only in Wave 6** (spec Q4); same treatment as `EtaConfig.branchId`.
- `authorityEnvironmentId` — SMALLINT, FK → `authority_environments(id)`. Required. Validated to be a ZATCA env (3, 4, or 5).
- `privateKey` — TEXT. **Required**, non-empty. Plain text per Constitution XVIII.
- `deviceUuid` — TEXT. **Required**, non-empty.
- `csr` — TEXT. **Required**, non-empty (Certificate Signing Request).
- `complianceCertificate` — TEXT. **Required**, non-empty.
- `complianceApiSecret` — TEXT. **Required**, non-empty.
- `productionCertificate` — TEXT. Optional. Null until production onboarding completes for this scope.
- `productionApiSecret` — TEXT. Optional. Same condition.
- `certificateExpiryDate` — DATE. Optional. The platform records what the user typed; Wave 6 does not parse the certificate to verify against this date (Q3 save-blind).
- `isActive` — boolean, default `true`. **Wave 7+ reservation, not used in Wave 6.** Same treatment as `EtaConfig.isActive`: column is provisioned by V46 for forward compatibility, never read or written by Wave 6 services, never exposed through API or UI.
- `createdAt`, `updatedAt` — as above.

**Validation rules**:
- All required-marked fields non-null and non-empty after trim. No PEM parsing, no base64 decoding, no field-shape verification (Q3 save-blind).
- `branchId` MUST NOT be sent in the request body. Any request body containing the `branchId` field is rejected with `400 BRANCH_ID_NOT_ALLOWED` (see `contracts/error-codes.md`). Stored value is always NULL in Wave 6.

**Constraints (DB level)**:
- `uq_zatca_config UNIQUE (company_id, authority_environment_id)` — singleton.
- Index `idx_zatca_configs_ctx ON (company_id, authority_environment_id)` (V47).

**Lifecycle**: Same as `EtaConfig` (read / upsert via PUT; no DELETE).

**Used by**:
- Wave 8 ZATCA submission flow.
- Wave 6 configuration screen (Story 4).

---

## 7. ZatcaChainState (operational singleton per scope)

**Purpose**: The running ZATCA hash-chain state for one (company, authority_environment) pair, shared across both Standard and Simplified flows for that scope (Constitution XII.1, FR-017).

**Fields**:
- `id` — UUID, primary key.
- `companyId` — UUID, FK → `companies(id)`. Required.
- `authorityEnvironmentId` — SMALLINT, FK → `authority_environments(id)`. Required. ZATCA env (3, 4, or 5).
- `invoiceCounter` — BIGINT. **Required**, non-negative; default `0`. The chain counter incremented on every ZATCA submission attempt (Constitution X.5, XII.5).
- `previousInvoiceHash` — TEXT. Nullable. Holds the most recent submitted invoice hash; null when the chain has not yet emitted any invoice.
- `lastUpdatedAt` — timestamp with timezone, default `now()`.

**Validation rules**:
- `invoiceCounter ≥ 0` always.
- Wave 6 never decrements this counter (Constitution X.5).
- Wave 8's submission flow will acquire a `SELECT FOR UPDATE` on this row before incrementing (Constitution XII.3) — Wave 6 establishes the row; Wave 8 establishes the locking discipline.

**Constraints (DB level)**:
- `uq_zatca_chain UNIQUE (company_id, authority_environment_id)` — singleton.
- Index `idx_zatca_chain_ctx ON (company_id, authority_environment_id)` (V47).

**Lifecycle**:
- **Created lazily** by Wave 6: not by user action, but by the first read or write that needs it. The Wave 6 ZATCA configuration `PUT` endpoint, after persisting the config, ensures a chain-state row exists for the same `(companyId, authority_environment_id)` if one does not already exist (FR-018). Initial values: `invoiceCounter = 0`, `previousInvoiceHash = NULL`.
- Read in Wave 6: there is no dedicated REST endpoint for `zatca_chain_state` in Wave 6. The row is read internally by services that compose configuration responses (e.g., the Wave 6 ZATCA config GET payload includes a small derived `chainStateInitialized` boolean for the UI to confirm Wave-7 readiness — see contracts).
- Updated only by Wave 8 submission flow.

**Used by**:
- Wave 8 ZATCA submission flow (locking + increment).
- Wave 6 ZATCA configuration GET payload (informational `chainStateInitialized`).

---

## Cross-cutting relationships and FK summary

```text
companies(id)                                ← (FK)
  └─ eta_customers.company_id, eta_items.company_id,
     zatca_customers.company_id, zatca_items.company_id,
     eta_configs.company_id, zatca_configs.company_id,
     zatca_chain_state.company_id

branches(id)                                  ← (FK; nullable; NULL in Wave 6)
  └─ eta_configs.branch_id, zatca_configs.branch_id

authority_environments(id)                    ← (FK)
  └─ all seven Wave 6 tables.authority_environment_id
```

No FK arrives **into** Wave 6 tables in this wave. Wave 7 (invoice/receipt headers) and Wave 8 (ZATCA standard/simplified headers) will introduce FKs from the document tables to these customers/items, at which point the spec Q1 "hard delete refused if referenced" rule activates.

## Lifecycle invariants enforced across all seven entities

1. **Tenant scope is JWT-derived** — services NEVER accept `companyId` or `authorityEnvironmentId` from the request body or query string (per FR-003, Constitution V, research Decision 1). The only exception is the `/api/companies/{id}/...` path parameter, which the controller cross-checks against `TenantContext.companyId` and rejects on mismatch with `UNAUTHORIZED_CONTEXT`.
2. **No actor columns** — none of the seven tables has `created_by` or `updated_by` (spec Q5). Forensic attribution waits for Wave 7's `audit_logs`.
3. **Plain-text storage** — secret/key/cert columns on `eta_configs` and `zatca_configs` are TEXT, no encryption at rest beyond what Postgres provides; field-level encryption is a Phase-2 storage-layer change with no schema migration required (Constitution XVIII.3).
4. **Compound index coverage** — every operational query path is served by an index keyed on `(company_id, authority_environment_id)` (Constitution XXV.2; V45–V47 add seven such indexes).
5. **Operational mode required** — every read/write of these entities rejects Admin-Mode requests (Constitution VII.4, research Decision 10).
