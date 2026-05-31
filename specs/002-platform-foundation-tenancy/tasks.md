# Tasopenopenks: Platform Foundation, Tenancy & Master Data

**Input**: Design documents from `/specs/002-platform-foundation-tenancy/`
**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, contracts/

**Tests**: Not explicitly requested — test tasks omitted. Tests can be added per-story if needed.

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

## Path Conventions

- **Backend**: `platform-core/src/`, `platform-security/src/`, `platform-api/src/`
- **Frontend**: `frontend/src/app/`
- **Migrations**: `platform-core/src/main/resources/db/migration/`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Add Wave 1 dependencies, configure JWT and encryption properties, prepare migration framework

- [x] T001 Add jjwt dependency (io.jsonwebtoken:jjwt-api, jjwt-impl, jjwt-jackson) to platform-security/pom.xml
- [x] T002 [P] Add spring-boot-starter-aop dependency to platform-core/pom.xml for audit AOP
- [x] T003 [P] Add apache-poi dependency (already in parent BOM) to platform-api/pom.xml for Excel import
- [x] T004 [P] Configure JWT properties (secret, expiry, refresh expiry) in platform-api/src/main/resources/application.yml and application-dev.yml
- [x] T005 [P] Configure encryption master key property reference in platform-api/src/main/resources/application.yml (reads from ENCRYPTION_MASTER_KEY env var)

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core infrastructure that MUST be complete before ANY user story can be implemented

**CRITICAL**: No user story work can begin until this phase is complete

### Database Schema

- [x] T006 Create V2__create_companies_and_branches.sql migration in platform-core/src/main/resources/db/migration/ *(must complete before T007, T008; T009-T012 can run parallel)*
- [x] T007 [P] Create V3__create_authority_configs.sql migration in platform-core/src/main/resources/db/migration/ *(depends on T006: FK to branches)*
- [x] T008 [P] Create V4__create_users_and_roles.sql migration (includes users, user_company_roles, user_environment_permissions, refresh_tokens, seed Super Admin) in platform-core/src/main/resources/db/migration/
- [x] T009 [P] Create V5__create_customers.sql migration in platform-core/src/main/resources/db/migration/
- [x] T010 [P] Create V6__create_items.sql migration in platform-core/src/main/resources/db/migration/
- [x] T011 [P] Create V7__create_invoices.sql migration (invoices, invoice_lines, invoice_vat_breakdown) in platform-core/src/main/resources/db/migration/
- [x] T012 [P] Create V8__create_audit_logs.sql migration (with REVOKE UPDATE/DELETE) in platform-core/src/main/resources/db/migration/
- [x] T013 Create V9__create_indexes.sql migration in platform-core/src/main/resources/db/migration/

### Domain Enums

- [x] T014 [P] Create Authority enum in platform-core/src/main/java/com/einvoice/core/domain/enums/Authority.java
- [x] T015 [P] Create Environment enum in platform-core/src/main/java/com/einvoice/core/domain/enums/Environment.java
- [x] T016 [P] Create Role enum in platform-core/src/main/java/com/einvoice/core/domain/enums/Role.java
- [x] T017 [P] Create InvoiceType enum in platform-core/src/main/java/com/einvoice/core/domain/enums/InvoiceType.java
- [x] T018 [P] Create InvoiceStatus enum in platform-core/src/main/java/com/einvoice/core/domain/enums/InvoiceStatus.java
- [x] T019 [P] Create CustomerType enum in platform-core/src/main/java/com/einvoice/core/domain/enums/CustomerType.java
- [x] T020 [P] Create AuthorityScope enum in platform-core/src/main/java/com/einvoice/core/domain/enums/AuthorityScope.java
- [x] T021 [P] Create InvoiceResetPolicy enum in platform-core/src/main/java/com/einvoice/core/domain/enums/InvoiceResetPolicy.java
- [x] T021a [P] Create VatCategory enum (S=Standard, Z=Zero-rated, E=Exempt, O=Out-of-scope) in platform-core/src/main/java/com/einvoice/core/domain/enums/VatCategory.java

