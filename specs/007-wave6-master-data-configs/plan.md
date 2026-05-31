# Implementation Plan: Wave 6 — Authority-Separated Master Data and Certificate Configurations

**Branch**: `007-wave6-master-data-configs` | **Date**: 2026-05-05 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/007-wave6-master-data-configs/spec.md`

## Summary

Wave 6 adds the platform's first **operational entity** layer on top of the Wave 5 global-vs-operational foundation. Seven new tables — `eta_customers`, `eta_items`, `zatca_customers`, `zatca_items`, `eta_configs`, `zatca_configs`, and `zatca_chain_state` — are created with `(company_id, authority_environment_id)` isolation built in from day one (Constitution II.5). Three Flyway migrations (V45–V47) seed the schema; ten REST endpoint groups (CRUD per master-data table per authority, plus read/replace per config) sit behind `@RequiresPermission` checks bound to the master-data permission set added in Wave 5 (Constitution XVII.7). Three Angular feature folders ship: `customers/`, `items/`, and `config/` (replacing the obsolete pre-Wave-5 customers/items screens that Wave 5 left as stubs).

Five clarifications recorded in `spec.md` shape this wave's implementation: **(Q1)** `DELETE` is a true hard delete in Wave 6 with `is_active=false` provided as a parallel deactivation capability — Wave 7 will add a pre-delete reference check when invoice tables arrive; **(Q2)** the Wave 5 English-only-UI rule carries forward — chrome stays English, `dir="rtl"` permitted only on Arabic data inputs; **(Q3)** save-blind validation — required fields, types, and lengths only, no URL parsing, PEM parsing, or outbound authority calls; **(Q4)** branch scoping on configs is schema-only — the `branch_id` column exists in V45/V46 but UI hides it, API rejects it, every Wave-6 row has `branch_id = NULL`; **(Q5)** actor attribution (`created_by` / `updated_by`) is deferred to Wave 7's audit infrastructure to stay consistent with Wave 5's audit deferral.

This wave is incremental and mechanical relative to Wave 5: no new modules, no new third-party libraries, no new infrastructure components. The constitutional posture is **enforce isolation** (every operational repository query filters by `(company_id, authority_environment_id)` per Constitution XV.7) and **honor the plain-text storage trade-off** (Constitution XVIII; FR-026).

## Technical Context

**Language/Version**: Java 17 (backend), TypeScript 5.x (frontend, Angular 19)
**Primary Dependencies**:
- Backend: Spring Boot 3.4.4 (web, data-jpa, security, validation), Spring Data JPA + Flyway (PostgreSQL 16), Lombok, Jackson, the Wave 5 `@RequiresPermission` AOP, `TenantContext`, `PermissionService`, and `SessionContextAssembler`. No new third-party libraries.
- Frontend: Angular 19, Angular Material, RxJS, the Wave 5 `SessionContextService`, `PermissionService`, `appHasPermission` directive. No new third-party libraries.
**Storage**: PostgreSQL 16 (single instance via Docker Compose). Three new Flyway migrations: **V45** (ETA master data + ETA config), **V46** (ZATCA master data + ZATCA config + ZATCA chain state), **V47** (compound indexes finalisation).
**Testing**:
- Backend: JUnit 5 + Spring Boot Test + Testcontainers (`postgres:16-alpine`); contract tests for each new endpoint group; integration tests for cross-context isolation (Constitution XXIV.5–6, mirrors SC-001).
- Frontend: Karma + Jasmine (existing config) for components and services; component-level tests for the three new feature folders.
**Target Platform**: On-prem Linux server (Docker Compose), browser (Chromium-based for UAT). Constitution Principle XIX.
**Project Type**: Multi-module Maven backend + Angular frontend. Existing layout from Wave 5: `platform-api`, `platform-core`, `platform-security`, `platform-eta`, `platform-zatca`, `platform-jobs`, `platform-pdf`, `frontend/`.
**Performance Goals** (mirrors `spec.md` SC-006 and SC-008):
- Customers/items unified list first page p95 < 2s for users assigned up to 10 companies.
- Company-filter narrowing latency p95 < 500ms after selection.
- Context-switch (logout + re-login under a different `authority_environment_id`) reflects in list output within one page load.
- All operational repository queries hit a compound `(company_id, authority_environment_id)` index (Constitution XXV.2).
**Constraints**:
- No outbound network call to ETA or ZATCA from any Wave 6 endpoint (FR-014a, FR-018a; reaffirmed by Q3).
- No `created_by` / `updated_by` columns on any Wave 6 table (FR-014; reaffirmed by Q5).
- `branch_id` column on `eta_configs` and `zatca_configs` is schema-only — UI hidden, API rejects, every row NULL (FR-013, FR-016; reaffirmed by Q4).
- All Wave 6 operational endpoints are **rejected in Admin Mode** with `COMPANY_CONTEXT_REQUIRED` (Constitution VII.3, VII.4).
- Plain-text storage of secrets and certificates retained — no masking, no encryption-at-rest beyond what the database provides (Constitution XVIII; FR-026).
- The configuration screen displays plain-text secrets to authorized users; this is an **explicit deviation from a strict reading of Constitution VI.2** (see *Constitution Check* below) and is governed by FR-026 + Constitution XVIII as the resolution.
**Scale/Scope**:
- 7 new tables, 3 Flyway migrations (V45–V47), 4 master-data tables × ~5 endpoints + 2 config tables × 2 endpoints = ~24 new REST endpoints, 3 new Angular feature folders, ~10 new components/screens, 7 new domain entities + repositories, 4 new application services + 2 new config services.
- Expected initial data per company: 0–5,000 customers, 0–10,000 items, 1 config row per (company, authority_environment), 1 chain-state row per (company, authority_environment) once first ZATCA submission runs in Wave 8.
- 28 functional requirements, 6 user stories (4× P1, 1× P2, 1× P3), 8 success criteria.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

### Pre-Phase-0 Evaluation

| #     | Principle                                  | Status                | Notes |
|-------|--------------------------------------------|-----------------------|-------|
| I     | Compliance-First                           | N/A                   | No authority document generation in Wave 6; submission flows ship in Wave 7/8. |
| II    | Multi-Tenant Isolation                     | ✅ Pass               | Every Wave 6 operational table carries `(company_id, authority_environment_id)`; FR-001/002/003 codify the four-collection partition model. |
| III   | Authority Environment Architecture         | ✅ Pass               | All seven new tables FK `authority_environment_id` → the V37 catalogue; per-row scoping never overrides JWT (FR-003). |
| IV    | Branch + Environment Segregation           | ✅ Pass               | Per-environment certificate configs delivered (FR-012, FR-015). ZATCA Sandbox / Simulation / Production each get a separate `zatca_configs` row and (when the chain starts in Wave 8) a separate `zatca_chain_state` row (FR-017, Constitution IV.5). |
| V     | Stateless Service                          | ✅ Pass               | All new services and controllers are request-scoped; `TenantContext` (Wave 5) carries per-request state. No cross-request mutable caches introduced. |
| VI    | Server-Side Cryptography                   | ⚠ See Complexity Tracking | Wave 6 introduces no signing or hashing. The configuration screen *displays* plaintext secrets to authorized users (FR-026, plan §Q3 save-blind). Strict literal reading of VI.2 ("Private keys, certificates, CSIDs, client secrets, tokens, or cryptographic material MUST never be sent to Angular") is in tension with this UX. Resolution under XVIII below. |
| VII   | Admin Mode / Operational Mode Separation   | ✅ Pass               | All Wave 6 endpoints are operational and require `mode = OPERATIONAL_MODE` with non-null `companyId` from JWT. Admin Mode requests are rejected with `COMPANY_CONTEXT_REQUIRED` (FR-019, Constitution VII.4). Super User in Admin Mode cannot read/write Wave 6 data even though their flag bypasses RBAC checks elsewhere — Wave 6 enforces VII.3. |
| VIII  | Transaction Module Architecture            | ✅ Pass               | Wave 6 ships master data, not transaction modules. Per Constitution XI.5 the master data is shared across that authority's transaction modules — the Customers/Items screens appear under the authority-scoped sidebar but do not require a specific transaction-module permission to view; the master-data permissions (CUSTOMERS / ITEMS / CONFIG, per XVII.7) gate them. |
| IX    | Immutable Audit                            | ⏭ Deferred to Wave 7  | `audit_logs` table not yet recreated (Wave 5 deferral; reaffirmed by spec Q5). No audit rows are emitted from Wave 6 endpoints. Forensic attribution is unavailable until Wave 7's V51. |
| X     | Deterministic Document Lifecycle           | N/A                   | No document lifecycle in Wave 6. |
| XI    | Physical Document Table Strategy           | ✅ Pass               | XI.5 explicitly: "`eta_customers` and `eta_items`: shared by Invoice and Receipt; `zatca_customers` and `zatca_items`: shared by Standard and Simplified" — Wave 6 implements this verbatim. No per-environment table groups (XI.4) — environment lives in `authority_environment_id`. |
| XII   | ZATCA Hash Chain Integrity                 | ✅ Pass (storage)     | `zatca_chain_state` table created with one row per `(company_id, authority_environment_id)`, shared between Standard and Simplified (FR-017). The pessimistic-lock acquisition during submission (Constitution XII.3) belongs to Wave 8; Wave 6 establishes the storage and the singleton constraint. |
| XIII  | Validation Layering                        | ✅ Pass               | UI guidance via Angular reactive validators; backend domain validation via Bean Validation + service rules (FR-006, FR-011, FR-014a, FR-018a); authority-compliance validation is N/A in Wave 6 (no payload generation). |
| XIV   | Angular Engineering                        | ✅ Pass               | Reactive forms only; permission-gated buttons via `appHasPermission` (FR-021); Customers/Items lists show all assigned companies with a Company column and filter (XIV.5, FR-022); no secret handling in the frontend beyond *display* of values the user themselves typed (resolved under XVIII). |
| XV    | Spring Boot Engineering                    | ✅ Pass               | Layered (controller → service → repo); Flyway V45–V47; `TenantContext` carries `(companyId, authorityEnvironmentId)` for every Wave 6 query; **XV.7 enforcement scaffold lands here** — every Wave 6 repository extends a base class / uses an aspect that injects `companyId` + `authorityEnvironmentId` filters into all read/write queries. The authoritative `/api/session/context` (XV.8) is unchanged from Wave 5 but now carries the master-data permissions from `transaction_role_permissions`. |
| XVI   | Authority Adapter                          | N/A                   | No authority engine work in Wave 6. |
| XVII  | RBAC and Access Control                    | ✅ Pass               | Master-data permissions exactly as XVII.7 (`VIEW`, `CREATE`, `EDIT`, `DELETE`, `REFRESH` on `CUSTOMERS`, `ITEMS`, `CONFIG`). Wave 6 endpoints bind to these via `@RequiresPermission`. The Wave 5 default role permission sets (XVII.8) already include these — `COMPANY_ADMIN` gets all five on all three master-data scopes; `ACCOUNTANT` gets `VIEW`, `CREATE`, `REFRESH` on `CUSTOMERS` and `ITEMS`, none on `CONFIG`; `VIEWER` gets `VIEW` only. |
| XVIII | Plain-Text Certificate Storage Policy      | ✅ Pass               | Storage shape matches XVIII.3 verbatim — all secret/key/cert columns are TEXT in dedicated `eta_configs` / `zatca_configs` tables only. Phase 2 AES-256-GCM upgrade can land at the storage layer with no schema migration. FR-026 documents the trade-off. |
| XIX   | Secure On-Prem Deployment                  | ✅ Pass               | No deployment-topology change. |
| XX    | Reliability and Recovery                   | ✅ Pass               | No long-running jobs in Wave 6. |
| XXI   | Document Preservation                      | N/A                   | Not applicable to Wave 6. |
| XXII  | Signed License Enforcement (Phase 2)       | ✅ Pass               | No company-creation paths added in Wave 6 (Wave 5 already covers admin company creation); license enforcement remains a Phase 2 concern. |
| XXIII | Change Control                             | ✅ Pass               | spec.md → plan.md → tasks.md chain maintained; clarifications recorded with traceable IDs. |
| XXIV  | Testing                                    | ✅ Pass               | Cross-context isolation tests are first-class in Wave 6 (SC-001 = Constitution XXIV.6). Per-endpoint contract tests for the new REST surface; component tests for the three new Angular feature folders; no golden-file work (no payload generation). |
| XXV   | Performance                                | ✅ Pass               | XXV.2 compound-index requirement satisfied — V45/V46 add `idx_eta_customers_ctx`, `idx_eta_items_ctx`, `idx_zatca_customers_ctx`, `idx_zatca_items_ctx`; V47 adds `idx_eta_configs_ctx`, `idx_zatca_configs_ctx`, `idx_zatca_chain_ctx`. SC-006 sets the latency targets. |
| XXVI  | Source of Truth                            | ✅ Pass               | spec.md is functional truth; this plan is implementation sequencing; tasks.md will be the execution detail. |

**Gate Result**: ✅ PASS — one tension flagged (Principle VI vs. configuration-screen plaintext display) and explicitly justified in *Complexity Tracking* below as the deliberate Phase-1 trade-off codified by Principle XVIII and FR-026.

### Post-Phase-1 Re-Evaluation

Re-evaluation after `data-model.md`, `contracts/`, and `quickstart.md` were generated:

- All seven entities in `data-model.md` carry `(companyId, authorityEnvironmentId)` exactly as Constitution II.5 requires. None violate the Global vs. Operational tier model — every Wave 6 entity is **operational**.
- All endpoint contracts in `contracts/master-data-api.openapi.yaml` and `contracts/config-api.openapi.yaml` pin tenant scope to the JWT (no `companyId` or `authorityEnvironmentId` query parameters that the client could spoof — FR-003); this matches Constitution V (stateless services scoped by request context) and XV.7 (compound filter on every operational query).
- The configuration endpoints' OpenAPI explicitly omits `branchId` from the request schemas (Q4) and explicitly omits any `createdBy` / `updatedBy` field from the response schemas (Q5).
- The `error-codes.md` additions extend the Wave 5 catalogue with `DUPLICATE_TAX_NUMBER_IN_CONTEXT`, `DUPLICATE_VAT_NUMBER_IN_CONTEXT`, `DUPLICATE_INTERNAL_CODE_IN_CONTEXT`, and `CONFIG_NOT_FOUND` — all stable codes; no message-only error paths.
- The hard-delete edge case (Q1) is reflected in both data-model.md (no FK from Wave 7 invoices yet) and the contracts (`DELETE` returns `204 No Content` unconditionally in Wave 6).
- Quickstart Phase D explicitly exercises Constitution XXIV.6 (cross-context isolation) for both customers and items, mapping to SC-001.
- No new principle violations detected. **Gate Result**: ✅ PASS.

## Project Structure

### Documentation (this feature)

```text
specs/007-wave6-master-data-configs/
├── plan.md                                  # This file (/speckit.plan output)
├── research.md                              # Phase 0 output
├── data-model.md                            # Phase 1 output (entities + relationships)
├── quickstart.md                            # Phase 1 output (smoke-test runbook)
├── contracts/
│   ├── master-data-api.openapi.yaml         # /api/companies/{id}/eta/{customers|items}/* and zatca counterparts
│   ├── config-api.openapi.yaml              # /api/companies/{id}/{eta|zatca}/config
│   └── error-codes.md                       # Wave 6 additions to the Wave 5 catalogue
├── checklists/
│   └── requirements.md                      # Already created during /speckit.specify
└── tasks.md                                 # Will be created by /speckit.tasks
```

### Source Code (repository root)

Existing multi-module Maven backend layout from Wave 5 — Wave 6 adds files under `platform-core` (entities + Flyway), `platform-api` (REST controllers + services), and three new Angular feature folders. No new modules introduced; no files removed.

```text
platform-core/
└── src/main/
    ├── java/com/einvoice/core/
    │   ├── domain/
    │   │   ├── eta/
    │   │   │   ├── EtaCustomer.java                 # NEW
    │   │   │   └── EtaItem.java                     # NEW
    │   │   ├── zatca/
    │   │   │   ├── ZatcaCustomer.java               # NEW
    │   │   │   ├── ZatcaItem.java                   # NEW
    │   │   │   └── ZatcaChainState.java             # NEW
    │   │   └── config/
    │   │       ├── EtaConfig.java                   # NEW
    │   │       └── ZatcaConfig.java                 # NEW
    │   ├── repository/
    │   │   ├── eta/{EtaCustomerRepository, EtaItemRepository}.java
    │   │   ├── zatca/{ZatcaCustomerRepository, ZatcaItemRepository, ZatcaChainStateRepository}.java
    │   │   ├── config/{EtaConfigRepository, ZatcaConfigRepository}.java
    │   │   └── support/
    │   │       └── OperationalRepositorySupport.java # NEW: compound-filter base / aspect (Constitution XV.7)
    │   └── error/
    │       ├── DuplicateTaxNumberException.java     # NEW (FR-006 ETA)
    │       ├── DuplicateVatNumberException.java     # NEW (FR-006 ZATCA)
    │       ├── DuplicateInternalCodeException.java  # NEW (FR-011)
    │       └── ConfigNotFoundException.java         # NEW
    └── resources/db/migration/
        ├── V45__eta_master_data_and_config.sql      # NEW
        ├── V46__zatca_master_data_and_config.sql    # NEW
        └── V47__operational_indexes.sql             # NEW

