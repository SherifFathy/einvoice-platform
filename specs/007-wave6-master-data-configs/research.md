# Phase 0 — Research: Wave 6 Master Data and Certificate Configurations

**Branch**: `007-wave6-master-data-configs` | **Date**: 2026-05-05

## Purpose

This document resolves the implementation-detail decisions that `spec.md` deliberately left to the planning phase, and consolidates the load-bearing technical choices for Wave 6. The five clarifications already integrated into `spec.md` (Q1 hard delete, Q2 English-only-UI carry-forward, Q3 save-blind validation, Q4 schema-only branch column, Q5 audit deferred to Wave 7) are not reopened here.

The two items the clarification report flagged as **Outstanding / Deferred** — `customer_type` enumeration and exact permission/role binding — are decided here.

---

## Decision 1 — Compound-tenancy filter enforcement (Constitution XV.7)

**Decision**: Introduce a small `OperationalRepositorySupport` helper in `platform-core/repository/support/` that exposes Spring Data JPA `Specification<T>` helpers for `companyIdEquals(...)` and `authorityEnvironmentIdEquals(...)`, and a single combined `inActiveTenant()` specification that reads from `TenantContext`. Every Wave 6 repository method that returns master data or configs goes through `findAll(Specification, Pageable)` (or `findOne(Specification)`), with `inActiveTenant()` always `AND`-ed onto the specification chain. Direct `findById(...)` is forbidden in Wave 6 services for operational entities — services must compose `id == :id AND inActiveTenant()` so a row owned by a different tenant cannot be loaded.

A PMD/Checkstyle custom rule (or a unit test that reflects over repository methods) verifies that every operational repository method uses the support class. The same rule rejects any `@Query` annotation that does not include both `:companyId` and `:authorityEnvironmentId` parameters bound from `TenantContext`.