### Core Domain Entities

- [x] T022 [P] Create Company entity in platform-core/src/main/java/com/einvoice/core/domain/Company.java
- [x] T023 [P] Create Branch entity in platform-core/src/main/java/com/einvoice/core/domain/Branch.java
- [x] T024 [P] Create User entity in platform-core/src/main/java/com/einvoice/core/domain/User.java
- [x] T025 [P] Create UserCompanyRole entity in platform-core/src/main/java/com/einvoice/core/domain/UserCompanyRole.java
- [x] T026 [P] Create UserEnvironmentPermission entity in platform-core/src/main/java/com/einvoice/core/domain/UserEnvironmentPermission.java
- [x] T027 [P] Create AuditLog entity in platform-core/src/main/java/com/einvoice/core/domain/AuditLog.java

### Core Repositories

- [x] T028 [P] Create CompanyRepository in platform-core/src/main/java/com/einvoice/core/repository/CompanyRepository.java
- [x] T029 [P] Create BranchRepository in platform-core/src/main/java/com/einvoice/core/repository/BranchRepository.java
- [x] T030 [P] Create UserRepository in platform-core/src/main/java/com/einvoice/core/repository/UserRepository.java
- [x] T031 [P] Create UserCompanyRoleRepository in platform-core/src/main/java/com/einvoice/core/repository/UserCompanyRoleRepository.java
- [x] T032 [P] Create AuditLogRepository (no update/delete methods) in platform-core/src/main/java/com/einvoice/core/repository/AuditLogRepository.java

### Security Infrastructure

- [x] T033 Implement TenantContext (ThreadLocal holder) in platform-core/src/main/java/com/einvoice/core/context/TenantContext.java
- [x] T034 Implement EnvironmentContext (ThreadLocal holder) in platform-core/src/main/java/com/einvoice/core/context/EnvironmentContext.java
- [x] T035 [P] Implement EncryptionService (AES-256-GCM, master key from env) in platform-security/src/main/java/com/einvoice/security/encryption/EncryptionService.java
- [x] T036 Implement JwtTokenProvider (generate, validate, parse claims) and JwtProperties config class in platform-security/src/main/java/com/einvoice/security/jwt/
- [x] T037 Implement JwtAuthenticationFilter (extract JWT from header, authenticate) in platform-security/src/main/java/com/einvoice/security/jwt/JwtAuthenticationFilter.java
- [x] T038 Implement TenantFilter (extract active_company_id from JWT, set TenantContext) in platform-security/src/main/java/com/einvoice/security/tenant/TenantFilter.java
- [x] T039 Update SecurityConfig with JWT filter chain, RBAC rules, and CSRF config in platform-security/src/main/java/com/einvoice/security/SecurityConfig.java
- [x] T039a Implement RolePermissions (maps roles to Spring Security authorities) in platform-security/src/main/java/com/einvoice/security/rbac/RolePermissions.java and enable @EnableMethodSecurity with @PreAuthorize on all service methods (Viewer=read-only, Accountant=CRUD, Company Admin=config, Super Admin=admin)

### Audit Infrastructure

- [x] T040 Create @Audited annotation in platform-core/src/main/java/com/einvoice/core/audit/Audited.java
- [x] T041 Implement AuditAspect (AOP around advice, captures before/after state) in platform-core/src/main/java/com/einvoice/core/audit/AuditAspect.java
- [x] T042 Implement AuditService (append-only writes) in platform-core/src/main/java/com/einvoice/core/service/AuditService.java

### Angular Shared Infrastructure

