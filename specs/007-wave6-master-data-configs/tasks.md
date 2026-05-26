cluade---
description: "Task list for Wave 6 — Authority-Separated Master Data and Certificate Configurations"
---

# Tasks: Wave 6 — Master Data and Certificate Configurations

**Input**: Design documents from `/specs/007-wave6-master-data-configs/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/, quickstart.md

**Tests**: REQUIRED. Constitution Principle XXIV mandates contract tests for the new REST surface, integration tests for tenant + authority-environment isolation, and permission-enforcement tests for `@RequiresPermission` gates. Test tasks are interleaved per story.

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story. All file paths are repo-relative from `D:\Cou\Spring Course In 28 Minutes\Projects\einvoice-platform\`.

## Path Conventions (Wave 6 actual layout — multi-module Maven + Angular)

- Backend modules at repo root: `platform-core/`, `platform-api/` (others untouched in Wave 6).
- Frontend at `frontend/src/app/`.
- Flyway migrations at `platform-core/src/main/resources/db/migration/`.
- Test sources at `<module>/src/test/java/...` (backend) and `frontend/src/app/.../*.spec.ts` (frontend).
- Contracts at `specs/007-wave6-master-data-configs/contracts/`.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Confirm baseline state and pre-stage configuration before Wave 6 implementation begins. Branch `007-wave6-master-data-configs` already exists with the Wave 5 commit as its parent.

- [x] T001 Confirm working tree clean and on branch `007-wave6-master-data-configs`; verify `mvn -q -DskipTests verify` and `cd frontend && npm ci && npm run build` succeed against the current Wave-5 baseline (captures pre-Wave-6 green state for rollback comparison).
- [x] T002 [P] Update `Docs/configuration-reference.md` with a Wave-6 section listing the new tables (`eta_customers`, `eta_items`, `zatca_customers`, `zatca_items`, `eta_configs`, `zatca_configs`, `zatca_chain_state`), the new permission scopes (`CUSTOMERS`, `ITEMS`, `CONFIG`) and their actions per Constitution XVII.7, and the Phase-1 plain-text storage trade-off (FR-026, Constitution XVIII).
- [x] T003 [P] Update `Docs/deployment-guide.md` with Wave-6 deployment notes: V45–V47 migrations apply on top of V44, no new env vars introduced, no new infrastructure components.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Schema, domain entities, repositories, support infrastructure, and permission gating that every user story depends on. **No user story work may begin until this phase is complete.**

### 2A — Flyway migrations (V45–V47, sequential by version number)

- [x] T004 Create `platform-core/src/main/resources/db/migration/V45__eta_master_data_and_config.sql` per implementation-plan §6.1 V45: creates `eta_configs` (with optional `branch_id` column per spec Q4 — column exists, no UI/API exposure), `eta_customers` (with `uq_eta_customer_tax`), `eta_items` (with `uq_eta_item_code`), and `idx_eta_customers_ctx`, `idx_eta_items_ctx`. All FK to `companies(id)`, `branches(id)` (nullable), `authority_environments(id)`.
- [x] T005 Create `platform-core/src/main/resources/db/migration/V46__zatca_master_data_and_config.sql` per implementation-plan §6.1 V46: creates `zatca_configs` (with optional `branch_id` per spec Q4), `zatca_chain_state` (with `uq_zatca_chain` and `invoice_counter` BIGINT default 0), `zatca_customers` (with `uq_zatca_customer_vat`), `zatca_items` (with `uq_zatca_item_code` and `vat_category` enumerated at app layer), and `idx_zatca_customers_ctx`, `idx_zatca_items_ctx`.
- [x] T006 Create `platform-core/src/main/resources/db/migration/V47__operational_indexes.sql` per implementation-plan §6.1 V47: adds `idx_eta_configs_ctx`, `idx_zatca_configs_ctx`, `idx_zatca_chain_ctx` on `(company_id, authority_environment_id)`. Constitution XXV.2 compound-index requirement.
- [x] T007 Apply V45–V47 against a clean local DB and confirm schema with `psql -c '\d+ eta_customers'`, `\d+ zatca_customers`, `\d+ eta_items`, `\d+ zatca_items`, `\d+ eta_configs`, `\d+ zatca_configs`, `\d+ zatca_chain_state`. Verify the 7 unique constraints and 7 compound indexes are present. **FR-026 / Constitution XVIII.3 assertion**: confirm via `information_schema.columns` that every secret/key/certificate column on `eta_configs` (`client_id`, `client_secret_1`, `client_secret_2`, `token_name`, `token_pass`, `token_url`, `submission_url`) and on `zatca_configs` (`private_key`, `device_uuid`, `csr`, `compliance_certificate`, `compliance_api_secret`, `production_certificate`, `production_api_secret`) has `data_type = 'text'` (no `varchar(N)`, no `bytea`, no encrypted-column wrapper). This locks the Phase-1 plain-text storage shape so the Phase-2 AES-256-GCM upgrade can be a storage-layer change with no schema migration.

### 2B — Operational repository support (Constitution XV.7)

- [x] T008 Create `platform-core/src/main/java/com/einvoice/core/repository/support/OperationalRepositorySupport.java` exposing static `Specification<T>` factories: `companyIdEquals(UUID)`, `authorityEnvironmentIdEquals(Short)`, `inActiveTenant()` (combines both, reading from `TenantContext`). Per research Decision 1.
- [x] T009 Create `platform-core/src/test/java/com/einvoice/core/repository/support/OperationalRepositorySupportTest.java` — unit test that exercises the three specifications against an in-memory criteria-builder mock to verify the `WHERE` clauses produced.
- [x] T010 Create `platform-core/src/test/java/com/einvoice/core/repository/CompoundFilterEnforcementTest.java` — reflective test with **two assertions**: (a) every `@Query`-annotated method on repositories under `com.einvoice.core.repository.eta`, `.zatca`, `.config` binds both `:companyId` and `:authorityEnvironmentId` parameters from `TenantContext`; (b) **no service class** under `platform-api/src/main/java/com/einvoice/api/{eta,zatca,config}/` invokes the operational repositories' `findById(...)`, `findAll()` (no-arg), `existsById(...)`, or `deleteById(...)` directly — services MUST go through `JpaSpecificationExecutor` methods composed with `OperationalRepositorySupport.inActiveTenant()`. Implementation: scan compiled bytecode (or AST) of service classes for `INVOKEINTERFACE` calls into operational repositories matching those forbidden method names. Per research Decision 1; closes the loophole that the Specification-only design would otherwise leave the @Query rule vacuous when no `@Query` methods exist.

### 2C — Domain entities (one file each, parallelizable)

- [x] T011 [P] Create `platform-core/src/main/java/com/einvoice/core/domain/eta/EtaCustomer.java` per data-model.md §1: fields, `@Entity`, `@Table(name="eta_customers")`, JSONB type binding for `addressData`, lifecycle `@CreationTimestamp`/`@UpdateTimestamp`.
- [x] T012 [P] Create `platform-core/src/main/java/com/einvoice/core/domain/eta/EtaItem.java` per data-model.md §2.
- [x] T013 [P] Create `platform-core/src/main/java/com/einvoice/core/domain/zatca/ZatcaCustomer.java` per data-model.md §3.
- [x] T014 [P] Create `platform-core/src/main/java/com/einvoice/core/domain/zatca/ZatcaItem.java` per data-model.md §4.
- [x] T015 [P] Create `platform-core/src/main/java/com/einvoice/core/domain/zatca/ZatcaChainState.java` per data-model.md §7. Includes `invoiceCounter`, `previousInvoiceHash`, `lastUpdatedAt` fields.
- [x] T016 [P] Create `platform-core/src/main/java/com/einvoice/core/domain/config/EtaConfig.java` per data-model.md §5: includes `branchId` field annotated as nullable so the column maps but is never populated by Wave 6 services.
- [x] T017 [P] Create `platform-core/src/main/java/com/einvoice/core/domain/config/ZatcaConfig.java` per data-model.md §6: includes the same nullable `branchId`, all required PEM/secret fields as TEXT.

### 2D — Repositories (one file each, parallelizable)

- [x] T018 [P] Create `platform-core/src/main/java/com/einvoice/core/repository/eta/EtaCustomerRepository.java` extending `JpaRepository<EtaCustomer, UUID>` and `JpaSpecificationExecutor<EtaCustomer>`. No `findById` exposed publicly; service must compose `id == :id AND inActiveTenant()` via specifications (research Decision 1).
- [x] T019 [P] Create `platform-core/src/main/java/com/einvoice/core/repository/eta/EtaItemRepository.java` analogous to T018.
- [x] T020 [P] Create `platform-core/src/main/java/com/einvoice/core/repository/zatca/ZatcaCustomerRepository.java` analogous to T018.
- [x] T021 [P] Create `platform-core/src/main/java/com/einvoice/core/repository/zatca/ZatcaItemRepository.java` analogous to T018.
- [x] T022 [P] Create `platform-core/src/main/java/com/einvoice/core/repository/zatca/ZatcaChainStateRepository.java` with `findByCompanyAndAuthorityEnvironment` reading from `TenantContext`.
- [x] T023 [P] Create `platform-core/src/main/java/com/einvoice/core/repository/config/EtaConfigRepository.java` with upsert helper (`save(...)` is the upsert when used with a key-based `findOne(...)`).
- [x] T024 [P] Create `platform-core/src/main/java/com/einvoice/core/repository/config/ZatcaConfigRepository.java` analogous to T023.

### 2E — Domain exceptions and error mapping

- [x] T025 [P] Create `platform-core/src/main/java/com/einvoice/core/error/DuplicateTaxNumberException.java` (FR-006 ETA → `DUPLICATE_TAX_NUMBER_IN_CONTEXT`).
- [x] T026 [P] Create `platform-core/src/main/java/com/einvoice/core/error/DuplicateVatNumberException.java` (FR-006 ZATCA → `DUPLICATE_VAT_NUMBER_IN_CONTEXT`).
- [x] T027 [P] Create `platform-core/src/main/java/com/einvoice/core/error/DuplicateInternalCodeException.java` (FR-011 → `DUPLICATE_INTERNAL_CODE_IN_CONTEXT`).
- [x] T028 [P] Create `platform-core/src/main/java/com/einvoice/core/error/ConfigNotFoundException.java` (used internally; HTTP layer maps to 200-with-null body per research Decision 2, never to 404).
- [x] T029 [P] Create `platform-core/src/main/java/com/einvoice/core/error/InvalidAuthorityForRouteException.java`, `InvalidEnvironmentForAuthorityException.java`, `BranchIdNotAllowedException.java`, `InvalidCustomerTypeException.java`, `InvalidItemTypeException.java`, `InvalidVatCategoryException.java`, `InvalidAddressDataException.java` — one class each in `platform-core/src/main/java/com/einvoice/core/error/`. Map to the codes in `contracts/error-codes.md`.
- [x] T030 Update `platform-api/src/main/java/com/einvoice/api/error/GlobalExceptionHandler.java` to map all 10 new exceptions to the corresponding stable error codes per `contracts/error-codes.md`. Detail-payload conventions (`DUPLICATE_*` → `{conflictingId, field}`; `INVALID_*` → `{field, value, allowed}`; `INVALID_ADDRESS_DATA` → `{missingKeys}`).

### 2F — Operational-mode aspect (Constitution VII.4, research Decision 10)

- [x] T031 Create `platform-security/src/main/java/com/einvoice/security/operational/RequireOperationalMode.java` annotation (target: TYPE and METHOD, runtime retention).
- [x] T032 Create `platform-security/src/main/java/com/einvoice/security/operational/OperationalModeAspect.java` AOP advice that reads `TenantContext.mode` and throws `CompanyContextRequiredException` (Wave 5 existing exception) when `mode == ADMIN_MODE`. Aspect ordering: must run **before** `PermissionAspect` so Super Users see `COMPANY_CONTEXT_REQUIRED` (403) rather than `FORBIDDEN`.
- [x] T033 [P] Create `platform-security/src/test/java/com/einvoice/security/operational/OperationalModeAspectTest.java` — unit test verifying ADMIN_MODE rejection and aspect ordering relative to `PermissionAspect`.

### 2G — Permission seed verification

- [x] T034 Verify the Wave 5 V42 seed already includes the master-data permissions per Constitution XVII.7: `CUSTOMERS / {VIEW, CREATE, EDIT, DELETE, REFRESH}`, `ITEMS / {VIEW, CREATE, EDIT, DELETE, REFRESH}`, `CONFIG / {VIEW, CREATE, EDIT, DELETE, REFRESH}` for `COMPANY_ADMIN`; and `CUSTOMERS / {VIEW, CREATE, REFRESH}`, `ITEMS / {VIEW, CREATE, REFRESH}` for `ACCOUNTANT`; `*/VIEW` for `VIEWER`. If any are missing, add `platform-core/src/main/resources/db/migration/V47a__wave6_master_data_permissions_seed.sql` to fill the gap.

### 2H — Frontend routing skeleton (no real components yet)

- [x] T035 Update `frontend/src/app/app.routes.ts` to add three lazy-loaded routes: `/customers` → `customers/customer-routing.module`, `/items` → `items/item-routing.module`, `/config` → `config/config-routing.module`. All three routes guarded by the existing Wave-5 `authGuard` plus a new operational-mode guard.
- [x] T036 Create `frontend/src/app/shared/guards/operational-mode.guard.ts` — Angular guard that reads `SessionContextService.snapshot$.mode`, redirects to `/dashboard` with a snackbar message when `mode === 'ADMIN_MODE'` for any of the three new routes.
- [x] T037 Update `frontend/src/app/layout/sidebar/sidebar.component.ts` to add Customers, Items, and Configuration menu entries gated by `appHasPermission` directives bound to the corresponding scope/action keys (`CUSTOMERS/VIEW`, `ITEMS/VIEW`, `CONFIG/VIEW`). Verify the sidebar still matches Constitution XIV.4.

**Checkpoint**: Foundation ready — user story implementation can now begin in parallel.

---

## Phase 3: User Story 1 — Maintain customers per authority and environment (Priority: P1) 🎯 MVP

**Goal**: Authorized users can create, view, update, hard-delete, and deactivate ETA and ZATCA customer entries scoped to a company and authority environment. Customers are isolated across the four authority/environment partitions; duplicate `taxNumber` (ETA) or `vatNumber` (ZATCA) within the same scope is rejected.

**Independent Test**: Sign in to ETA Sandbox as a `COMPANY_ADMIN`, create "Acme LLC" with tax number `100200300`. Switch to ETA Production — list is empty; same tax number can be reused there. Switch to ZATCA Sandbox — different table, no leakage. Verify duplicate-create within the same scope returns `409 DUPLICATE_TAX_NUMBER_IN_CONTEXT`. Verify a `VIEWER`-roled user sees rows but no create/edit/delete buttons.

### Tests for User Story 1 (write FIRST, ensure they FAIL before implementation)

- [x] T038 [P] [US1] Contract test `platform-api/src/test/java/com/einvoice/api/eta/EtaCustomerContractTest.java` — exercises every path in `contracts/master-data-api.openapi.yaml` for `/eta/customers/**`. Verifies status codes, response shapes, error-code envelopes for `400 INVALID_*`, `409 DUPLICATE_TAX_NUMBER_IN_CONTEXT`, `403 FORBIDDEN`, `404 NotFound`.
- [x] T039 [P] [US1] Contract test `platform-api/src/test/java/com/einvoice/api/zatca/ZatcaCustomerContractTest.java` — analogous coverage for `/zatca/customers/**`.
- [x] T040 [P] [US1] Integration test `platform-api/src/test/java/com/einvoice/api/eta/EtaCustomerIsolationIT.java` (Testcontainers postgres:16-alpine) — for SC-001: creates customers across two ETA environments and one ZATCA environment, asserts list/detail queries never cross. 50-probe randomized fuzz scenario included.
- [x] T041 [P] [US1] Integration test `platform-api/src/test/java/com/einvoice/api/zatca/ZatcaCustomerIsolationIT.java` — analogous for ZATCA.
- [x] T042 [P] [US1] Integration test `platform-api/src/test/java/com/einvoice/api/eta/EtaCustomerPermissionIT.java` — verifies VIEWER can `GET` but cannot `POST/PUT/DELETE` (403 `FORBIDDEN`). Also verifies Admin-Mode rejection (403 `COMPANY_CONTEXT_REQUIRED`).
- [x] T043 [P] [US1] Integration test `platform-api/src/test/java/com/einvoice/api/zatca/ZatcaCustomerPermissionIT.java` — analogous.

### Implementation for User Story 1 — Backend

- [x] T044 [P] [US1] Create DTO `platform-api/src/main/java/com/einvoice/api/eta/dto/EtaCustomerResponse.java` matching `contracts/master-data-api.openapi.yaml#/components/schemas/EtaCustomer`.
- [x] T045 [P] [US1] Create DTO `platform-api/src/main/java/com/einvoice/api/eta/dto/EtaCustomerWriteRequest.java` matching `EtaCustomerWriteRequest`. Bean Validation annotations: `@NotBlank` on `nameEn`, `@Valid` on `addressData`, `@Email(nullable)` on `contactEmail`.
- [x] T046 [P] [US1] Create DTO `platform-api/src/main/java/com/einvoice/api/zatca/dto/ZatcaCustomerResponse.java`.
- [x] T047 [P] [US1] Create DTO `platform-api/src/main/java/com/einvoice/api/zatca/dto/ZatcaCustomerWriteRequest.java` (note `customerType ∈ {B,P}` only, ZATCA-specific allowlist).
- [x] T048 [P] [US1] Create address-shape validators in `platform-api/src/main/java/com/einvoice/api/shared/validation/`: `EtaAddressDataValidator.java` (required keys: `country, governorate, regionCity, street, buildingNumber`), `ZatcaAddressDataValidator.java` (required keys: `streetName, buildingNumber, city, postalCode, districtName, country`). Per research Decision 4. Throws `InvalidAddressDataException` with missing-keys details.
- [x] T049 [US1] Create `platform-api/src/main/java/com/einvoice/api/eta/service/EtaCustomerService.java` with methods `list`, `get`, `create`, `update`, `delete`, `deactivate`. Uses `OperationalRepositorySupport.inActiveTenant()` for every read; on duplicate-tax-number from DataIntegrityViolationException maps to `DuplicateTaxNumberException`. Validates `customerType ∈ {B, P, F}` and ETA address shape (research Decision 3, 4). Hard-delete unconditional in Wave 6 (spec Q1). The `deactivate` method is called from the `update` handler when the request transitions `isActive` from `true` to `false` — there is no separate PATCH endpoint (FR-007 via PUT-with-flag).
- [x] T050 [US1] Create `platform-api/src/main/java/com/einvoice/api/zatca/service/ZatcaCustomerService.java` mirroring T049. Validates `customerType ∈ {B, P}` and ZATCA `vatNumber` regex `^3[0-9]{13}3$` when `customerType=B` (15 digits beginning and ending with `3`, per ZATCA TIN spec).
- [x] T051 [US1] Create `platform-api/src/main/java/com/einvoice/api/eta/EtaCustomerController.java` exposing `/api/companies/{companyId}/eta/customers` and `/{customerId}` paths. Annotated with `@RequireOperationalMode` at class level and `@RequiresPermission(scope=CUSTOMERS, action=...)` per method (T049 service mapping). Cross-checks path `companyId` against `TenantContext.companyId` → throws `UnauthorizedContextException` on mismatch.
- [x] T052 [US1] Create `platform-api/src/main/java/com/einvoice/api/zatca/ZatcaCustomerController.java` analogous to T051.

### Implementation for User Story 1 — Frontend

- [x] T053 [P] [US1] Create `frontend/src/app/customers/customer-routing.module.ts` declaring routes `/customers/eta` (list+form for ETA) and `/customers/zatca` (list+form for ZATCA). Active route resolved from `SessionContextService.snapshot$.authority`.
- [x] T054 [P] [US1] Create `frontend/src/app/customers/services/eta-customer.service.ts` with methods `list(params)`, `get(id)`, `create(payload)`, `update(id, payload)`, `delete(id)`, `deactivate(id)`. Returns RxJS observables typed against generated TypeScript interfaces from the OpenAPI schema.
- [x] T055 [P] [US1] Create `frontend/src/app/customers/services/zatca-customer.service.ts` analogous.
- [x] T056 [P] [US1] Create `frontend/src/app/customers/eta-customer-list.component.{ts,html,scss}` — Angular Material table with columns: Company, Name (EN), Name (AR with `dir="rtl"`), Tax Number, Customer Type, Active, Actions. Pagination, sort, search input, "Show inactive" toggle, Company filter. Action buttons gated by `*appHasPermission="'CUSTOMERS/CREATE'"` etc. Per FR-027/028 — chrome English-only.
- [x] T057 [P] [US1] Create `frontend/src/app/customers/eta-customer-form.component.{ts,html,scss}` — reactive form mirroring `EtaCustomerWriteRequest`. Arabic name field gets `dir="rtl"`; chrome English. ETA address sub-form with required-field highlighting per research Decision 4. `Save` button disabled until form valid.
- [x] T058 [P] [US1] Create `frontend/src/app/customers/zatca-customer-list.component.{ts,html,scss}` — same shape as T056 but with `VAT Number` column and ZATCA-specific allowlist (`B`/`P`).
- [x] T059 [P] [US1] Create `frontend/src/app/customers/zatca-customer-form.component.{ts,html,scss}` — ZATCA address sub-form (`streetName`, `buildingNumber`, etc.). VAT-number 15-digit-ending-in-3 inline validator when `customerType=B`.
- [x] T060 [P] [US1] Create component spec `frontend/src/app/customers/eta-customer-list.component.spec.ts` — verifies permission-gated buttons hide for VIEWER role, table renders with correct columns, search/filter/toggle wire up to the service. **FR-027/028 assertions**: (a) every column header, button label, tooltip, and validation message rendered by the component is an English literal (no Angular i18n / locale strings); (b) the Arabic-name display cell carries `dir="rtl"` while the surrounding row remains LTR.
- [x] T061 [P] [US1] Create component spec `frontend/src/app/customers/zatca-customer-list.component.spec.ts` analogous.
- [x] T062 [P] [US1] Create component spec `frontend/src/app/customers/eta-customer-form.component.spec.ts` — reactive form validation rules: `nameEn` required, ETA address required keys, `customerType ∈ {B, P, F}` allowlist on the dropdown, `taxNumber` required-when-B rule. **FR-027/028 assertions**: (a) every field label, placeholder, button text, and validation error message is an English literal; (b) the `nameAr` input and any Arabic address line input carries `dir="rtl"` while the surrounding form chrome (labels, buttons) stays LTR.
- [x] T063 [P] [US1] Create component spec `frontend/src/app/customers/zatca-customer-form.component.spec.ts` analogous (different allowlist, VAT regex).

**Checkpoint**: User Story 1 complete. ETA and ZATCA customer screens functional; isolation, permission, and Admin-Mode rejection ITs green.

---

## Phase 4: User Story 2 — Maintain items per authority with authority-specific tax fields (Priority: P1)

**Goal**: Authorized users can create, view, update, hard-delete, and deactivate ETA and ZATCA item entries with each authority's distinct tax modeling (ETA: itemType + tax type/subtype/rate; ZATCA: vatCategory + vatRate).

**Independent Test**: Create an ETA item with `internalCode=SKU-001`, `itemType=EGS`, tax fields. Switch context to ZATCA Sandbox; create a ZATCA item with the same internal code, `vatCategory=S`, `vatRate=15`. Both exist independently. Re-creating `SKU-001` in ETA Sandbox returns `409 DUPLICATE_INTERNAL_CODE_IN_CONTEXT`. ZATCA item with `vatCategory=Z, vatRate=5` rejected (rule: rate must be 0 for non-S categories).

### Tests for User Story 2

- [x] T064 [P] [US2] Contract test `platform-api/src/test/java/com/einvoice/api/eta/EtaItemContractTest.java` — every path in `contracts/master-data-api.openapi.yaml` for `/eta/items/**`. Includes `INVALID_ITEM_TYPE`, `DUPLICATE_INTERNAL_CODE_IN_CONTEXT`, hard-delete 204.
- [x] T065 [P] [US2] Contract test `platform-api/src/test/java/com/einvoice/api/zatca/ZatcaItemContractTest.java` — `INVALID_VAT_CATEGORY`, `vatRate=0` rule for non-S categories, hard-delete.
- [x] T066 [P] [US2] Integration test `platform-api/src/test/java/com/einvoice/api/eta/EtaItemIsolationIT.java` — same pattern as T040 but for items.
- [x] T067 [P] [US2] Integration test `platform-api/src/test/java/com/einvoice/api/zatca/ZatcaItemIsolationIT.java`.
- [x] T068 [P] [US2] Integration test `platform-api/src/test/java/com/einvoice/api/eta/EtaItemPermissionIT.java` — VIEWER read-only; ACCOUNTANT can create+view but not delete (per Constitution XVII.8).
- [x] T069 [P] [US2] Integration test `platform-api/src/test/java/com/einvoice/api/zatca/ZatcaItemPermissionIT.java` analogous.

### Implementation for User Story 2 — Backend

- [x] T070 [P] [US2] Create DTO `platform-api/src/main/java/com/einvoice/api/eta/dto/EtaItemResponse.java`.
- [x] T071 [P] [US2] Create DTO `platform-api/src/main/java/com/einvoice/api/eta/dto/EtaItemWriteRequest.java` with `@NotBlank` on `internalCode`, `itemCode`, `nameEn`; `@Pattern` enforcing `itemType ∈ {GS1, EGS}` (delegated to `InvalidItemTypeException` via `@AssertTrue` cross-validator).
- [x] T072 [P] [US2] Create DTO `platform-api/src/main/java/com/einvoice/api/zatca/dto/ZatcaItemResponse.java`.
- [x] T073 [P] [US2] Create DTO `platform-api/src/main/java/com/einvoice/api/zatca/dto/ZatcaItemWriteRequest.java` with `vatCategory ∈ {S, Z, E, O}` allowlist and cross-field rule "vatRate must be 0 when vatCategory != S".
- [x] T074 [US2] Create `platform-api/src/main/java/com/einvoice/api/eta/service/EtaItemService.java` with `list/get/create/update/delete/deactivate`. Validates `itemType ∈ {GS1, EGS}`, `unitPrice ≥ 0`, `taxRate ≥ 0`. Maps DB unique-constraint violation on `internal_code` to `DuplicateInternalCodeException`. The `deactivate` method is called from the `update` handler when `isActive` transitions `true → false` (same PUT-with-flag design as T049; FR-008).
- [x] T075 [US2] Create `platform-api/src/main/java/com/einvoice/api/zatca/service/ZatcaItemService.java` analogous; enforces vatCategory allowlist and vatRate rule at the service layer.
- [x] T076 [US2] Create `platform-api/src/main/java/com/einvoice/api/eta/EtaItemController.java` exposing `/api/companies/{companyId}/eta/items/**`. Annotated with `@RequireOperationalMode` and `@RequiresPermission(scope=ITEMS, action=...)`.
- [x] T077 [US2] Create `platform-api/src/main/java/com/einvoice/api/zatca/ZatcaItemController.java` analogous.

### Implementation for User Story 2 — Frontend

- [x] T078 [P] [US2] Create `frontend/src/app/items/item-routing.module.ts`.
- [x] T079 [P] [US2] Create `frontend/src/app/items/services/eta-item.service.ts`.
- [x] T080 [P] [US2] Create `frontend/src/app/items/services/zatca-item.service.ts`.
- [x] T081 [P] [US2] Create `frontend/src/app/items/eta-item-list.component.{ts,html,scss}` — table with Internal Code, Item Type, Item Code, Name (EN), Name (AR with `dir="rtl"`), Unit Price, Tax Type/Subtype/Rate columns. Permission-gated actions.
- [x] T082 [P] [US2] Create `frontend/src/app/items/eta-item-form.component.{ts,html,scss}` — reactive form. `itemType` select with `GS1`/`EGS` options.
- [x] T083 [P] [US2] Create `frontend/src/app/items/zatca-item-list.component.{ts,html,scss}` — VAT Category column, VAT Rate.
- [x] T084 [P] [US2] Create `frontend/src/app/items/zatca-item-form.component.{ts,html,scss}` — `vatCategory` dropdown with `{S, Z, E, O}`. Conditional `vatRate` field: required when `S`, fixed-disabled-zero otherwise.
- [x] T085 [P] [US2] Create component specs `frontend/src/app/items/{eta-item-list, eta-item-form, zatca-item-list, zatca-item-form}.component.spec.ts` covering form validation rules and permission-gated buttons.

**Checkpoint**: User Story 2 complete. ETA and ZATCA item screens functional; both authorities' tax models validated.

---

## Phase 5: User Story 3 — Maintain ETA certificate and submission configuration per company and environment (Priority: P1)

**Goal**: Authorized users with `CONFIG/EDIT` can read and replace the ETA configuration for the active scope. At most one row per (company, environment); upsert via PUT; save-blind validation only.

**Independent Test**: Sign in as a `COMPANY_ADMIN` for ABC Co in ETA Pre-production, open the Configuration screen, fill all required ETA fields, save, reload, confirm round-trip. Submit again with `branchId` in the body via curl → 400 `BRANCH_ID_NOT_ALLOWED`. Sign in as `ACCOUNTANT` (no `CONFIG` permission) and confirm the screen and API are denied with `403 FORBIDDEN`.

### Tests for User Story 3

- [x] T086 [P] [US3] Contract test `platform-api/src/test/java/com/einvoice/api/config/EtaConfigContractTest.java` — covers GET (200 with all-null body when never configured per Decision 2), PUT (200 with persisted body), `400 BRANCH_ID_NOT_ALLOWED` when `branchId` present in request, missing-required-field 400, `403 FORBIDDEN` for `ACCOUNTANT`.
- [x] T087 [P] [US3] Integration test `platform-api/src/test/java/com/einvoice/api/config/EtaConfigUpsertIT.java` — multiple PUTs against same scope produce one row; concurrent PUTs from two threads either both succeed (last-write-wins) or one fails with the `uq_eta_config` constraint mapped to a 409. Aligns with spec edge case "concurrent saves … latest save wins."
- [x] T088 [P] [US3] Integration test `platform-api/src/test/java/com/einvoice/api/config/EtaConfigPermissionIT.java` — VIEWER cannot read; ACCOUNTANT cannot read; COMPANY_ADMIN can read+write.

### Implementation for User Story 3

- [x] T089 [P] [US3] Create DTO `platform-api/src/main/java/com/einvoice/api/config/dto/EtaConfigResponse.java`.
- [x] T090 [P] [US3] Create DTO `platform-api/src/main/java/com/einvoice/api/config/dto/EtaConfigWriteRequest.java` with `@JsonIgnoreProperties(ignoreUnknown=false)` so unknown fields (notably `branchId`) trigger Jackson `UnrecognizedPropertyException`, mapped to `BranchIdNotAllowedException` in the global handler when the offending field name is `branchId`, `VALIDATION_ERROR` for any other unknown property.
- [x] T091 [US3] Create `platform-api/src/main/java/com/einvoice/api/config/service/EtaConfigService.java` with `read()` (returns existing or all-null shell per research Decision 2) and `replace(EtaConfigWriteRequest)` (upsert with `tokenName`/`tokenPass` required-when-Production cross-validator). Save-blind: required-field/length checks only — no URL parsing, no PEM parsing, no outbound call.
- [x] T092 [US3] Create `platform-api/src/main/java/com/einvoice/api/config/EtaConfigController.java` exposing `GET` and `PUT /api/companies/{companyId}/eta/config`. `@RequireOperationalMode`, `@RequiresPermission(scope=CONFIG, action=VIEW|EDIT)`.

### Frontend for User Story 3

- [x] T093 [P] [US3] Create `frontend/src/app/config/config-routing.module.ts` with routes `/config/eta` and `/config/zatca` (the latter is wired in Phase 6).
- [x] T094 [P] [US3] Create `frontend/src/app/config/services/eta-config.service.ts` with `read()` and `replace(payload)` methods.
- [x] T095 [P] [US3] Create `frontend/src/app/config/eta-config.component.{ts,html,scss}` — single reactive form with all ETA config fields per data-model §5. Plain-text display per FR-026 — no masking. Save button gated by `*appHasPermission="'CONFIG/EDIT'"`. **No `branchId` field anywhere in the template**. Field-level English labels per FR-027.
- [x] T096 [P] [US3] Create component spec `frontend/src/app/config/eta-config.component.spec.ts` — covers (a) empty-form initial render (GET returns all-null), (b) round-trip after PUT, (c) `Save` button hidden for users without `CONFIG/EDIT`, (d) form rejects when required fields blank, (e) **FR-027 assertion**: every label, button, and validation message rendered is an English literal (no i18n keys).

**Checkpoint**: User Story 3 complete. ETA configuration screen functional; permission and Admin-Mode gates active; Q4 enforcement (no `branchId` in API) verified.

---

## Phase 6: User Story 4 — Maintain ZATCA certificate configuration and chain state per company and environment (Priority: P1)

**Goal**: Authorized users can persist ZATCA cryptographic and onboarding artefacts and the platform automatically initializes the corresponding chain-state record on first save (FR-018).

**Independent Test**: Sign in as `COMPANY_ADMIN` in ZATCA Sandbox, fill all required compliance fields, save → 200 with `chainStateInitialized=true`. Verify directly: `psql -c "SELECT invoice_counter FROM zatca_chain_state WHERE …;"` returns `0` and a row exists. Add production cert + secret + expiry on a second save; row update preserves compliance fields. Try to save with `branchId` → 400.

### Tests for User Story 4

- [x] T097 [P] [US4] Contract test `platform-api/src/test/java/com/einvoice/api/config/ZatcaConfigContractTest.java` — GET/PUT, `BRANCH_ID_NOT_ALLOWED`, missing-required-field, `chainStateInitialized` present in response.
- [x] T098 [P] [US4] Integration test `platform-api/src/test/java/com/einvoice/api/config/ZatcaConfigChainStateInitIT.java` — first PUT creates `zatca_chain_state` row with `invoiceCounter=0, previousInvoiceHash=NULL`; subsequent PUTs do not overwrite the chain-state row (FR-018). Cross-environment verification: ZATCA Sandbox chain-state row is independent from ZATCA Production chain-state row.
- [x] T099 [P] [US4] Integration test `platform-api/src/test/java/com/einvoice/api/config/ZatcaConfigPermissionIT.java`.

### Implementation for User Story 4

- [x] T100 [P] [US4] Create DTO `platform-api/src/main/java/com/einvoice/api/config/dto/ZatcaConfigResponse.java` including `chainStateInitialized` boolean.
- [x] T101 [P] [US4] Create DTO `platform-api/src/main/java/com/einvoice/api/config/dto/ZatcaConfigWriteRequest.java` with `@JsonIgnoreProperties(ignoreUnknown=false)` and the `branchId` rejection mapping per T090.
- [x] T102 [US4] Create `platform-api/src/main/java/com/einvoice/api/config/service/ZatcaConfigService.java`: `read()` returns config + `chainStateInitialized` flag; `replace(...)` upserts the config row, then ensures (idempotent insert) the matching `zatca_chain_state` row exists with `invoiceCounter=0, previousInvoiceHash=NULL` per FR-018. Single-transaction boundary to prevent partial state.
- [x] T103 [US4] Create `platform-api/src/main/java/com/einvoice/api/config/ZatcaConfigController.java` analogous to T092.

### Frontend for User Story 4

- [x] T104 [P] [US4] Create `frontend/src/app/config/services/zatca-config.service.ts`.
- [x] T105 [P] [US4] Create `frontend/src/app/config/zatca-config.component.{ts,html,scss}` — reactive form with fields per data-model §6. Multi-line `textarea` controls for `privateKey`, `csr`, `complianceCertificate`, `productionCertificate`. `certificateExpiryDate` is a Material datepicker; the value is sent as ISO date and the platform does not parse it (Q3 save-blind).
- [x] T106 [P] [US4] Create component spec `frontend/src/app/config/zatca-config.component.spec.ts` covering empty-form render, round-trip, permission gating, and that `chainStateInitialized` is displayed as a small status indicator alongside the save button after the first successful save.

**Checkpoint**: User Story 4 complete. ZATCA configuration screen functional; chain-state row initialized on first save; SC-007 satisfied.

---

## Phase 7: User Story 5 — Cross-company unified lists with company filter (Priority: P2)

**Goal**: Users assigned to multiple companies see a unified list of customers and items across those companies with the company shown in each row and a filter dropdown.

**Independent Test**: Assign one user to ABC Co and XYZ Co under ETA Pre-production. Create one ETA customer in each company. Open the customers list — both rows visible with their company column. Select XYZ in the company-filter dropdown — only XYZ rows remain. Verify a third company the user is NOT assigned to never appears regardless of any filter.

The backend behavior for this story is already covered by Phase 3 / Phase 4 controllers (the `/customers` and `/items` lists already span all assigned companies — see `CompanyFilterParam` in the OpenAPI). Phase 7 covers the frontend-side multi-company experience and the explicit non-leakage assertion.

### Tests for User Story 5

- [x] T107 [P] [US5] Integration test `platform-api/src/test/java/com/einvoice/api/eta/EtaCustomerMultiCompanyListIT.java` — user with assignments to A and B, optional `?companyId=A`, `?companyId=B`, no parameter (returns A+B), no leakage from C (unassigned).
- [x] T108 [P] [US5] Integration test `platform-api/src/test/java/com/einvoice/api/eta/EtaItemMultiCompanyListIT.java` analogous.
- [x] T109 [P] [US5] Component spec `frontend/src/app/customers/eta-customer-list.component.spec.ts` (extend T060) — adds the multi-company filter scenario: dropdown options come from `SessionContextService.snapshot$.assignedCompanies` filtered to the active authority+environment; selecting one narrows the visible rows.

### Implementation for User Story 5

- [x] T110 [US5] Update `frontend/src/app/customers/eta-customer-list.component.ts` and `zatca-customer-list.component.ts` to derive the company-filter dropdown options from `SessionContextService.snapshot$.assignedCompanies`, scoped to the active authority+environment. Default selection: "All companies" (no filter param). Selection updates the `companyId` query parameter on the underlying service call. Wire the same pattern in `eta-item-list.component.ts` and `zatca-item-list.component.ts`.
- [x] T111 [US5] Update component templates so each row displays a "Company" column with the resolved company name from the same `assignedCompanies` snapshot (no extra HTTP call per row).

**Checkpoint**: User Story 5 complete. Multi-company users see unified lists; filter narrows in <500ms per SC-006.

---

## Phase 8: User Story 6 — Search master data by name and tax/VAT number (Priority: P3)

**Goal**: Users can search the customers list by English name, Arabic name, and tax/VAT number; items list by English name, Arabic name, and internal code. Real-time substring matching.

**Independent Test**: Pre-populate ABC Co with 25 ETA customers. Type partial English name → list narrows. Type partial tax number → list narrows further. Same for items by `internalCode`.

The backend search behavior is already exposed via the `q` query parameter in the OpenAPI. Phase 8 covers (a) the SQL implementation in services, (b) the frontend search input wiring, (c) confirmation that search hits the compound-tenancy filter so it never leaks across scopes.

### Tests for User Story 6

- [x] T112 [P] [US6] Integration test `platform-api/src/test/java/com/einvoice/api/eta/EtaCustomerSearchIT.java` — `q=acm` returns rows matching `nameEn ILIKE %acm%` OR `nameAr ILIKE %acm%` OR `taxNumber ILIKE %acm%`; results stay within the active scope.
- [x] T113 [P] [US6] Integration test `platform-api/src/test/java/com/einvoice/api/zatca/ZatcaCustomerSearchIT.java` — same against `vatNumber`.
- [x] T114 [P] [US6] Integration test `platform-api/src/test/java/com/einvoice/api/eta/EtaItemSearchIT.java` and `ZatcaItemSearchIT.java` — `q` searches `internalCode`, `nameEn`, `nameAr`.

### Implementation for User Story 6

- [x] T115 [US6] Update `EtaCustomerService.list` and `ZatcaCustomerService.list` (T049, T050) to accept an optional `q: String` parameter and compose `Specification<Customer>` with `OR`-joined `ILIKE` clauses against the search-eligible columns per FR-024.
- [x] T116 [US6] Update `EtaItemService.list` and `ZatcaItemService.list` (T074, T075) analogously per FR-025.
- [x] T117 [US6] Update `frontend/src/app/customers/eta-customer-list.component.ts`, `zatca-customer-list.component.ts`, `frontend/src/app/items/eta-item-list.component.ts`, `zatca-item-list.component.ts` to debounce the search input (300ms) and pass the `q` query parameter on the underlying service call.

**Checkpoint**: User Story 6 complete. All search inputs functional and scope-respecting.

---

## Phase 9: Polish & Cross-Cutting Concerns

**Purpose**: Final hardening, performance verification, and sign-off.

- [x] T118 Cross-context isolation harness `platform-api/src/test/java/com/einvoice/api/Wave6IsolationIT.java` — automated SC-001 50-probe randomized fuzz across all four master-data partitions plus configurations. Asserts zero cross-context leakage in any list, detail GET, search, or filter combination.
- [x] T119 [P] Performance smoke test `platform-api/src/test/java/com/einvoice/api/Wave6PerformanceSmokeIT.java` — 10-company × 5,000-customers fixture; `GET /eta/customers` first page p95 < 2s; company filter response p95 < 500ms (SC-006). Capture timings, attach to PR description.
- [x] T120 [P] Update `Docs/deployment-guide.md` Wave-6 section with: (a) how to verify V45–V47 applied, (b) the new permission-scope catalogue users may see, (c) the operational-mode requirement for the three new screens.
- [x] T121 [P] Code cleanup pass: remove any `TODO` markers introduced during Wave-6 implementation; confirm Checkstyle/PMD/SpotBugs run clean (`mvn -q verify`). Verify `frontend/` `npm run lint` runs clean.
- [x] T122 Run `mvn -q clean verify` (full backend test suite, Testcontainers postgres:16-alpine) and `cd frontend && npm run lint && npm test -- --watch=false --browsers=ChromeHeadless`. Both must be green. Capture pass count and attach to PR.
- [x] T123 Execute `quickstart.md` Phases A–L end-to-end on a clean DB and record sign-off in the PR description (each phase ticked in the sign-off matrix at the end of `quickstart.md`).
- [x] T124 Update `specs/007-wave6-master-data-configs/checklists/requirements.md` with a final review pass: re-validate all checklist items pass post-implementation. Add a "Verified by Phase 9 review" annotation to each item.

**Checkpoint**: ✅ Wave 6 ready for review and merge.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — can start immediately on the existing branch.
- **Foundational (Phase 2)**: Depends on Setup. **BLOCKS all user stories.** Within Phase 2: 2A (migrations) → 2C (entities depend on the schema). 2B (support class), 2E (exceptions), 2F (operational-mode aspect), 2G (permission seed), 2H (frontend skeleton) can run in parallel once their inputs exist. 2D (repositories) depends on 2C.
- **User Story 1 (Phase 3, P1)**: Depends on Phase 2.
- **User Story 2 (Phase 4, P1)**: Depends on Phase 2. Independent of US1; can run in parallel.
- **User Story 3 (Phase 5, P1)**: Depends on Phase 2. Independent of US1/US2.
- **User Story 4 (Phase 6, P1)**: Depends on Phase 2. Independent of US1/US2/US3.
- **User Story 5 (Phase 7, P2)**: Depends on Phase 3 + Phase 4 (extends customer/item lists).
- **User Story 6 (Phase 8, P3)**: Depends on Phase 3 + Phase 4 (adds `q` to existing services).
- **Polish (Phase 9)**: Depends on Phases 3–8 being complete.

### Within Each User Story

- Tests (T038–T043, T064–T069, T086–T088, T097–T099, T107–T109, T112–T114) are written FIRST and must FAIL before implementation begins (TDD per Constitution XXIV).
- DTOs before services; services before controllers; controllers before component specs that exercise them.
- Backend complete before frontend integration is verified end-to-end.
- Each story complete before moving to the next priority.

### Parallel Opportunities

- **Within Phase 2**: T011–T017 (entities) all parallel. T018–T024 (repositories) all parallel after entities. T025–T029 (exceptions) parallel. T031, T034, T035, T037 parallel.
- **Within each user story**: All `[P]`-marked tasks can be claimed by independent developers — they touch different files. DTOs (T044–T048; T070–T073; etc.) parallel. Component files in different feature folders parallel.
- **Across user stories**: Once Phase 2 completes, US1, US2, US3, US4 can all be picked up by separate developers in parallel.
- **Phase 9 polish**: T119, T120, T121 all `[P]`.

---

## Parallel Example: Phase 2C — Domain Entities

```bash
# Once V45–V47 have applied, fan out the entity creation:
Task: "T011 [P] Create platform-core/src/main/java/com/einvoice/core/domain/eta/EtaCustomer.java"
Task: "T012 [P] Create platform-core/src/main/java/com/einvoice/core/domain/eta/EtaItem.java"
Task: "T013 [P] Create platform-core/src/main/java/com/einvoice/core/domain/zatca/ZatcaCustomer.java"
Task: "T014 [P] Create platform-core/src/main/java/com/einvoice/core/domain/zatca/ZatcaItem.java"
Task: "T015 [P] Create platform-core/src/main/java/com/einvoice/core/domain/zatca/ZatcaChainState.java"
Task: "T016 [P] Create platform-core/src/main/java/com/einvoice/core/domain/config/EtaConfig.java"
Task: "T017 [P] Create platform-core/src/main/java/com/einvoice/core/domain/config/ZatcaConfig.java"
```

## Parallel Example: User Story 1 — Frontend components

```bash
# Once T044–T052 are implemented, fan out the Angular components:
Task: "T056 [P] [US1] Create eta-customer-list.component.{ts,html,scss}"
Task: "T057 [P] [US1] Create eta-customer-form.component.{ts,html,scss}"
Task: "T058 [P] [US1] Create zatca-customer-list.component.{ts,html,scss}"
Task: "T059 [P] [US1] Create zatca-customer-form.component.{ts,html,scss}"
Task: "T060 [P] [US1] Create eta-customer-list.component.spec.ts"
Task: "T061 [P] [US1] Create zatca-customer-list.component.spec.ts"
```

---

## Implementation Strategy

### MVP First (User Story 1 only)

1. Complete Phase 1: Setup (T001–T003).
2. Complete Phase 2: Foundational (T004–T037) — schema, entities, repositories, support class, error mapping, operational-mode aspect, permission seed, frontend skeleton.
3. Complete Phase 3: User Story 1 — customers (T038–T063).
4. **STOP and VALIDATE**: Run quickstart.md Phases A, B, H, I, K, L. Customers MVP complete.
5. Demo to stakeholders if appropriate.

### Incremental Delivery

1. Setup + Foundational complete → Foundation ready.
2. Add User Story 1 (customers) → Demo MVP slice.
3. Add User Story 2 (items) → Demo full master data.
4. Add User Stories 3 + 4 (configurations) → Demo full configuration management.
5. Add User Story 5 (multi-company filter) → Demo unified list UX.
6. Add User Story 6 (search) → Demo search.
7. Polish phase → Wave 6 ready for merge.

### Parallel Team Strategy

Once Phase 2 lands:
- Developer A: User Story 1 (customers, both authorities).
- Developer B: User Story 2 (items, both authorities).
- Developer C: User Stories 3 + 4 (configurations).
- After all four P1 stories converge: one developer picks up US5 and US6 in sequence (small additions).
- Polish phase shared across team.

---

## Notes

- `[P]` tasks = different files, no dependencies on incomplete tasks at the same level.
- `[US1]` etc. labels map tasks to specific user stories for traceability.
- Each user story is independently completable and testable.
- Tests fail before implementation (TDD per Constitution XXIV).
- Commit after each task or logical group; never bypass hooks (`--no-verify`) without explicit user instruction.
- Stop at any checkpoint to validate story independently against `quickstart.md`.
- All Wave 6 endpoints reject Admin Mode with `COMPANY_CONTEXT_REQUIRED` (Constitution VII.4 / research Decision 10) — verified explicitly by T042/T043/T068/T069/T088/T099 and the Phase-I quickstart step.
- All Wave 6 operational repositories use `OperationalRepositorySupport.inActiveTenant()` — verified by T010 reflective enforcement test.
- No actor columns on any Wave 6 table (spec Q5); no field-level encryption (Constitution XVIII; Phase 2); no outbound authority calls during configuration save (FR-014a/018a, spec Q3); no `branchId` exposed in Wave 6 UI/API (spec Q4).