**Rationale**:
- Constitution XV.7 mandates compound filtering on every operational repository query. A specification-based approach forces composition rather than allowing raw `findById` to bypass the filter.
- A reflective unit test is cheap (Wave 6 has fewer than 20 operational repository methods) and catches regressions when Wave 7 adds more.
- The base specification reads from `TenantContext` (per-request `ThreadLocal` populated by Wave 5's `TenantFilter`), so services do not need to thread `companyId` through every call. This also mirrors how `@RequiresPermission` already reads from the same context.

**Alternatives considered**:
- AspectJ around-advice that rewrites JPA queries: rejected — too magical; failures at runtime are hard to debug; specification composition is already idiomatic in Spring Data JPA.
- Hibernate `@Filter` / `@FilterDef` with global `enable_filter`: rejected — works for entity reads but does not cover native queries; harder to verify in tests; one more Hibernate-specific lock-in.
- Manual `Where(...)` JPQL on every query: rejected — the very repetition that Constitution XV.7 was written to prevent.
- Trust developers to compose filters by convention with code review: rejected — Wave 5 retro showed two near-misses where `findById` was used in a half-written branch; mechanical enforcement is cheaper than retros.

---

## Decision 2 — Configuration upsert semantics

**Decision**: Both `PUT /api/companies/{id}/eta/config` and `PUT /api/companies/{id}/zatca/config` are **upsert** semantics: idempotent replacement of the (at-most-one) row identified by `(companyId, authorityEnvironmentId)`. There is **no `POST` endpoint** for configurations. `GET` on a never-configured tenant returns `200` with `null` body fields rather than `404`, so the screen always renders the empty form. The DB-level unique constraint `uq_eta_config (company_id, authority_environment_id)` and the matching ZATCA constraint guarantee the singleton invariant; race losers see `409` mapped from the constraint-violation exception.

**Rationale**:
- Spec FR-012 / FR-015 explicitly say "at most one configuration record per company-and-environment pair." Upsert via `PUT` is the cleanest REST shape for a singleton resource.
- A `POST` endpoint would invite "create then update" client patterns that race each other; the singleton uniqueness constraint would make one of the two `POST`s fail.
- Returning `200 null` for never-configured tenants spares the Angular form an extra "is this 404 or did the request fail?" branch.

**Alternatives considered**:
- `POST` for create + `PUT` for update: rejected — couples clients to the existence check; adds `404` semantics that confuse the empty-form initial render.
- `PATCH` semantics (partial update merging JSON): rejected — the configuration is small and tightly coupled (e.g., changing `compliance_certificate` typically also changes `compliance_api_secret`); partial updates make the consistency story unclear.
- Soft `is_active` on configs to allow multiple historical rows per (company, environment): rejected — Wave 6 explicitly stores at most one row per scope (FR-012 / FR-015); audit history will live in `audit_logs` (Wave 7) or in a future `eta_config_history` table if needed.

---

## Decision 3 — `customer_type` enumeration per authority

**Decision**: Persist `customer_type` as a free-text column in Wave 6 (matching the V45 / V46 DDL: `customer_type VARCHAR(30)`). Validate at the application layer against the following authority-specific allowlists:

| Authority | Allowed values | Source |
|-----------|---------------|--------|
| ETA       | `B` (Business), `P` (Person), `F` (Foreigner) | ETA Invoice/Receipt JSON spec — `receiver.type` field |
| ZATCA     | `B` (Business), `P` (Person)                  | ZATCA UBL spec — `cac:AccountingCustomerParty/cac:Party/cac:PartyLegalEntity` exists for B; absent for P |

Validation lives in `EtaCustomerService.create/update` and `ZatcaCustomerService.create/update`. Invalid values produce `VALIDATION_ERROR` with a `details.field = "customerType"` and `details.allowed = ["B", "P", ...]` payload.

**Rationale**:
- The implementation-plan SQL is already `VARCHAR(30)` (not an enum), which keeps Phase 2 flexibility (e.g., adding ETA's `business_buyer` distinction if the spec expands). Application-layer allowlist gives the same correctness guarantee with a clearer error message.
- ETA and ZATCA differ on the third value (`F` Foreigner is ETA-only). Keeping the lists in code per authority avoids one shared lookup table that would require authority-aware filtering on every read.
- Free-text in the column also lets us migrate to `IDENTIFICATION_DOCUMENT_VALIDATION` style ("only Foreigners must provide passport id_value") without DDL change.

**Alternatives considered**:
- PostgreSQL `ENUM` type per authority: rejected — schema-level enums on Postgres require a migration to add a value, which Phase 2 may need.
- Single shared `customer_types` lookup table with `(authority, code, label)` rows: rejected — adds an unnecessary join on every read; the value set is small and stable.
- Persist as a numeric code: rejected — values are mnemonic letters in the authority specs; using numbers diverges from the authority documentation that operators read.

---

## Decision 4 — Address-data JSONB shape

**Decision**: `address_data` is a JSONB column on both `eta_customers` and `zatca_customers`. Wave 6 stores per-authority shapes:

```jsonc
// ETA address_data
{
  "country": "EG",
  "governorate": "Cairo",
  "regionCity": "Nasr City",
  "street": "10 Kornish El Nil",
  "buildingNumber": "12",
  "postalCode": "11371",
  "floor": "5",
  "room": "501",
  "landmark": "next to the bank",
  "additionalInformation": "..."
}

// ZATCA address_data
{
  "streetName": "King Fahd Road",
  "buildingNumber": "1234",
  "additionalNumber": "5678",
  "city": "Riyadh",
  "postalCode": "11564",
  "districtName": "Al-Olaya",
  "country": "SA"
}
```

Validation checks at the service layer: required keys per authority (ETA requires `country`, `governorate`, `regionCity`, `street`, `buildingNumber`; ZATCA requires `streetName`, `buildingNumber`, `city`, `postalCode`, `districtName`, `country`) — all other keys optional. Unknown keys are accepted and stored verbatim (forward compatibility with future authority field additions).

**Rationale**:
- Both authorities have address shapes that are stable but extensible; JSONB lets us avoid a `customer_addresses` join table while still storing the full shape.
- Per-authority validation keeps the contract enforceable now without locking the schema.
- Extra keys are accepted (rather than rejected) so a forward-compatible spec change does not require a migration.

**Alternatives considered**:
- Normalised `customer_addresses` table with all possible fields as nullable columns: rejected — wide-row antipattern; ETA and ZATCA share fewer than half their fields.
- Separate ETA-address and ZATCA-address tables: rejected — already implicitly partitioned because the parent customer table is per authority; an address sub-table per authority would double the join count for no gain over JSONB.
- Strict-mode JSON schema validation rejecting unknown keys: rejected — too brittle for a Phase-1 system; allowlist of required keys plus `additionalProperties: true` is the right balance.

---

## Decision 5 — ETA client-secret rotation storage

**Decision**: Store both `client_secret_1` and `client_secret_2` in the `eta_configs` row (per V45 DDL). Wave 6 does **not** automate rotation — both fields are user-editable plaintext strings populated by the admin during ETA onboarding. Wave 7 (or a future submission-hardening wave) will introduce a primary/secondary indicator and the failover-on-401 logic; Wave 6 simply persists what the user typed.

**Rationale**:
- ETA's onboarding flow gives operators two secrets they rotate between manually; storing both is a Phase-1 honesty requirement, not an automation feature.
- Adding rotation logic in Wave 6 without the matching submission flow (which lives in Wave 7) would be dead code.

**Alternatives considered**:
- Single secret column with a manual rotation step (delete-and-paste): rejected — lossy; operators sometimes need to compare the old and new values during audit.
- Three-secret rolling window: rejected — ETA's flow is two-secret, not three.

---

## Decision 6 — Permission binding for Wave 6 endpoints

**Decision**: Each Wave 6 endpoint is annotated with `@RequiresPermission` against the master-data permission catalogue from Constitution XVII.7 (verified to exist in Wave 5's `transaction_role_permissions` seed):

| Endpoint group                         | Permission scope | Required action(s)                         |
|----------------------------------------|------------------|--------------------------------------------|
| `GET    /eta/customers`, `GET /eta/customers/{id}`  | `CUSTOMERS`      | `VIEW`                                     |
| `POST   /eta/customers`                | `CUSTOMERS`      | `CREATE`                                   |
| `PUT    /eta/customers/{id}`           | `CUSTOMERS`      | `EDIT`                                     |
| `DELETE /eta/customers/{id}`           | `CUSTOMERS`      | `DELETE`                                   |
| `GET    /eta/items`, etc.              | `ITEMS`          | `VIEW`                                     |
| `POST   /eta/items`                    | `ITEMS`          | `CREATE`                                   |
| `PUT    /eta/items/{id}`               | `ITEMS`          | `EDIT`                                     |
| `DELETE /eta/items/{id}`               | `ITEMS`          | `DELETE`                                   |
| `GET    /eta/config`, `GET /zatca/config` | `CONFIG`      | `VIEW`                                     |
| `PUT    /eta/config`, `PUT /zatca/config` | `CONFIG`      | `EDIT`                                     |
| (ZATCA customers/items mirror ETA)     | `CUSTOMERS` / `ITEMS` (zatca-scoped via JWT authority) | same actions |

The permission scope is **not** authority-prefixed. The same `CUSTOMERS / VIEW` permission grants visibility into ETA customers when the JWT's `authorityEnvironmentId` resolves to an ETA environment, and visibility into ZATCA customers when it resolves to a ZATCA environment. This matches the Constitution XVII intent that permissions are role-derived per assignment, and assignments are already authority-environment-scoped (Wave 5's `user_company_transaction_roles.authority_environment_id`).

`REFRESH` permission, listed in XVII.7, is unused in Wave 6 (no list-refresh action) and remains reserved for Wave 7+.

**Rationale**:
- Authority partitioning already exists on the assignment table; layering authority-prefixed permissions on top would double-encode the same constraint and risk drift between the two layers.
- `VIEW`/`CREATE`/`EDIT`/`DELETE` map cleanly to REST verbs; using the existing 5-action set avoids growing the permission vocabulary in Wave 6.
- `CONFIG` is a separate scope from `CUSTOMERS`/`ITEMS` per Constitution XVII.7, which keeps the principle of least privilege live: an `ACCOUNTANT` with `VIEW`/`CREATE`/`REFRESH` on `CUSTOMERS` and `ITEMS` cannot read ZATCA private keys.

**Alternatives considered**:
- Authority-prefixed permissions (`ETA_CUSTOMERS_VIEW`, `ZATCA_CUSTOMERS_VIEW`, …): rejected — doubles the permission count; the assignment-row authority is already authoritative; doubles maintenance burden when adding a third authority.
- One `MANAGE_CONFIG` permission gating all four config endpoints: rejected — Constitution XVII.7 explicitly enumerates `VIEW` and `EDIT` (and others) per scope; collapsing to a single permission would lose the read-only-config role that some auditors will need.
- Skip `@RequiresPermission` on read endpoints and rely on tenant filtering alone: rejected — Constitution XVII.5 ("Permissions are role-derived") plus XIV.6 ("Backend enforces the same rules independently of the UI") require explicit permission checks even on reads.

---

## Decision 7 — Inactive-row visibility default

**Decision**: List endpoints (`GET /eta/customers`, `GET /eta/items`, ZATCA mirrors) accept an optional query parameter `includeInactive=false|true`, defaulting to `false`. When `false`, only rows with `is_active = true` are returned. The Angular list components surface a "Show inactive" toggle that flips the parameter. Detail endpoints (`GET /eta/customers/{id}`) return the row regardless of `is_active` — explicit ID lookup is not filtered.

**Rationale**:
- FR-007 requires inactive customers to be excluded by default; the same applies to items by FR-008.
- A query-parameter-driven toggle keeps the URL stable and bookmarkable; the alternative of two endpoint variants (`/active`, `/all`) would multiply paths.
- Detail endpoints intentionally bypass the default filter so a deactivation-then-reactivation flow can complete without breaking deep links.

**Alternatives considered**:
- Always return both and filter on the frontend: rejected — defeats SC-006 latency targets at scale; pushes filtering responsibility off the backend, which is the source of truth.
- Two separate endpoints (`/customers/active`, `/customers/all`): rejected — REST hygiene; multiplies route count for an attribute filter.
- Server-side default to `includeInactive=true`: rejected — contradicts FR-007's "MUST exclude inactive customers from default list views unless the user opts to show them."

---

## Decision 8 — Hard-delete of referenced rows in Wave 6 (per spec Q1)

**Decision**: Per spec Q1, Wave 6 hard-deletes always succeed because no document table references customers or items yet. The Wave 6 `DELETE` endpoint returns `204 No Content` on success and `404 Not Found` only when the row does not exist (or belongs to another tenant — same response code; no leakage of cross-tenant existence). No `409` is returned by Wave 6 `DELETE`.

When Wave 7 introduces the invoice tables (`eta_invoice_headers`, etc.), the Wave 7 spec will specify the pre-delete reference check that returns `409 CUSTOMER_REFERENCED_BY_INVOICES` (or analogous) before invoking the delete. The data-model column `is_active` remains available throughout for users who want to deactivate without removing the row.

**Rationale**:
- Reaffirms spec Q1; pinned here so the contract test for Wave 6 `DELETE` does not assert any 409 path that would falsely become a regression in Wave 7.

---

## Decision 9 — Pagination, sorting, and search semantics

**Decision**: List endpoints accept `page` (0-based, default `0`), `size` (default `20`, max `100`), and `sort` (default `name_en,asc`). Search is exact-substring case-insensitive matching applied at the database via `ILIKE '%term%'` against the search-eligible columns named in FR-024 / FR-025. The unified multi-company list (FR-022) is the same endpoint shape — the JWT determines which companies the user can see; no `companyIds[]` query parameter is needed. Filtering by company within that set uses an optional `companyId=<uuid>` query parameter.

**Rationale**:
- 0-based pagination matches Spring Data's default; the cap at 100 prevents pathological requests.
- `ILIKE` on indexed text columns hits SC-006's 2s p95 target at the documented scale (10 companies × ~5,000 customers = 50k rows). If the search ever exceeds the target, V47+ can introduce trigram indexes without API change.
- Driving the visible companies from the JWT (rather than an explicit query parameter) is consistent with Constitution II.4 / XV.7.

**Alternatives considered**:
- Cursor-based pagination: rejected — overkill at the documented scale; offset pagination is sufficient and cheaper to implement.
- PostgreSQL full-text search: rejected — the spec calls out exact-substring as out-of-scope for fuzziness; FTS would have to be tuned per language (EN/AR), which is its own project.

---

## Decision 10 — Operational endpoints reject Admin Mode

**Decision**: Every Wave 6 controller is annotated with a `@RequireOperationalMode` (or equivalent) marker that reads `TenantContext.mode` and rejects with `COMPANY_CONTEXT_REQUIRED` (HTTP `403`) when the request is in `ADMIN_MODE`. This is enforced before `@RequiresPermission` so that Super Users in Admin Mode see the operational-mode-required error rather than a permission denial that would falsely suggest the action could succeed with a different role.

**Rationale**:
- Constitution VII.3 forbids Super Users from accessing operational data while in Admin Mode; VII.4 mandates the specific error code `COMPANY_CONTEXT_REQUIRED`.
- Ordering the check before permission evaluation produces a clearer error message and prevents Super Users from being misled by a `FORBIDDEN` that has nothing to do with their permissions.

**Alternatives considered**:
- Repeat the mode check inside each service: rejected — mechanical duplication; aspect-style enforcement is cheaper.
- Bake the mode check into `@RequiresPermission`: rejected — mode and permission are orthogonal concerns; collapsing them would make the failure-cause less diagnosable.

---

## Open items deferred to Wave 7+ (intentionally out of Wave 6 scope)

- Pre-delete reference check on customers and items (Wave 7, when invoice tables exist).
- Audit rows for Wave 6 writes (Wave 7's `audit_logs` recreation in V51; spec Q5).
- Outbound connectivity test from configuration screen (Wave 7+ "Test connection" button alongside the submission client).
- Field-level encryption-at-rest for secret/key/cert columns (Phase 2, per Constitution XVIII.3).
- Branch-scoped configuration UI and partitioning semantics (future wave; spec Q4 keeps the column reserved).
- Bulk import / export of customers and items (out-of-scope per spec Assumptions).
- Trigram or full-text search on customer / item names (deferred; present plan meets SC-006 with `ILIKE`).