- [x] T043 Implement AuthService (login, logout, refresh, switchCompany, selectEnvironment, JWT storage) in frontend/src/app/shared/services/auth.service.ts
- [x] T044 Update AuthInterceptor (attach JWT + environment header to requests) in frontend/src/app/shared/interceptors/auth.interceptor.ts
- [x] T045 [P] Implement AuthGuard (route guard checking authentication) in frontend/src/app/shared/guards/auth.guard.ts
- [x] T046 [P] Implement RoleGuard (route guard checking role permissions) in frontend/src/app/shared/guards/role.guard.ts
- [x] T047 [P] Create DataTableComponent (reusable paginated table with sort/filter) in frontend/src/app/shared/components/data-table/
- [x] T048 [P] Create FormFieldComponent (reusable form field wrapper) in frontend/src/app/shared/components/form-field/
- [x] T049 [P] Create StatusBadgeComponent in frontend/src/app/shared/components/status-badge/
- [x] T050 [P] Create ConfirmDialogComponent in frontend/src/app/shared/components/confirm-dialog/
- [x] T051 [P] Create ToastNotificationService in frontend/src/app/shared/services/toast.service.ts
- [x] T052 [P] Create LoadingSpinnerComponent in frontend/src/app/shared/components/loading-spinner/
- [x] T053 Create HeaderComponent shell with placeholder slots for company switcher dropdown and environment badge (integration in T075/T080) in frontend/src/app/layout/header/header.component.ts
- [x] T054 Update app.routes.ts with auth guards and layout wrapper in frontend/src/app/app.routes.ts

**Checkpoint**: Foundation ready — all migrations run, security chain functional, shared components available. User story implementation can begin.

---

## Phase 3: User Story 1 — Super Admin Onboards a New Company (Priority: P1) MVP

**Goal**: Super Admin can create a company, add a branch, configure authority credentials, and assign a Company Admin user.

**Independent Test**: Log in as seeded Super Admin, create a company, add a branch, set up authority config with encrypted credentials, assign a user by email. Verify company appears in list, credentials are encrypted, user gets access.

### Implementation for User Story 1

- [x] T055 [US1] Implement CompanyService (create, activate, deactivate, list) in platform-core/src/main/java/com/einvoice/core/service/CompanyService.java
- [x] T056 [US1] Implement BranchService (create, update, list by company) in platform-core/src/main/java/com/einvoice/core/service/BranchService.java
- [x] T057 [P] [US1] Create AuthorityConfig entity in platform-core/src/main/java/com/einvoice/core/domain/AuthorityConfig.java
- [x] T058 [P] [US1] Create AuthorityConfigRepository in platform-core/src/main/java/com/einvoice/core/repository/AuthorityConfigRepository.java
- [x] T059 [US1] Implement AuthorityConfigService (CRUD with encryption/decryption via EncryptionService) in platform-core/src/main/java/com/einvoice/core/service/AuthorityConfigService.java
- [x] T060 [US1] Implement UserService (create user, assign to company, remove from company) in platform-core/src/main/java/com/einvoice/core/service/UserService.java
- [x] T061 [US1] Create Admin API DTOs (CreateCompanyRequest, AssignUserRequest, CompanyResponse, etc.) in platform-api/src/main/java/com/einvoice/api/admin/dto/
- [x] T062 [US1] Implement AdminCompanyController (POST/GET companies, activate/deactivate, assign-user, create branch, authority config) per contracts/admin-api.md in platform-api/src/main/java/com/einvoice/api/admin/AdminCompanyController.java
- [x] T063 [US1] Add @Audited annotations to CompanyService, BranchService, AuthorityConfigService, UserService methods
- [x] T064 [US1] Implement Super Admin dashboard page (company list, create company wizard) in frontend/src/app/dashboard/
- [x] T065 [US1] Implement company create form (Angular Reactive Form) in frontend/src/app/config/company-create/
- [x] T066 [US1] Implement branch CRUD forms in frontend/src/app/config/branches/
- [x] T067 [US1] Implement authority config form (credentials masked, file upload for certs, invoice sequence settings: prefix, starting number, reset policy) in frontend/src/app/config/authority-config/
- [x] T068 [US1] Implement user-to-company assignment form in frontend/src/app/config/user-assignment/