platform-api/
└── src/main/java/com/einvoice/api/
    ├── eta/
    │   ├── EtaCustomerController.java               # NEW
    │   ├── EtaItemController.java                   # NEW
    │   └── service/{EtaCustomerService, EtaItemService}.java
    ├── zatca/
    │   ├── ZatcaCustomerController.java             # NEW
    │   ├── ZatcaItemController.java                 # NEW
    │   └── service/{ZatcaCustomerService, ZatcaItemService}.java
    ├── config/
    │   ├── EtaConfigController.java                 # NEW
    │   ├── ZatcaConfigController.java               # NEW
    │   └── service/{EtaConfigService, ZatcaConfigService}.java
    ├── error/
    │   └── GlobalExceptionHandler.java              # UPDATE: map four new exceptions to error codes
    └── (existing admin/, auth/, session/ folders untouched)

platform-security/
└── (no Wave 6 changes — Wave 5's @RequiresPermission, TenantContext, PermissionAspect are reused)

frontend/src/app/
├── customers/                                       # NEW feature folder (replaces Wave-5 stub)
│   ├── eta-customer-list.component.{ts,html,scss}
│   ├── eta-customer-form.component.{ts,html,scss}
│   ├── zatca-customer-list.component.{ts,html,scss}
│   ├── zatca-customer-form.component.{ts,html,scss}
│   ├── customer-routing.module.ts
│   └── services/{eta-customer.service, zatca-customer.service}.ts
├── items/                                           # NEW feature folder (replaces Wave-5 stub)
│   ├── eta-item-list.component.{ts,html,scss}
│   ├── eta-item-form.component.{ts,html,scss}
│   ├── zatca-item-list.component.{ts,html,scss}
│   ├── zatca-item-form.component.{ts,html,scss}
│   ├── item-routing.module.ts
│   └── services/{eta-item.service, zatca-item.service}.ts
├── config/                                          # NEW feature folder
│   ├── eta-config.component.{ts,html,scss}
│   ├── zatca-config.component.{ts,html,scss}
│   ├── config-routing.module.ts
│   └── services/{eta-config.service, zatca-config.service}.ts
├── app.routes.ts                                    # UPDATE: add routes for the three feature folders
├── layout/sidebar/sidebar.component.ts              # UPDATE: add Customers / Items / Configuration menu entries gated by master-data permissions
└── (existing admin/, auth/, dashboard/, shared/ untouched except sidebar entries)
```

**Structure Decision**: All Wave 6 backend code fits in two existing modules (`platform-core` for entities + repositories + migrations; `platform-api` for controllers + services). The deliberate split of services between `platform-core` (domain) and `platform-api` (application) follows the Wave 5 convention; in Wave 6 the application services live in `platform-api` because they are thin orchestrators over repositories and the existing `PermissionAspect` from `platform-security`. The three frontend feature folders (`customers/`, `items/`, `config/`) are new sibling folders to the Wave 5 `admin/` and `dashboard/` folders. No code lands in `platform-eta`, `platform-zatca`, `platform-jobs`, `platform-pdf`, or `platform-security`. Wave 7 will revisit `platform-eta` and `platform-zatca` for submission flows.

## Complexity Tracking

> One Constitution-Check tension flagged. The remaining principles pass cleanly.

| Violation                                                                 | Why Needed                                                                                                                                                                                                                                                                              | Simpler Alternative Rejected Because                                                                                                                                                                                                                                                                                                                |
|---------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Configuration screen sends plaintext secrets/keys/certs to Angular (tension with Constitution VI.2) | Authorized users with `CONFIG VIEW`/`CONFIG EDIT` permission must be able to read what they previously typed in order to verify, audit, and update the values. The configuration form is the *primary* operational tool for credential management in Phase 1; without read-back the only way to confirm a stored value is to re-paste it. FR-026 + Constitution XVIII codify Phase 1's plain-text storage decision and implicitly extend it to the read-back path used by the same admins who wrote the values. | Masked display with a "Show" toggle: rejected — adds UI scope without storage-layer benefit, and would have to be removed in Phase 2 anyway when AES-256-GCM lands at the storage layer (Constitution XVIII.3). Write-only fields (typeable but never returned): rejected — defeats the purpose of an "edit my saved values" workflow. Server-side comparison-only (user types the candidate, server says match/mismatch): rejected — onerous for a 4-line CSR or a multi-line PEM key. |

The tension above is the only intentional deviation in Wave 6, and it is explicit, scoped, and time-bounded (Phase 2 will remove it together with the storage-layer encryption upgrade per Constitution XVIII.3).