**Checkpoint**: Super Admin onboarding flow is fully functional and testable independently.

---

## Phase 4: User Story 2 — User Authentication and Multi-Company Switching (Priority: P1)

**Goal**: Users can log in with email/password, receive JWT, switch between companies via header dropdown, and see data isolated per company.

**Independent Test**: Log in with valid credentials, verify JWT issued. Switch companies via dropdown, verify role changes and data isolation. Test refresh token rotation and logout.

### Implementation for User Story 2

- [x] T069 [US2] Implement AuthenticationService (login with deactivated company check, logout, refresh, switchCompany with password verification via BCrypt) in platform-core/src/main/java/com/einvoice/core/service/AuthenticationService.java
- [x] T070 [P] [US2] Create RefreshToken entity and RefreshTokenRepository in platform-core/src/main/java/com/einvoice/core/domain/RefreshToken.java and platform-core/src/main/java/com/einvoice/core/repository/RefreshTokenRepository.java
- [x] T071 [US2] Create Auth API DTOs (LoginRequest, LoginResponse, SwitchCompanyRequest, RefreshRequest) in platform-api/src/main/java/com/einvoice/api/auth/dto/
- [x] T072 [US2] Implement AuthController (login, switch-company, refresh, logout) per contracts/auth-api.md in platform-api/src/main/java/com/einvoice/api/auth/AuthController.java
- [x] T073 [US2] Add failed login attempt audit logging (FR-022) to AuthenticationService
- [x] T074 [US2] Implement login page (Angular Reactive Form, email + password) in frontend/src/app/auth/
- [x] T075 [US2] Integrate company switcher dropdown in HeaderComponent (calls /api/auth/switch-company, updates JWT and reloads context) in frontend/src/app/layout/header/

**Checkpoint**: Authentication and multi-company switching are fully functional.

---

## Phase 5: User Story 3 — Environment Selection and Access Control (Priority: P1)

**Goal**: Users select an active environment after login/company switch. All operations scoped to (company, environment). Users only see environments they have permission for.

**Independent Test**: Grant user access to specific environments, verify only those appear in selector. Select an environment, verify header badge updates and all data operations are filtered.

### Implementation for User Story 3

- [x] T076 [US3] Create UserEnvironmentPermissionRepository in platform-core/src/main/java/com/einvoice/core/repository/UserEnvironmentPermissionRepository.java
- [x] T077 [US3] Implement EnvironmentService (selectEnvironment, validate permissions, list permitted environments) in platform-core/src/main/java/com/einvoice/core/service/EnvironmentService.java
- [x] T078 [US3] Implement environment selection endpoint (POST /api/auth/select-environment) in AuthController in platform-api/src/main/java/com/einvoice/api/auth/AuthController.java
- [x] T079 [US3] Implement environment selector component (post-login/company-switch picker) in frontend/src/app/shared/components/environment-selector/
- [x] T080 [US3] Integrate environment badge in HeaderComponent and update EnvironmentContext on selection in frontend/src/app/layout/header/

**Checkpoint**: Environment selection and access control are functional. Full security chain (auth + tenant + environment) is complete.

---

## Phase 6: User Story 4 — Customer Management with Excel Import (Priority: P2)

**Goal**: Accountants can CRUD customers, search/filter, import via Excel with row-level validation, and soft-delete (blocked if referenced by invoices).

**Independent Test**: Create customers manually, import 50 via Excel with some invalid rows, verify validation report. Search/filter. Attempt to delete a customer referenced by an invoice and verify it's blocked.

### Implementation for User Story 4

- [x] T081 [P] [US4] Create Customer entity in platform-core/src/main/java/com/einvoice/core/domain/Customer.java
- [x] T082 [P] [US4] Create CustomerRepository (tenant-scoped queries, search, filter by type/VAT) in platform-core/src/main/java/com/einvoice/core/repository/CustomerRepository.java
- [x] T083 [US4] Implement CustomerService (CRUD, soft-delete with invoice reference check, search) in platform-core/src/main/java/com/einvoice/core/service/CustomerService.java
- [x] T084 [US4] Implement CustomerImportService (Excel parsing with Apache POI, row-level validation, duplicate detection, handle zero-valid-rows edge case) in platform-core/src/main/java/com/einvoice/core/service/CustomerImportService.java
- [x] T085 [US4] Implement CustomerExcelTemplateService (generate .xlsx template with headers, data types, example rows) in platform-core/src/main/java/com/einvoice/core/service/CustomerExcelTemplateService.java
- [x] T086 [US4] Create Customer API DTOs in platform-api/src/main/java/com/einvoice/api/customer/dto/
- [x] T087 [US4] Implement CustomerController (CRUD, import, template download) per contracts/customer-api.md in platform-api/src/main/java/com/einvoice/api/customer/CustomerController.java
- [x] T088 [US4] Add @Audited annotations to CustomerService methods
- [x] T089 [US4] Implement customer list page (data table with search/filter) in frontend/src/app/customers/customer-list/
- [x] T090 [US4] Implement customer add/edit form (Angular Reactive Form) in frontend/src/app/customers/customer-form/
- [x] T091 [US4] Implement Excel import component (file upload, progress, validation report display) in frontend/src/app/customers/customer-import/
- [x] T092 [US4] Add template download button to customer list page

**Checkpoint**: Customer management with Excel import is fully functional.

---

## Phase 7: User Story 5 — Item/Product Management with Excel Import (Priority: P2)

**Goal**: Accountants can CRUD items with unique code enforcement, authority scope filtering, and Excel import following the same pattern as customers.

**Independent Test**: Create items, verify code uniqueness. Import via Excel. Filter by authority scope (ZATCA/ETA/Both).

### Implementation for User Story 5

- [x] T093 [P] [US5] Create Item entity in platform-core/src/main/java/com/einvoice/core/domain/Item.java
- [x] T094 [P] [US5] Create ItemRepository (tenant-scoped, search, filter by authority scope) in platform-core/src/main/java/com/einvoice/core/repository/ItemRepository.java
- [x] T095 [US5] Implement ItemService (CRUD, code uniqueness check, soft-delete) in platform-core/src/main/java/com/einvoice/core/service/ItemService.java
- [x] T096 [US5] Implement ItemImportService (Excel parsing, validation, duplicate code detection) in platform-core/src/main/java/com/einvoice/core/service/ItemImportService.java
- [x] T097 [US5] Implement ItemExcelTemplateService in platform-core/src/main/java/com/einvoice/core/service/ItemExcelTemplateService.java
- [x] T098 [US5] Create Item API DTOs in platform-api/src/main/java/com/einvoice/api/item/dto/
- [x] T099 [US5] Implement ItemController (CRUD, import, template) per contracts/item-api.md in platform-api/src/main/java/com/einvoice/api/item/ItemController.java
- [x] T100 [US5] Add @Audited annotations to ItemService methods
- [x] T101 [US5] Implement item list page (data table with search/filter by authority scope) in frontend/src/app/items/item-list/
- [x] T102 [US5] Implement item add/edit form in frontend/src/app/items/item-form/
- [x] T103 [US5] Implement Excel import component (reuse customer import pattern) in frontend/src/app/items/item-import/

**Checkpoint**: Item management with Excel import is fully functional.

---

## Phase 8: User Story 6 — Draft Invoice Creation with Auto-Calculated Totals (Priority: P2)

**Goal**: Accountants can create draft invoices with type selection, line items, auto-calculated totals, VAT breakdown, and domain validation (future dates, subtype flags, original invoice reference for CN/DN).

**Independent Test**: Create a Tax Invoice with 3+ line items and verify all calculated amounts. Create a Credit Note without original invoice reference and verify validation error. Test ZATCA subtype flag rejection (self_billed + export).

### Implementation for User Story 6

- [x] T104 [P] [US6] Create Invoice entity in platform-core/src/main/java/com/einvoice/core/domain/Invoice.java
- [x] T105 [P] [US6] Create InvoiceLine entity in platform-core/src/main/java/com/einvoice/core/domain/InvoiceLine.java
- [x] T106 [P] [US6] Create InvoiceVatBreakdown entity in platform-core/src/main/java/com/einvoice/core/domain/InvoiceVatBreakdown.java
- [x] T107 [P] [US6] Create InvoiceRepository (tenant-scoped, filtered by status/date/type) in platform-core/src/main/java/com/einvoice/core/repository/InvoiceRepository.java
- [x] T108 [US6] Implement InvoiceCalculationService (line amounts, VAT breakdown, document totals using BigDecimal HALF_UP) in platform-core/src/main/java/com/einvoice/core/service/InvoiceCalculationService.java
- [x] T109 [US6] Implement InvoiceValidationService (issue date not future, supply end > supply date, buyer VAT for B2B, at least one line, positive amounts, original invoice ref for CN/DN, ZATCA subtype flag validation) in platform-core/src/main/java/com/einvoice/core/service/InvoiceValidationService.java
- [x] T110 [US6] Implement InvoiceNumberService (sequence generation per authority config: prefix, counter, reset policy) in platform-core/src/main/java/com/einvoice/core/service/InvoiceNumberService.java
- [x] T111 [US6] Implement InvoiceService (create draft, update draft, cancel draft, list, get detail — orchestrates calculation + validation + number generation) in platform-core/src/main/java/com/einvoice/core/service/InvoiceService.java
- [x] T112 [US6] Create Invoice API DTOs (CreateInvoiceRequest, InvoiceResponse, InvoiceLineRequest, etc.) in platform-api/src/main/java/com/einvoice/api/invoice/dto/
- [x] T113 [US6] Implement InvoiceController (POST create, GET list, GET detail, PUT update, DELETE cancel) per contracts/invoice-api.md in platform-api/src/main/java/com/einvoice/api/invoice/InvoiceController.java
- [x] T114 [US6] Implement invoice list page (data table with status/type/date filters) in frontend/src/app/invoices/invoice-list/
- [x] T115 [US6] Implement invoice create/edit form with live total calculation (Angular Reactive Form: header, line items array, totals preview, VAT breakdown display) in frontend/src/app/invoices/invoice-form/
- [x] T116 [US6] Implement invoice detail view in frontend/src/app/invoices/invoice-detail/

**Checkpoint**: Draft invoice creation with auto-calculated totals and domain validation is fully functional.

---

## Phase 9: User Story 7 — Company and Branch Configuration by Company Admin (Priority: P2)

**Goal**: Company Admin can manage their own company profile, branches, authority configs, and user environment permissions.

**Independent Test**: Log in as Company Admin, update company name, manage branches, configure authority credentials (verify masked display), assign environment permissions to users.

### Implementation for User Story 7

- [x] T117 [US7] Create Company API DTOs (UpdateCompanyRequest, CompanyResponse) in platform-api/src/main/java/com/einvoice/api/company/dto/
- [x] T118 [US7] Implement CompanyController (GET/PUT company profile) per contracts/company-api.md in platform-api/src/main/java/com/einvoice/api/company/CompanyController.java
- [x] T119 [US7] Implement BranchController (CRUD branches, authority config CRUD) in platform-api/src/main/java/com/einvoice/api/company/BranchController.java
- [x] T120 [US7] Create User API DTOs (UserResponse, PermissionRequest) in platform-api/src/main/java/com/einvoice/api/user/dto/
- [x] T121 [US7] Implement UserController (list users, assign permissions, activate/deactivate) per contracts/company-api.md in platform-api/src/main/java/com/einvoice/api/user/UserController.java
- [x] T122 [US7] Implement company profile form in frontend/src/app/config/company-profile/
- [x] T123 [US7] Implement user list and permission management in frontend/src/app/config/users/
- [x] T124 [US7] Implement environment permission matrix (checkboxes per environment per user) in frontend/src/app/config/users/permission-matrix/

**Checkpoint**: Company Admin self-service configuration is fully functional.

---

## Phase 10: User Story 8 — Audit Trail for All Administrative Actions (Priority: P3)

**Goal**: All admin/config/master-data actions are audit-logged automatically. Audit logs are append-only and cannot be modified.

**Independent Test**: Perform several admin actions (create company, update customer, etc.), query audit logs and verify entries with before/after state. Attempt to modify/delete an audit log and verify rejection.

### Implementation for User Story 8

- [x] T125 [US8] Verify all @Audited annotations are in place across CompanyService, BranchService, AuthorityConfigService, UserService, CustomerService, ItemService (cross-check with earlier phases)
- [x] T126 [US8] Implement audit log query endpoint (GET /api/audit-logs with filters: company, entity_type, entity_id, date range) in platform-api/src/main/java/com/einvoice/api/audit/AuditLogController.java
- [x] T127 [US8] Create AuditLog API DTOs in platform-api/src/main/java/com/einvoice/api/audit/dto/
- [x] T128 [US8] Implement audit log viewer page (filterable data table showing action, user, entity, timestamp, before/after diff) in frontend/src/app/logs/

**Checkpoint**: Audit trail is complete — all admin actions logged, append-only integrity verified.

---

## Phase 11: Polish & Cross-Cutting Concerns

**Purpose**: Improvements that affect multiple user stories

- [x] T129 [P] Add global exception handler with consistent error response format in platform-api/src/main/java/com/einvoice/api/config/GlobalExceptionHandler.java
- [x] T130 [P] Add CORS configuration for Angular dev server in platform-security/src/main/java/com/einvoice/security/SecurityConfig.java
- [x] T131 [P] Configure optimistic locking (version field) on Invoice entity for concurrent edit detection in platform-core/src/main/java/com/einvoice/core/domain/Invoice.java
- [x] T132 Update frontend route guards to protect all routes by role (Super Admin routes, Company Admin routes, Accountant routes, Viewer read-only) in frontend/src/app/app.routes.ts
- [x] T133 [P] Add unsaved changes warning on company switch (CanDeactivate guard) in frontend/src/app/shared/guards/unsaved-changes.guard.ts
- [x] T134 Run quickstart.md validation — verify full onboarding flow, multi-tenant isolation, invoice calculation accuracy
- [x] T135 [P] Create tenant isolation integration test (2 tenants, verify Company A cannot access Company B data via API) in platform-api/src/test/java/com/einvoice/api/TenantIsolationIntegrationTest.java
- [x] T136 [P] Create EncryptionService unit test (encrypt/decrypt round-trip, verify ciphertext differs from plaintext, verify key rotation) in platform-security/src/test/java/com/einvoice/security/encryption/EncryptionServiceTest.java
- [x] T137 [P] Create InvoiceCalculationService unit test (golden-file tests for line amounts, VAT breakdown, document totals, rounding edge cases) in platform-core/src/test/java/com/einvoice/core/service/InvoiceCalculationServiceTest.java

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — can start immediately
- **Foundational (Phase 2)**: Depends on Setup completion — BLOCKS all user stories
- **User Stories (Phase 3–10)**: All depend on Foundational phase completion
  - US1 (Phase 3) and US2 (Phase 4) and US3 (Phase 5) are all P1 and should be done in order
  - US4–US8 (Phases 6–10) can proceed in parallel after US1–US3 are complete
- **Polish (Phase 11)**: Depends on all user stories being complete

### User Story Dependencies

- **US1 (Super Admin Onboarding)**: Can start after Foundational — no dependencies on other stories
- **US2 (Authentication)**: Can start after Foundational — no dependencies on other stories. However, US1 provides the seeded Super Admin for testing
- **US3 (Environment Selection)**: Depends on US2 (authentication must exist for environment to be scoped to a session)
- **US4 (Customer Management)**: Can start after US1+US2+US3 — needs auth chain and tenant context
- **US5 (Item Management)**: Can start after US1+US2+US3 — same as US4, can run in parallel with US4
- **US6 (Draft Invoices)**: Depends on US4 (customers) and US5 (items) for buyer selection and line item creation
- **US7 (Company Config)**: Can start after US1+US2+US3 — can run in parallel with US4/US5
- **US8 (Audit Trail)**: Can start after all services with @Audited are implemented — best done last to verify cross-cutting coverage

### Within Each User Story

- Models before services
- Services before controllers
- Controllers before Angular pages
- Backend complete before frontend for that story
- @Audited annotations added after service implementation

### Parallel Opportunities

- All Setup tasks marked [P] can run in parallel
- All Foundational migrations (T007–T012) can run in parallel (independent tables)
- All enums (T014–T021) can run in parallel
- All core entities (T022–T027) can run in parallel
- All core repositories (T028–T032) can run in parallel
- All shared Angular components (T047–T052) can run in parallel
- US4 and US5 can run in parallel (independent entity domains)
- US7 can run in parallel with US4/US5

---

## Parallel Example: User Story 4 (Customers)

```bash
# Launch model + repository in parallel:
Task: "Create Customer entity in platform-core/.../Customer.java"
Task: "Create CustomerRepository in platform-core/.../CustomerRepository.java"

# Then service layer (depends on entity + repo):
Task: "Implement CustomerService in platform-core/.../CustomerService.java"
Task: "Implement CustomerImportService in platform-core/.../CustomerImportService.java"
Task: "Implement CustomerExcelTemplateService in platform-core/.../CustomerExcelTemplateService.java"

# Then API layer:
Task: "Create Customer API DTOs in platform-api/.../dto/"
Task: "Implement CustomerController in platform-api/.../CustomerController.java"

# Then frontend (all can be parallel):
Task: "Implement customer list page in frontend/src/app/customers/customer-list/"
Task: "Implement customer add/edit form in frontend/src/app/customers/customer-form/"
Task: "Implement Excel import component in frontend/src/app/customers/customer-import/"
```

---

## Implementation Strategy

### MVP First (US1 + US2 + US3)

1. Complete Phase 1: Setup
2. Complete Phase 2: Foundational (CRITICAL — blocks all stories)
3. Complete Phase 3: US1 — Super Admin Onboarding
4. Complete Phase 4: US2 — Authentication
5. Complete Phase 5: US3 — Environment Selection
6. **STOP and VALIDATE**: Full security chain works end-to-end
7. Deploy/demo if ready

### Incremental Delivery

1. Setup + Foundational → Foundation ready
2. US1 + US2 + US3 → Full auth/tenant/env chain (MVP!)
3. US4 + US5 (parallel) → Master data management
4. US6 → Draft invoices with calculations
5. US7 → Company Admin self-service
6. US8 → Audit trail verification
7. Polish → Error handling, optimistic locking, route guards

### Parallel Team Strategy

With multiple developers after Foundational is complete:
- Developer A: US1 (onboarding) → US6 (invoices)
- Developer B: US2 (auth) → US4 (customers)
- Developer C: US3 (environments) → US5 (items)
- Developer D: US7 (company config) → US8 (audit)

---

## Notes

- [P] tasks = different files, no dependencies
- [Story] label maps task to specific user story for traceability
- Each user story should be independently completable and testable
- Commit after each task or logical group
- Stop at any checkpoint to validate story independently
- Angular forms MUST use Reactive Forms (Constitution IX)
- All tenant-scoped queries MUST use TenantContext (Constitution II)
- No secrets in Angular responses (Constitution V)
- Financial calculations use BigDecimal with HALF_UP rounding (FR-021)
